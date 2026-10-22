package no.uib.dat250.LMS;

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
import org.springframework.test.web.servlet.MockMvc;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.user.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=", "lms.demo.accounts.enabled=true", "lms.messaging.enabled=false"})
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class ExampleRegistrationTests {
    private static final String EMAIL = "ny@example.test";
    private static final String PASSWORD = "abcdefg!";
    @Autowired private MockMvc mvc;
    @Autowired private LmsUserRepository users;
    @Autowired private CourseRepository courses;
    @Autowired private CourseEnrollmentRepository enrollments;
    @Autowired private CourseService courseService;
    @Autowired private CourseContentService content;
    @Autowired private ContentItemRepository items;
    @Autowired private CourseModuleRepository modules;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizService quizzes;
    @Autowired private AssignmentService assignments;
    @Autowired private QuizAttemptRepository attempts;
    @Autowired private ExampleSetupService examples;

    @BeforeEach void clear() { courses.deleteAll(); users.deleteAll(); }

    @ParameterizedTest @ValueSource(strings = {"STUDENT", "TEACHER"})
    void usersCanChooseTheirProfileAndStartWithoutExamples(String role) throws Exception {
        register(EMAIL, role, false);
        assertThat(users.findByEmail(EMAIL).orElseThrow().getPreferredRole()).isEqualTo(CourseRole.valueOf(role));
        assertThat(courses.count()).isZero(); assertThat(enrollments.count()).isZero();
        var result = mvc.perform(formLogin("/api/login").user(EMAIL).password(PASSWORD)).andExpect(status().isNoContent()).andReturn();
        mvc.perform(get("/api/session").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(jsonPath("$.preferredRole").value(role));
        assertThat(courseService.findAll(EMAIL)).isEmpty();
    }

    @Test
    void teacherTemplateContainsEditableModulesPagesDraftsQuizzesAndAssignments() throws Exception {
        register(EMAIL, "TEACHER", true);
        assertThat(courses.count()).isEqualTo(2); assertThat(modules.count()).isEqualTo(4);
        assertThat(items.count()).isEqualTo(10); assertThat(quizRepository.count()).isEqualTo(2);
        var sampleCourses = courseService.findAll(EMAIL);
        assertThat(sampleCourses).extracting(CourseResponse::role).containsOnly(CourseRole.TEACHER);
        for (var course : sampleCourses) {
            var groups = content.list(course.id(), EMAIL);
            assertThat(groups).hasSize(2);
            var quizItem = groups.getFirst().items().stream().filter(item -> item.type() == ContentType.QUIZ).findFirst().orElseThrow();
            var quiz = quizzes.read(course.id(), groups.getFirst().id(), quizItem.id(), EMAIL);
            assertThat(quiz.questions()).hasSize(2);
            assertThat(quiz.questions().getFirst().correctOption()).isZero();
            var assignmentItem = groups.getLast().items().stream().filter(item -> item.type() == ContentType.ASSIGNMENT).findFirst().orElseThrow();
            var assignment = assignments.read(course.id(), groups.getLast().id(), assignmentItem.id(), EMAIL);
            assertThat(assignment.content().published()).isTrue();
            assertThat(assignment.submissions()).isEmpty();
            courseService.update(course.id(), new CreateCourseRequest("Mitt fag", "Egen beskrivelse", "Høst 2026"), EMAIL);
        }
    }

    @Test
    void studentJoinsOnlyTheSharedExampleCoursesAndCanTakeAQuiz() throws Exception {
        examples.initializeDemoAccounts();
        var teacherCourses = courseService.findAll(ExampleSetupService.TEACHER_EMAIL);
        // Ordinary courses owned by the example teacher are not part of the template.
        courseService.create(new CreateCourseRequest("Annet fag", "", "Høst 2026"), ExampleSetupService.TEACHER_EMAIL);
        register(EMAIL, "STUDENT", true);
        var studentCourses = courseService.findAll(EMAIL);
        assertThat(studentCourses).hasSize(2).extracting(CourseResponse::id).containsExactlyInAnyOrderElementsOf(
                teacherCourses.stream().map(CourseResponse::id).toList());
        assertThat(studentCourses).extracting(CourseResponse::role).containsOnly(CourseRole.STUDENT);
        long courseId = studentCourses.getFirst().id();
        var groups = content.list(courseId, EMAIL);
        assertThat(groups.stream().flatMap(module -> module.items().stream())).allMatch(ContentResponse::published);
        var quizItem = groups.getFirst().items().stream().filter(item -> item.type() == ContentType.QUIZ).findFirst().orElseThrow();
        var quiz = quizzes.read(courseId, groups.getFirst().id(), quizItem.id(), EMAIL);
        assertThat(quiz.questions().getFirst().correctOption()).isNull();
        var result = quizzes.submit(courseId, groups.getFirst().id(), quizItem.id(), new QuizSubmission(UUID.randomUUID(),
                quiz.questions().stream().map(question -> new QuizSubmission.Answer(question.id(), 0)).toList()), EMAIL);
        assertThat(result.total()).isEqualTo(2);
        mvc.perform(put("/api/courses/" + courseId).with(user(EMAIL)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Kan ikke endre\",\"description\":\"Beskrivelse\",\"semester\":\"Høst 2026\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newTeachersReceiveIndependentCopiesAndNoAccessToOtherTeachersCourses() throws Exception {
        examples.initializeDemoAccounts();
        var shared = courseService.findAll(ExampleSetupService.TEACHER_EMAIL);
        register(EMAIL, "TEACHER", true);
        assertThat(courses.count()).isEqualTo(4);
        assertThat(courseService.findAll(EMAIL)).extracting(CourseResponse::id)
                .doesNotContainAnyElementsOf(shared.stream().map(CourseResponse::id).toList());
        mvc.perform(get("/api/courses/" + shared.getFirst().id()).with(user(EMAIL))).andExpect(status().isNotFound());
    }

    @Test
    void selectedProfileDoesNotGrantTeacherRightsInUnrelatedCourses() throws Exception {
        examples.initializeDemoAccounts();
        register(EMAIL, "TEACHER", false);
        long sharedCourse = courseService.findAll(ExampleSetupService.TEACHER_EMAIL).getFirst().id();
        mvc.perform(get("/api/courses/" + sharedCourse + "/members").with(user(EMAIL))).andExpect(status().isNotFound());
        courseService.addStudent(sharedCourse, new AddStudentRequest(EMAIL), ExampleSetupService.TEACHER_EMAIL);
        assertThat(courseService.findById(sharedCourse, EMAIL).role()).isEqualTo(CourseRole.STUDENT);
        mvc.perform(get("/api/courses/" + sharedCourse + "/members").with(user(EMAIL))).andExpect(status().isForbidden());
    }

    @Test
    void testAccountsCanLogInAndInitializationNeverResetsExistingWork() throws Exception {
        examples.initializeDemoAccounts();
        var teacherHash = users.findByEmail(ExampleSetupService.TEACHER_EMAIL).orElseThrow().getPasswordHash();
        long courseId = courseService.findAll(ExampleSetupService.TEACHER_EMAIL).getFirst().id();
        courseService.update(courseId, new CreateCourseRequest("Behold endringen", "", "Høst 2026"), ExampleSetupService.TEACHER_EMAIL);
        examples.initializeDemoAccounts();
        assertThat(users.count()).isEqualTo(2); assertThat(courses.count()).isEqualTo(2);
        assertThat(enrollments.count()).isEqualTo(4);
        assertThat(users.findByEmail(ExampleSetupService.TEACHER_EMAIL).orElseThrow().getPasswordHash()).isEqualTo(teacherHash);
        assertThat(courses.findById(courseId).orElseThrow().getTitle()).isEqualTo("Behold endringen");
        for (var email : List.of(ExampleSetupService.TEACHER_EMAIL, ExampleSetupService.STUDENT_EMAIL)) {
            mvc.perform(formLogin("/api/login").user(email).password(ExampleSetupService.DEMO_PASSWORD)).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/demo-access")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.teacherEmail").value(ExampleSetupService.TEACHER_EMAIL))
                .andExpect(jsonPath("$.password").value(ExampleSetupService.DEMO_PASSWORD));
    }

    @Test
    void choosingNoTemplateDoesNotEnrollTheUserInExistingExampleCourses() throws Exception {
        examples.initializeDemoAccounts(); register(EMAIL, "STUDENT", false);
        assertThat(courseService.findAll(EMAIL)).isEmpty(); assertThat(enrollments.count()).isEqualTo(4);
    }

    @Test
    void duplicateRegistrationDoesNotLeaveExtraCoursesOrAccounts() throws Exception {
        register(EMAIL, "TEACHER", true);
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(EMAIL, "TEACHER", true)))
                .andExpect(status().isConflict());
        assertThat(courses.count()).isEqualTo(2); assertThat(users.count()).isEqualTo(1);
        examples.addExamples(users.findByEmail(EMAIL).orElseThrow());
        assertThat(courses.count()).isEqualTo(2); assertThat(items.count()).isEqualTo(10);
    }

    @Test
    void concurrentStudentRegistrationsCreateOnlyOneSharedTeacherAndTemplate() throws Exception {
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown(); assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("student" + index + "@example.test", "STUDENT", true))).andReturn().getResponse().getStatus();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var job : jobs) assertThat(job.get(30, TimeUnit.SECONDS)).isEqualTo(201);
        }
        assertThat(users.count()).isEqualTo(3); assertThat(courses.count()).isEqualTo(2); assertThat(enrollments.count()).isEqualTo(6);
    }

    private void register(String email, String role, boolean include) throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(email, role, include)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.preferredRole").value(role));
        mvc.perform(get("/api/courses").with(user(email))).andExpect(jsonPath("$", hasSize(include ? 2 : 0)));
    }
    private static String body(String email, String role, boolean include) {
        return "{\"name\":\"Ny bruker\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"preferredRole\":\"" + role + "\",\"includeExamples\":" + include + "}";
    }
}
