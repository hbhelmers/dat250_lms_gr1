package no.uib.dat250.LMS;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import tools.jackson.databind.json.JsonMapper;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.events.*;
import no.uib.dat250.LMS.storage.FileStorage;
import no.uib.dat250.LMS.user.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=",
        "lms.demo.accounts.enabled=false", "lms.messaging.enabled=true", "lms.messaging.publish-interval-ms=3600000",
        "lms.storage.cleanup.enabled=false"})
@AutoConfigureMockMvc
@Import({TestDatabaseConfiguration.class, CourseContentTests.FixedTime.class})
class MessagingTests {
    static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:4.3.6-management")
            .withEnv("RABBITMQ_DEFAULT_USER", "messaging-test").withEnv("RABBITMQ_DEFAULT_PASS", "messaging-test-password")
            .withEnv("RABBITMQ_DEFAULT_VHOST", "lms").withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*\\n", 1));
    static { BROKER.start(); }
    @DynamicPropertySource static void rabbitProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.rabbitmq.host", BROKER::getHost);
        properties.add("spring.rabbitmq.port", () -> BROKER.getMappedPort(5672));
        properties.add("spring.rabbitmq.username", () -> "messaging-test");
        properties.add("spring.rabbitmq.password", () -> "messaging-test-password");
    }
    static final String TEACHER = "teacher@example.test", STUDENT = "student@example.test", OTHER = "other@example.test";
    static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CourseRepository courses;
    @Autowired LmsUserRepository users;
    @Autowired CourseService courseService;
    @Autowired CourseContentService content;
    @Autowired QuizService quizzes;
    @Autowired AssignmentService assignments;
    @Autowired OutboxPublisher publisher;
    @Autowired RabbitEventTransport transport;
    @Autowired AmqpAdmin admin;
    @Autowired CachingConnectionFactory connection;
    @Autowired JsonMapper json;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean FileStorage storage;
    @MockitoSpyBean CourseActivityConsumer consumer;
    long courseId, moduleId;

    @BeforeEach void seed() {
        courses.deleteAll(); users.deleteAll();
        admin.initialize();
        admin.purgeQueue(MessagingConfiguration.ACTIVITY_QUEUE, false);
        admin.purgeQueue(MessagingConfiguration.DEAD_QUEUE, false);
        for (var email : List.of(TEACHER, STUDENT, OTHER)) users.save(new LmsUser(email, email, "unused"));
        courseId = courseService.create(new CreateCourseRequest("DAT250", "", "Høst 2026"), TEACHER).id();
        courseService.addStudent(courseId, new AddStudentRequest(STUDENT), TEACHER);
        courseService.addStudent(courseId, new AddStudentRequest(OTHER), TEACHER);
        moduleId = content.createModule(courseId, new ModuleRequest("Uke 1"), TEACHER).id();
    }

    @Test void quizEventTravelsThroughRealRabbitMqBeforeAppearingInActivityApi() throws Exception {
        long quizId = quiz();
        var attempt = submitQuiz(quizId, STUDENT, UUID.randomUUID());
        assertThat(activityCount()).isZero();
        assertThat(pending()).isEqualTo(1);
        var event = event();
        assertThat(event.type()).isEqualTo(LearningEvent.Type.QUIZ_SUBMITTED);
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.referenceId()).isEqualTo(attempt.id());
        assertThat(event.occurredAt()).isEqualTo(NOW);
        publisher.publishPending(); awaitActivity(1);
        assertThat(pending()).isZero();
        mvc.perform(get(api()).with(user(TEACHER))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("QUIZ_SUBMITTED"))
                .andExpect(jsonPath("$[0].studentEmail").value(STUDENT))
                .andExpect(jsonPath("$[0].itemId").value(quizId)).andExpect(jsonPath("$[0].version").value(1));
    }

    @Test void lateSubmissionPublishesFlagAndResubmissionVersion() throws Exception {
        long id = assignment(NOW, true);
        assignments.submit(courseId, moduleId, id, UUID.randomUUID(), pdf(), STUDENT);
        assignments.submit(courseId, moduleId, id, UUID.randomUUID(), pdf(), STUDENT);
        publisher.publishPending(); awaitActivity(2);
        mvc.perform(get(api()).with(user(TEACHER))).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].late").value(true)).andExpect(jsonPath("$[1].late").value(true));
        assertThat(jdbc.queryForList("SELECT version FROM course_activity", Integer.class)).containsExactlyInAnyOrder(1, 2);
    }

    @Test void retriesOfQuizAndFileSubmissionCreateOneEventEach() {
        long quiz = quiz(), assignment = assignment(null, false);
        var quizKey = UUID.randomUUID(); var fileKey = UUID.randomUUID();
        submitQuiz(quiz, STUDENT, quizKey); submitQuiz(quiz, STUDENT, quizKey);
        assignments.submit(courseId, moduleId, assignment, fileKey, pdf(), STUDENT);
        assignments.submit(courseId, moduleId, assignment, fileKey, pdf(), STUDENT);
        assertThat(pending()).isEqualTo(2);
        publisher.publishPending(); awaitActivity(2);
    }

    @Test void rolledBackUserActionLeavesNeitherAttemptNorOutboxEvent() {
        long id = quiz();
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            submitQuiz(id, STUDENT, UUID.randomUUID());
            assertThat(pending()).isEqualTo(1);
            transaction.setRollbackOnly();
        });
        assertThat(pending()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts", Integer.class)).isZero();
    }

    @Test void rejectedOrFailedSubmissionsDoNotPublishEvents() {
        long id = assignment(NOW, false);
        assertThatThrownBy(() -> assignments.submit(courseId, moduleId, id, UUID.randomUUID(), pdf(), STUDENT)).hasMessageContaining("409");
        long open = assignment(null, false);
        assertThatThrownBy(() -> assignments.submit(courseId, moduleId, open, UUID.randomUUID(), pdf(), TEACHER)).hasMessageContaining("403");
        assertThat(pending()).isZero();
        doThrow(new IllegalStateException("Object storage offline")).when(storage).put(any(), any(), any());
        assertThatThrownBy(() -> assignments.submit(courseId, moduleId, open, UUID.randomUUID(), pdf(), STUDENT)).hasMessageContaining("offline");
        assertThat(pending()).isZero();
    }

    @Test void gradingDraftIsPrivateAndOnlyPublishedChangesGenerateEvents() {
        long id = assignment(null, false);
        var submission = assignments.submit(courseId, moduleId, id, UUID.randomUUID(), pdf(), STUDENT);
        assignments.assess(courseId, moduleId, id, submission.id(), new GradeRequest(AssignmentGrade.A, "Private draft", false), TEACHER);
        assertThat(pending()).isEqualTo(1);
        var published = new GradeRequest(AssignmentGrade.A, "Feedback", true);
        assignments.assess(courseId, moduleId, id, submission.id(), published, TEACHER);
        assignments.assess(courseId, moduleId, id, submission.id(), published, TEACHER);
        assertThat(pending()).isEqualTo(2);
        assignments.assess(courseId, moduleId, id, submission.id(), new GradeRequest(AssignmentGrade.B, "Updated feedback", true), TEACHER);
        assertThat(pending()).isEqualTo(3);
        assignments.assess(courseId, moduleId, id, submission.id(), new GradeRequest(AssignmentGrade.B, "Withheld", false), TEACHER);
        assertThat(pending()).isEqualTo(3);
        var payloads = jdbc.queryForList("SELECT payload::text FROM event_outbox", String.class);
        assertThat(payloads).allSatisfy(payload -> assertThat(payload).doesNotContain("Private draft", "Feedback", "Updated feedback", "Withheld",
                "\"grade\":", "\"answers\":", "\"feedback\":", "\"filename\":", STUDENT, TEACHER));
        publisher.publishPending(); awaitActivity(3);
    }

    @Test void duplicateMessagesAreAcknowledgedWithoutDuplicateActivity() throws Exception {
        submitQuiz(quiz(), STUDENT, UUID.randomUUID());
        var event = event(); var payload = json.writeValueAsString(event);
        transport.send(event.id(), event.type().routingKey(), payload);
        transport.send(event.id(), event.type().routingKey(), payload);
        verify(consumer, timeout(10_000).atLeast(2)).receive(any());
        assertThat(activityCount()).isEqualTo(1);
        publisher.publishPending();
        verify(consumer, timeout(10_000).atLeast(3)).receive(any());
        assertThat(activityCount()).isEqualTo(1);
    }

    @Test void unroutableMessageStaysPendingUntilRoutingIsRestored() {
        submitQuiz(quiz(), STUDENT, UUID.randomUUID());
        jdbc.update("UPDATE event_outbox SET routing_key = 'missing.binding'");
        publisher.publishPending();
        assertThat(pending()).isEqualTo(1); assertThat(activityCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox", Integer.class)).isEqualTo(1);
        publisher.publishPending(); // Backoff prevents an immediate retry.
        assertThat(jdbc.queryForObject("SELECT attempts FROM event_outbox", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE event_outbox SET routing_key = 'quiz.submitted', available_at = '2000-01-01T00:00:00Z'");
        publisher.publishPending(); awaitActivity(1);
        assertThat(pending()).isZero();
    }

    @Test void brokerOutageDoesNotBlockSubmissionAndPendingEventIsDeliveredAfterRestart() throws Exception {
        long id = quiz();
        assertThat(BROKER.execInContainer("rabbitmqctl", "stop_app").getExitCode()).isZero();
        try {
            var attempt = submitQuiz(id, STUDENT, UUID.randomUUID());
            assertThat(attempt.id()).isPositive(); assertThat(pending()).isEqualTo(1);
            publisher.publishPending();
            assertThat(pending()).isEqualTo(1); assertThat(activityCount()).isZero();
        } finally {
            assertThat(BROKER.execInContainer("rabbitmqctl", "start_app").getExitCode()).isZero();
            connection.resetConnection(); admin.initialize();
        }
        jdbc.update("UPDATE event_outbox SET available_at = '2000-01-01T00:00:00Z'");
        publisher.publishPending(); awaitActivity(1);
        assertThat(pending()).isZero();
    }

    @Test void malformedAndUnsupportedEventsAreMovedToDeadLetterQueue() throws Exception {
        var invalid = "{\"schemaVersion\":999}";
        transport.send(UUID.randomUUID(), "quiz.submitted", invalid);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(deadLetters()).isEqualTo(1));
        assertThat(activityCount()).isZero();
        transport.send(UUID.randomUUID(), "quiz.submitted", "not-json");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(deadLetters()).isEqualTo(2));
    }

    @Test void deletedContentCascadesPendingEventsAndLateMessagesCannotRecreateActivity() throws Exception {
        submitQuiz(quiz(), STUDENT, UUID.randomUUID());
        var event = event();
        content.deleteModule(courseId, moduleId, TEACHER);
        assertThat(pending()).isZero();
        transport.send(event.id(), event.type().routingKey(), json.writeValueAsString(event));
        verify(consumer, timeout(10_000)).receive(any());
        assertThat(activityCount()).isZero(); assertThat(deadLetters()).isZero();
    }

    @Test void studentsOnlySeeTheirOwnVisibleActivityAndCannotSeeIdentitiesOrScores() throws Exception {
        long id = quiz();
        submitQuiz(id, STUDENT, UUID.randomUUID()); submitQuiz(id, OTHER, UUID.randomUUID());
        publisher.publishPending(); awaitActivity(2);
        mvc.perform(get(api()).with(user(TEACHER))).andExpect(jsonPath("$", hasSize(2)));
        for (var email : List.of(STUDENT, OTHER)) mvc.perform(get(api()).with(user(email)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentName").isEmpty()).andExpect(jsonPath("$[0].studentEmail").isEmpty())
                .andExpect(jsonPath("$[0].score").doesNotExist()).andExpect(jsonPath("$[0].grade").doesNotExist());
        var request = quizRequest();
        quizzes.update(courseId, moduleId, id, new QuizRequest(request.title(), request.body(), false, null, null, 20, null, false, request.questions()), TEACHER);
        mvc.perform(get(api()).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get(api()).with(user(TEACHER))).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test void activityVisibilityUsesSameWindowBoundariesAsCourseContent() throws Exception {
        long id = assignment(null, false);
        assignments.submit(courseId, moduleId, id, UUID.randomUUID(), pdf(), STUDENT);
        publisher.publishPending(); awaitActivity(1);
        assignments.update(courseId, moduleId, id, new AssignmentRequest("Oppgave", "Tekst", true, NOW, NOW.plusSeconds(1), null, false), TEACHER);
        mvc.perform(get(api()).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(1)));
        assignments.update(courseId, moduleId, id, new AssignmentRequest("Oppgave", "Tekst", true, null, NOW, null, false), TEACHER);
        mvc.perform(get(api()).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
        assignments.update(courseId, moduleId, id, new AssignmentRequest("Oppgave", "Tekst", true, NOW.plusSeconds(1), null, null, false), TEACHER);
        mvc.perform(get(api()).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void activityRequiresMembershipAndCannotLeakAcrossCourses() throws Exception {
        submitQuiz(quiz(), STUDENT, UUID.randomUUID()); publisher.publishPending(); awaitActivity(1);
        mvc.perform(get(api())).andExpect(status().isUnauthorized());
        mvc.perform(get(api()).with(user("outsider@example.test"))).andExpect(status().isNotFound());
        var otherCourse = courseService.create(new CreateCourseRequest("Annet fag", "", "Høst 2026"), TEACHER);
        mvc.perform(get("/api/courses/" + otherCourse.id() + "/activity").with(user(TEACHER))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/courses/999999/activity").with(user(TEACHER))).andExpect(status().isNotFound());
    }

    @Test void activityIsBoundedToFiftyNewestEvents() throws Exception {
        long id = quiz();
        var attempt = submitQuiz(id, STUDENT, UUID.randomUUID());
        for (int index = 0; index < 55; index++) {
            consumer.receive(message(new LearningEvent(1, UUID.randomUUID(), LearningEvent.Type.QUIZ_SUBMITTED,
                    NOW.plusSeconds(index), courseId, moduleId, id, users.findByEmail(STUDENT).orElseThrow().getId(), attempt.id(), 1, false)));
        }
        mvc.perform(get(api()).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(50)))
                .andExpect(jsonPath("$[0].occurredAt").value(NOW.plusSeconds(54).toString()))
                .andExpect(jsonPath("$[49].occurredAt").value(NOW.plusSeconds(5).toString()));
    }

    @Test void concurrentRelaysLockPendingRowsAndDoNotPublishThemTwice() throws Exception {
        long id = quiz();
        for (int index = 0; index < 4; index++) submitQuiz(id, STUDENT, UUID.randomUUID());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> { start.await(); publisher.publishPending(); return true; })).toList();
            start.countDown();
            for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS)).isTrue();
        }
        awaitActivity(4);
        assertThat(jdbc.queryForList("SELECT attempts FROM event_outbox", Integer.class)).containsOnly(1);
    }

    private long quiz() { return quizzes.create(courseId, moduleId, quizRequest(), TEACHER).content().id(); }
    private QuizRequest quizRequest() {
        return new QuizRequest("Quiz", "Introduksjon", true, null, null, 20, null, false,
                List.of(new QuizQuestionRequest("Spørsmål", List.of("A", "B"), 0, "Hint")));
    }
    private QuizAttemptResponse submitQuiz(long id, String email, UUID key) {
        var quiz = quizzes.read(courseId, moduleId, id, email);
        return quizzes.submit(courseId, moduleId, id, new QuizSubmission(key,
                List.of(new QuizSubmission.Answer(quiz.questions().getFirst().id(), 0))), email);
    }
    private long assignment(Instant deadline, boolean late) {
        return assignments.create(courseId, moduleId, new AssignmentRequest("Oppgave", "Beskrivelse", true, null, null, deadline, late), TEACHER).content().id();
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.7\nTest\n%%EOF".getBytes(StandardCharsets.UTF_8)); }
    private int pending() { return jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE published_at IS NULL", Integer.class); }
    private int activityCount() { return jdbc.queryForObject("SELECT count(*) FROM course_activity", Integer.class); }
    private void awaitActivity(int count) { await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(activityCount()).isEqualTo(count)); }
    private int deadLetters() { return ((Number) admin.getQueueProperties(MessagingConfiguration.DEAD_QUEUE).get("QUEUE_MESSAGE_COUNT")).intValue(); }
    private LearningEvent event() { return json.readValue(jdbc.queryForObject("SELECT payload::text FROM event_outbox LIMIT 1", String.class), LearningEvent.class); }
    private Message message(LearningEvent event) { return new Message(json.writeValueAsBytes(event), new MessageProperties()); }
    private String api() { return "/api/courses/" + courseId + "/activity"; }
}
