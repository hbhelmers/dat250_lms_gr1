package no.uib.dat250.LMS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import no.uib.dat250.LMS.course.Course;
import no.uib.dat250.LMS.course.CourseEnrollmentRepository;
import no.uib.dat250.LMS.course.CourseRepository;
import no.uib.dat250.LMS.course.CourseService;
import no.uib.dat250.LMS.course.CreateCourseRequest;
import no.uib.dat250.LMS.course.LegacyCourseOwnership;
import no.uib.dat250.LMS.user.LmsUserRepository;
import no.uib.dat250.LMS.user.RegistrationRequest;
import no.uib.dat250.LMS.user.RegistrationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=legacy@example.test", "lms.demo.accounts.enabled=false"
})
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class CourseMembershipTests {
    private static final String TEACHER = "teacher@example.test";
    private static final String STUDENT = "student@example.test";
    private static final String OUTSIDER = "outsider@example.test";
    private static final String PASSWORD = "et-langt-testpassord-2026";
    private static final String COURSE_BODY = """
            {"title":"  Databaser  ","description":"  Lær om ORM.  ","semester":"  Høst 2026  "}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private CourseRepository courses;
    @Autowired private CourseEnrollmentRepository enrollments;
    @Autowired private CourseService courseService;
    @Autowired private LmsUserRepository users;
    @Autowired private RegistrationService registration;
    @Autowired private LegacyCourseOwnership legacyOwnership;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clearAndSeedUsers() {
        courses.deleteAll();
        users.deleteAll();
        registration.register(new RegistrationRequest("Ada Lærer", TEACHER, PASSWORD));
        registration.register(new RegistrationRequest("Bo Student", STUDENT, PASSWORD));
        registration.register(new RegistrationRequest("Cam Utenfor", OUTSIDER, PASSWORD));
    }

    @Test
    void creatorAutomaticallyBecomesTeacher() throws Exception {
        mvc.perform(post("/api/courses").with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("TEACHER"));
        assertThat(courses.count()).isEqualTo(1);
        assertThat(enrollments.count()).isEqualTo(1);
        long id = courses.findAll().getFirst().getId();
        mvc.perform(get("/api/courses/{id}/members", id).with(user(TEACHER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(TEACHER))
                .andExpect(jsonPath("$[0].role").value("TEACHER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void usersOnlySeeCoursesTheyBelongTo() throws Exception {
        long teacherCourse = createCourse(TEACHER);
        long studentCourse = createCourse(STUDENT);
        mvc.perform(get("/api/courses").with(user(TEACHER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(teacherCourse));
        mvc.perform(get("/api/courses").with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(studentCourse));
        mvc.perform(get("/api/courses").with(user(OUTSIDER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void outsidersCannotReadModifyOrEnrollThemselves() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(get("/api/courses/{id}", id).with(user(OUTSIDER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/{id}/members", id).with(user(OUTSIDER))).andExpect(status().isNotFound());
        mvc.perform(put("/api/courses/{id}", id).with(user(OUTSIDER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY)).andExpect(status().isNotFound());
        mvc.perform(post("/api/courses/{id}/students", id).with(user(OUTSIDER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(emailBody(OUTSIDER))).andExpect(status().isNotFound());
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test
    void teacherCanAddExistingStudentWithNormalizedEmail() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emailBody("  STUDENT@EXAMPLE.TEST  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.email").value(STUDENT)).andExpect(jsonPath("$.name").value("Bo Student"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/courses/{id}", id).with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void studentsCanReadButCannotEditOrAdministerMembers() throws Exception {
        long id = createCourse(TEACHER);
        addStudent(id, STUDENT);
        mvc.perform(get("/api/courses/{id}", id).with(user(STUDENT))).andExpect(status().isOk());
        mvc.perform(get("/api/courses/{id}/members", id).with(user(STUDENT))).andExpect(status().isForbidden());
        mvc.perform(put("/api/courses/{id}", id).with(user(STUDENT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/courses/{id}/students", id).with(user(STUDENT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emailBody(OUTSIDER)))
                .andExpect(status().isForbidden());
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void roleIsDeterminedByServerInsteadOfRequestBody() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@example.test\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void samePersonCanHaveDifferentRolesInDifferentCourses() throws Exception {
        long first = createCourse(TEACHER);
        long second = createCourse(STUDENT);
        addStudent(first, STUDENT);
        mvc.perform(post("/api/courses/{id}/students", second).with(user(STUDENT)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emailBody(TEACHER)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/courses/{id}", first).with(user(TEACHER)))
                .andExpect(jsonPath("$.role").value("TEACHER"));
        mvc.perform(get("/api/courses/{id}", second).with(user(TEACHER)))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        mvc.perform(put("/api/courses/{id}", second).with(user(TEACHER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY)).andExpect(status().isForbidden());
    }

    @Test
    void teacherCanEditAndStudentSeesSavedChanges() throws Exception {
        long id = createCourse(TEACHER);
        addStudent(id, STUDENT);
        mvc.perform(put("/api/courses/{id}", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY.replace("Databaser", "Nytt emnenavn")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Nytt emnenavn"))
                .andExpect(jsonPath("$.role").value("TEACHER"));
        mvc.perform(get("/api/courses/{id}", id).with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Nytt emnenavn"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void teacherCannotAccidentallyDowngradeOwnRole() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(emailBody(TEACHER))).andExpect(status().isConflict());
        mvc.perform(get("/api/courses/{id}", id).with(user(TEACHER)))
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    @Test
    void duplicateEnrollmentIsRejected() throws Exception {
        long id = createCourse(TEACHER);
        addStudent(id, STUDENT);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(emailBody(STUDENT))).andExpect(status().isConflict());
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"email\":null}", "{\"email\":\"  \"}", "{\"email\":\"not-an-email\"}" })
    void invalidStudentEmailIsRejected(String body) throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists());
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test
    void unregisteredStudentIsNotAutomaticallyCreated() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(emailBody("missing@example.test")))
                .andExpect(status().isNotFound());
        assertThat(users.count()).isEqualTo(3);
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test
    void writesRequireCsrfEvenForTeacher() throws Exception {
        long id = createCourse(TEACHER);
        mvc.perform(put("/api/courses/{id}", id).with(user(TEACHER))
                .contentType(MediaType.APPLICATION_JSON).content(COURSE_BODY)).andExpect(status().isForbidden());
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER))
                .contentType(MediaType.APPLICATION_JSON).content(emailBody(STUDENT))).andExpect(status().isForbidden());
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test
    void newEnrollmentTakesEffectWithoutLoggingInAgain() throws Exception {
        long id = createCourse(TEACHER);
        var result = mvc.perform(formLogin("/api/login").user(STUDENT).password(PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/courses").session(session)).andExpect(jsonPath("$", hasSize(0)));
        addStudent(id, STUDENT);
        mvc.perform(get("/api/courses").session(session)).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("STUDENT"));
    }

    @Test
    void simultaneousEnrollmentRequestsOnlyCreateOneMembership() throws Exception {
        long id = createCourse(TEACHER);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> enrollConcurrently(id, start));
            var second = executor.submit(() -> enrollConcurrently(id, start));
            start.countDown();
            assertThat(new int[] { first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS) })
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test
    void pendingLegacyCoursesWaitForExplicitlyConfiguredTeacher() throws Exception {
        long legacyId = createLegacyCourse();
        mvc.perform(get("/api/courses/{id}", legacyId).with(user(TEACHER))).andExpect(status().isNotFound());
        registration.register(new RegistrationRequest("Den valgte læreren", "legacy@example.test", PASSWORD));
        mvc.perform(get("/api/courses/{id}", legacyId).with(user("legacy@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));
        assertThat(jdbc.queryForObject("select legacy_owner_pending from courses where id = ?", Boolean.class, legacyId))
                .isFalse();
    }

    @Test
    void legacyAssignmentAtStartupIsIdempotentAndDoesNotTakeOverNewCourses() throws Exception {
        registration.register(new RegistrationRequest("Den valgte læreren", "legacy@example.test", PASSWORD));
        long legacyId = createLegacyCourse();
        long newId = createCourse(STUDENT);
        legacyOwnership.run(new DefaultApplicationArguments());
        legacyOwnership.run(new DefaultApplicationArguments());
        assertThat(enrollments.count()).isEqualTo(2);
        mvc.perform(get("/api/courses/{id}", legacyId).with(user("legacy@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));
        mvc.perform(get("/api/courses/{id}", newId).with(user("legacy@example.test"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/{id}", newId).with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TEACHER"));
    }

    private long createCourse(String email) {
        return courseService.create(new CreateCourseRequest("Databaser", "Lær om ORM.", "Høst 2026"), email).id();
    }

    private long createLegacyCourse() {
        long id = courses.save(new Course("Eldre emne", "Eksisterende innhold", "Høst 2026")).getId();
        jdbc.update("update courses set legacy_owner_pending = true where id = ?", id);
        return id;
    }

    private void addStudent(long id, String email) throws Exception {
        mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emailBody(email)))
                .andExpect(status().isCreated());
    }

    private int enrollConcurrently(long id, CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/courses/{id}/students", id).with(user(TEACHER)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(emailBody(STUDENT)))
                .andReturn().getResponse().getStatus();
    }

    private static String emailBody(String email) { return "{\"email\":\"" + email + "\"}"; }
}
