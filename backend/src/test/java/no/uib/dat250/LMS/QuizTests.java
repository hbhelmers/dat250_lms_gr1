package no.uib.dat250.LMS;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.user.LmsUser;
import no.uib.dat250.LMS.user.LmsUserRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=", "lms.demo.accounts.enabled=false" })
@AutoConfigureMockMvc
@Import({TestDatabaseConfiguration.class, CourseContentTests.FixedTime.class})
class QuizTests {
    private static final String TEACHER = "teacher@example.test", STUDENT = "student@example.test", OTHER = "other@example.test";
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String BODY = """
            {"title":"  Quiz  ","body":"Introduksjon","published":true,"maxAttempts":2,"resultsVisible":true,
             "questions":[{"prompt":"Første spørsmål","options":["A","B"],"correctOption":0,"hint":"Tenk på A"},
                          {"prompt":"Andre spørsmål","options":["C","D"],"correctOption":1,"hint":"Tenk på D"}]}
            """;
    @Autowired private MockMvc mvc;
    @Autowired private CourseRepository courses;
    @Autowired private CourseService courseService;
    @Autowired private LmsUserRepository users;
    @Autowired private CourseContentService content;
    @Autowired private QuizService quizzes;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attempts;
    @Autowired private JdbcTemplate jdbc;
    private long courseId, moduleId;

    @BeforeEach
    void seed() {
        courses.deleteAll(); users.deleteAll();
        for (var email : List.of(TEACHER, STUDENT, OTHER)) users.save(new LmsUser(email, email, "unused"));
        courseId = courseService.create(new CreateCourseRequest("DAT250", "", "Høst 2026"), TEACHER).id();
        courseService.addStudent(courseId, new AddStudentRequest(STUDENT), TEACHER);
        moduleId = content.createModule(courseId, new ModuleRequest("Uke 1"), TEACHER).id();
    }

    @Test
    void teacherCreatesRelationalQuizInTheSameOrderAsPages() throws Exception {
        content.createPage(courseId, moduleId, new PageRequest("Side", "Tekst", true, null, null), TEACHER);
        mvc.perform(write(post(base()), TEACHER, BODY)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.title").value("Quiz"))
                .andExpect(jsonPath("$.content.type").value("QUIZ"))
                .andExpect(jsonPath("$.content.position").value(1))
                .andExpect(jsonPath("$.questions[0].correctOption").value(0));
        assertThat(quizRepository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from quiz_questions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from quiz_options", Integer.class)).isEqualTo(4);
        mvc.perform(get("/api/courses/" + courseId + "/modules").with(user(STUDENT)))
                .andExpect(jsonPath("$[0].items[1].type").value("QUIZ"))
                .andExpect(jsonPath("$[0].items[1].questions").doesNotExist());
    }

    @Test
    void answersAndHintsAreNotExposedToStudentsBeforeSubmitting() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].correctOption").isEmpty())
                .andExpect(jsonPath("$.questions[0].hint").isEmpty())
                .andExpect(jsonPath("$.attempts", hasSize(0)));
        mvc.perform(get(path(quiz)).with(user(TEACHER))).andExpect(jsonPath("$.questions[0].correctOption").value(0));
        mvc.perform(get("/api/courses/" + courseId + "/modules/" + moduleId + "/pages/" + quiz.content().id()).with(user(STUDENT)))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersAreGradedOnServerAndStoredWithFeedback() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 0).replace("\"answers\":", "\"score\":999,\"userId\":999,\"answers\":")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.score").value(1)).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.answers[0].correct").value(true))
                .andExpect(jsonPath("$.answers[0].hint").value(""))
                .andExpect(jsonPath("$.answers[1].correct").value(false))
                .andExpect(jsonPath("$.answers[1].hint").value("Tenk på D"));
        assertThat(attempts.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from quiz_answers", Integer.class)).isEqualTo(2);
        mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(jsonPath("$.attempts[0].score").value(1));
        mvc.perform(get(path(quiz) + "/attempts").with(user(TEACHER)))
                .andExpect(jsonPath("$[0].studentEmail").value(STUDENT)).andExpect(jsonPath("$[0].score").value(1));
    }

    @Test
    void withheldResultsDoNotLeakScoresAnswersOrHintsAndCanBePublishedLater() throws Exception {
        var quiz = create(request(true, false, 2, null, null, null));
        mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 0)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.score").isEmpty())
                .andExpect(jsonPath("$.answers", hasSize(0)));
        mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(jsonPath("$.attempts[0].score").isEmpty())
                .andExpect(jsonPath("$.questions[0].correctOption").isEmpty());
        mvc.perform(get(path(quiz) + "/attempts").with(user(TEACHER))).andExpect(jsonPath("$[0].score").value(1));
        quizzes.update(courseId, moduleId, quiz.content().id(), request(true, true, 2, null, null, null), TEACHER);
        mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(jsonPath("$.attempts[0].score").value(1))
                .andExpect(jsonPath("$.attempts[0].answers", hasSize(2)));
    }

    @Test
    void attemptLimitIsPerStudentAndPrivateHistoryCannotBeReadByOtherStudents() throws Exception {
        courseService.addStudent(courseId, new AddStudentRequest(OTHER), TEACHER);
        var quiz = create(request(true, true, 1, null, null, null));
        submit(quiz, STUDENT, UUID.randomUUID(), 0, 1);
        mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isConflict());
        mvc.perform(get(path(quiz)).with(user(OTHER))).andExpect(jsonPath("$.attempts", hasSize(0)));
        submit(quiz, OTHER, UUID.randomUUID(), 0, 0);
        mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.attempts[0].score").value(2));
        mvc.perform(get(path(quiz) + "/attempts").with(user(STUDENT))).andExpect(status().isForbidden());
        assertThat(attempts.count()).isEqualTo(2);
    }

    @Test
    void retryingTheSameSubmissionDoesNotConsumeAnotherAttempt() throws Exception {
        var quiz = create(request(true, true, 1, null, null, null));
        var key = UUID.randomUUID();
        var first = quizzes.submit(courseId, moduleId, quiz.content().id(), answers(quiz, key, 0, 1), STUDENT);
        var retry = quizzes.submit(courseId, moduleId, quiz.content().id(), answers(quiz, key, 0, 1), STUDENT);
        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(attempts.count()).isEqualTo(1);
        mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, key, 1, 0))).andExpect(status().isConflict());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-10-20T10:00:00Z", "2026-10-20T09:59:59Z"})
    void deadlineRejectsAttemptsAtOrAfterTheBoundary(String deadline) throws Exception {
        var quiz = create(request(true, true, 2, Instant.parse(deadline), null, null));
        mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isConflict());
        assertThat(attempts.count()).isZero();
    }

    @Test
    void anAttemptJustBeforeDeadlineIsAccepted() throws Exception {
        var quiz = create(request(true, true, 2, NOW.plusSeconds(1), null, null));
        submit(quiz, STUDENT, UUID.randomUUID(), 0, 1);
    }

    @Test
    void draftsFutureAndExpiredQuizzesCannotBeReadOrSubmittedByStudents() throws Exception {
        var requests = List.of(request(false, true, 2, null, null, null),
                request(true, true, 2, null, NOW.plusSeconds(1), null), request(true, true, 2, null, null, NOW));
        for (var request : requests) {
            var quiz = create(request);
            mvc.perform(get(path(quiz)).with(user(STUDENT))).andExpect(status().isNotFound());
            mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isNotFound());
            mvc.perform(get(path(quiz)).with(user(TEACHER))).andExpect(status().isOk());
        }
        mvc.perform(get("/api/courses/" + courseId + "/modules").with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rolesAndCsrfAreEnforcedOnQuizRoutes() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        mvc.perform(write(post(base()), STUDENT, BODY)).andExpect(status().isForbidden());
        mvc.perform(write(put(path(quiz)), STUDENT, BODY)).andExpect(status().isForbidden());
        mvc.perform(write(post(path(quiz) + "/attempts"), TEACHER, submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isForbidden());
        mvc.perform(get(path(quiz)).with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(write(post(path(quiz) + "/attempts"), OTHER, submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isNotFound());
        mvc.perform(get(path(quiz))).andExpect(status().isUnauthorized());
        mvc.perform(post(path(quiz) + "/attempts").with(user(STUDENT)).contentType(MediaType.APPLICATION_JSON)
                .content(submission(quiz, UUID.randomUUID(), 0, 1))).andExpect(status().isForbidden());
    }

    @Test
    void courseModuleAndContentTypeCannotBeSpoofed() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        long otherModule = content.createModule(courseId, new ModuleRequest("Uke 2"), TEACHER).id();
        long otherCourse = courseService.create(new CreateCourseRequest("Annet emne", "", "Høst 2026"), TEACHER).id();
        String wrongModule = "/api/courses/" + courseId + "/modules/" + otherModule + "/quizzes/" + quiz.content().id();
        String wrongCourse = "/api/courses/" + otherCourse + "/modules/" + moduleId + "/quizzes/" + quiz.content().id();
        for (var path : List.of(wrongModule, wrongCourse)) {
            mvc.perform(get(path).with(user(TEACHER))).andExpect(status().isNotFound());
            mvc.perform(write(put(path), TEACHER, BODY)).andExpect(status().isNotFound());
            mvc.perform(get(path + "/attempts").with(user(TEACHER))).andExpect(status().isNotFound());
        }
        long page = content.createPage(courseId, moduleId, new PageRequest("Side", "Tekst", true, null, null), TEACHER).id();
        mvc.perform(get(base() + "/" + page).with(user(STUDENT))).andExpect(status().isNotFound());
        mvc.perform(write(put("/api/courses/" + courseId + "/modules/" + moduleId + "/pages/" + quiz.content().id()), TEACHER,
                "{\"title\":\"Angrep\",\"body\":\"Tekst\",\"published\":true}")).andExpect(status().isNotFound());
    }

    @Test
    void missingDuplicateForeignAndOutOfRangeAnswersAreRejected() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        long first = quiz.questions().getFirst().id();
        for (var body : List.of("{\"questionId\":" + first + ",\"option\":0}",
                "{\"questionId\":" + first + ",\"option\":0},{\"questionId\":" + first + ",\"option\":1}",
                "{\"questionId\":999999,\"option\":0}", "{\"questionId\":" + first + ",\"option\":7}")) {
            mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, "{\"submissionId\":\"" + UUID.randomUUID() + "\",\"answers\":[" + body + "]}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(attempts.count()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"\"maxAttempts\":0", "\"maxAttempts\":21", "\"maxAttempts\":null"})
    void invalidAttemptLimitsAreRejected(String value) throws Exception {
        mvc.perform(write(post(base()), TEACHER, BODY.replace("\"maxAttempts\":2", value))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {
        "{\"prompt\":\"\",\"options\":[\"A\",\"B\"],\"correctOption\":0}",
        "{\"prompt\":\"Q\",\"options\":[\"A\"],\"correctOption\":0}",
        "{\"prompt\":\"Q\",\"options\":[\"A\",\" A \"],\"correctOption\":0}",
        "{\"prompt\":\"Q\",\"options\":[\"A\",\"B\"],\"correctOption\":2}",
        "{\"prompt\":\"Q\",\"options\":[\"A\",\"B\"]}", "null"
    })
    void malformedQuestionsCannotBeStored(String question) throws Exception {
        String body = "{\"title\":\"Quiz\",\"body\":\"\",\"published\":false,\"maxAttempts\":1,\"resultsVisible\":true,\"questions\":[" + question + "]}";
        mvc.perform(write(post(base()), TEACHER, body)).andExpect(status().isBadRequest());
        assertThat(quizRepository.count()).isZero();
    }

    @Test
    void questionsCanBeReplacedBeforeAnyAttempts() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        mvc.perform(write(put(path(quiz)), TEACHER, BODY.replace("Første spørsmål", "Endret spørsmål"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt").value("Endret spørsmål"));
        assertThat(jdbc.queryForObject("select count(*) from quiz_questions", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from quiz_options", Integer.class)).isEqualTo(4);
    }

    @Test
    void submittedQuestionsAreImmutableButSettingsCanStillBeChanged() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        submit(quiz, STUDENT, UUID.randomUUID(), 0, 1);
        mvc.perform(write(put(path(quiz)), TEACHER, BODY.replace("Første spørsmål", "Endret spørsmål"))).andExpect(status().isConflict());
        mvc.perform(write(put(path(quiz)), TEACHER, BODY.replace("\"maxAttempts\":2", "\"maxAttempts\":3")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questionsLocked").value(true))
                .andExpect(jsonPath("$.maxAttempts").value(3)).andExpect(jsonPath("$.questions[0].id").value(quiz.questions().getFirst().id()));
        assertThat(attempts.findAll().getFirst().getScore()).isEqualTo(2);
    }

    @Test
    void deletingQuizCascadesAttemptsAndAnswersAndPreservesOtherContent() throws Exception {
        var quiz = create(request(true, true, 2, null, null, null));
        submit(quiz, STUDENT, UUID.randomUUID(), 0, 1);
        long page = content.createPage(courseId, moduleId, new PageRequest("Side", "Tekst", true, null, null), TEACHER).id();
        mvc.perform(delete("/api/courses/" + courseId + "/modules/" + moduleId + "/items/" + quiz.content().id()).with(user(TEACHER)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(quizRepository.count()).isZero(); assertThat(attempts.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from quiz_answers", Integer.class)).isZero();
        assertThat(content.list(courseId, TEACHER).getFirst().items().getFirst().id()).isEqualTo(page);
        assertThat(content.list(courseId, TEACHER).getFirst().items().getFirst().position()).isZero();
    }

    @Test
    void concurrentSubmissionsCannotExceedTheAttemptLimit() throws Exception {
        var quiz = create(request(true, true, 1, null, null, null));
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(write(post(path(quiz) + "/attempts"), STUDENT, submission(quiz, UUID.randomUUID(), 0, 1))).andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(jobs.get(0).get(20, TimeUnit.SECONDS), jobs.get(1).get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(attempts.count()).isEqualTo(1);
    }

    private QuizResponse create(QuizRequest request) { return quizzes.create(courseId, moduleId, request, TEACHER); }
    private QuizRequest request(boolean published, boolean results, int limit, Instant deadline, Instant from, Instant until) {
        return new QuizRequest("Quiz", "Introduksjon", published, from, until, limit, deadline, results, List.of(
                new QuizQuestionRequest("Første spørsmål", List.of("A", "B"), 0, "Tenk på A"),
                new QuizQuestionRequest("Andre spørsmål", List.of("C", "D"), 1, "Tenk på D")));
    }
    private String base() { return "/api/courses/" + courseId + "/modules/" + moduleId + "/quizzes"; }
    private String path(QuizResponse quiz) { return base() + "/" + quiz.content().id(); }
    private QuizSubmission answers(QuizResponse quiz, UUID key, int first, int second) {
        return new QuizSubmission(key, List.of(new QuizSubmission.Answer(quiz.questions().get(0).id(), first), new QuizSubmission.Answer(quiz.questions().get(1).id(), second)));
    }
    private String submission(QuizResponse quiz, UUID key, int first, int second) {
        return "{\"submissionId\":\"" + key + "\",\"answers\":[{\"questionId\":" + quiz.questions().get(0).id() + ",\"option\":" + first
                + "},{\"questionId\":" + quiz.questions().get(1).id() + ",\"option\":" + second + "}]}";
    }
    private void submit(QuizResponse quiz, String email, UUID key, int first, int second) throws Exception {
        mvc.perform(write(post(path(quiz) + "/attempts"), email, submission(quiz, key, first, second))).andExpect(status().isCreated());
    }
    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String email, String body) {
        return request.with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
