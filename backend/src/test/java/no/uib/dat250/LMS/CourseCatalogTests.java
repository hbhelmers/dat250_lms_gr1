package no.uib.dat250.LMS;

import java.util.List;
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
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.user.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=",
        "lms.demo.accounts.enabled=false", "lms.messaging.enabled=false", "lms.catalog.seed.enabled=false"})
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class CourseCatalogTests {
    static final String TEACHER = "teacher@example.test", STUDENT = "student@example.test", OTHER = "other@example.test";
    static final String BODY = """
            {"title":"INF999 – Testemne","description":"Beskrivelse","semester":"Høst 2026",
             "institution":"UIB","courseCode":" inf999 ","selfEnrollment":true}
            """;
    @Autowired MockMvc mvc;
    @Autowired CourseRepository courses;
    @Autowired LmsUserRepository users;
    @Autowired CourseEnrollmentRepository enrollments;
    @Autowired CourseService service;
    @Autowired CourseContentService content;
    @Autowired CatalogSeedService seeder;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seedUsers() {
        courses.deleteAll(); users.deleteAll();
        users.save(new LmsUser("Testlærer", TEACHER, "unused", CourseRole.TEACHER));
        users.save(new LmsUser("Teststudent", STUDENT, "unused", CourseRole.STUDENT));
        users.save(new LmsUser("Annen student", OTHER, "unused", CourseRole.STUDENT));
    }

    @Test void studentProfilesCannotCreateCoursesEvenWithTeacherRoleInRequest() throws Exception {
        mvc.perform(post("/api/courses").with(user(STUDENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"institution\":", "\"role\":\"TEACHER\",\"preferredRole\":\"TEACHER\",\"institution\":")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Bare lærerkontoer")));
        assertThat(courses.count()).isZero(); assertThat(enrollments.count()).isZero();
    }

    @Test void teacherCreatesAnOpenCourseWithInstitutionAndNormalizedCode() throws Exception {
        mvc.perform(post("/api/courses").with(user(TEACHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.institution").value("UIB"))
                .andExpect(jsonPath("$.courseCode").value("INF999")).andExpect(jsonPath("$.selfEnrollment").value(true))
                .andExpect(jsonPath("$.role").value("TEACHER"));
        mvc.perform(get(catalog("UIB")).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].membershipRole").isEmpty());
        mvc.perform(get(catalog("HVL")).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void catalogRequiresAnInstitutionAndAuthentication() throws Exception {
        mvc.perform(get(catalog("HVL"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/courses/catalog").with(user(STUDENT))).andExpect(status().isBadRequest());
        mvc.perform(get(catalog("NTNU")).with(user(STUDENT))).andExpect(status().isBadRequest());
        mvc.perform(get(catalog("HVL")).with(user("unknown@example.test"))).andExpect(status().isUnauthorized());
    }

    @Test void catalogPreviewDoesNotGrantAccessToContentOrRoster() throws Exception {
        var course = create(Institution.UIB, true);
        content.createModule(course.id(), new ModuleRequest("Modul"), TEACHER);
        mvc.perform(get(catalog("UIB")).with(user(STUDENT))).andExpect(jsonPath("$[0].id").value(course.id()))
                .andExpect(jsonPath("$[0].modules").doesNotExist()).andExpect(jsonPath("$[0].members").doesNotExist());
        mvc.perform(get("/api/courses/" + course.id()).with(user(STUDENT))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/" + course.id() + "/modules").with(user(STUDENT))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/" + course.id() + "/members").with(user(STUDENT))).andExpect(status().isNotFound());
    }

    @Test void studentCanEnrollThenImmediatelyReadPublishedContent() throws Exception {
        var course = create(Institution.HVL, true);
        var module = content.createModule(course.id(), new ModuleRequest("Introduksjon"), TEACHER);
        content.createPage(course.id(), module.id(), new PageRequest("Velkommen", "Tekst", true, null, null), TEACHER);
        mvc.perform(post(enroll(course.id())).with(user(STUDENT)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"));
        mvc.perform(get("/api/courses").with(user(STUDENT))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/courses/" + course.id() + "/modules").with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].title").value("Velkommen"));
        mvc.perform(get(catalog("HVL")).with(user(STUDENT))).andExpect(jsonPath("$[0].membershipRole").value("STUDENT"));
        mvc.perform(get("/api/courses/" + course.id() + "/members").with(user(TEACHER)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test void enrollmentAlwaysUsesAuthenticatedUserAndStudentRole() throws Exception {
        var course = create(Institution.HVL, true);
        var other = users.findByEmail(OTHER).orElseThrow();
        mvc.perform(post(enroll(course.id())).with(user(STUDENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + other.getId() + ",\"email\":\"other@example.test\",\"role\":\"TEACHER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        assertThat(enrollments.findMembership(course.id(), OTHER)).isEmpty();
        mvc.perform(put("/api/courses/" + course.id()).with(user(STUDENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/courses").with(user(STUDENT)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test void retryingEnrollmentIsIdempotentAndTeacherRoleCannotBeDowngraded() throws Exception {
        var course = create(Institution.UIB, true);
        for (int index = 0; index < 2; index++) mvc.perform(post(enroll(course.id())).with(user(STUDENT)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        mvc.perform(post(enroll(course.id())).with(user(TEACHER)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test void closedAndLegacyCoursesCannotBeJoinedOrDiscovered() throws Exception {
        var closed = create(Institution.HVL, false);
        var legacy = courses.save(new Course("Eldre emne", "Tekst", "Høst 2026"));
        for (var id : List.of(closed.id(), legacy.getId(), 999999L))
            mvc.perform(post(enroll(id)).with(user(STUDENT)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(catalog("HVL")).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(0)));
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test void enrollmentRequiresAuthenticationAndCsrf() throws Exception {
        var course = create(Institution.HVL, true);
        mvc.perform(post(enroll(course.id())).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(enroll(course.id())).with(user(STUDENT))).andExpect(status().isForbidden());
        assertThat(enrollments.count()).isEqualTo(1);
    }

    @Test void teacherCanCloseEnrollmentWithoutRemovingExistingStudents() throws Exception {
        var course = create(Institution.HVL, true);
        service.enroll(course.id(), STUDENT);
        service.update(course.id(), new CreateCourseRequest("Lukket emne", "Tekst", "Høst 2026", Institution.HVL, "DAT999", false), TEACHER);
        mvc.perform(get(catalog("HVL")).with(user(OTHER))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post(enroll(course.id())).with(user(OTHER)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/" + course.id()).with(user(STUDENT))).andExpect(status().isOk());
        mvc.perform(post(enroll(course.id())).with(user(STUDENT)).with(csrf())).andExpect(status().isOk());
    }

    @Test void oldUpdatePayloadPreservesExistingInstitutionCodeAndEnrollmentSettings() throws Exception {
        var course = create(Institution.UIB, false);
        mvc.perform(put("/api/courses/" + course.id()).with(user(TEACHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Ny tittel\",\"description\":\"Tekst\",\"semester\":\"Vår 2027\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.institution").value("UIB"))
                .andExpect(jsonPath("$.courseCode").value("TEST100")).andExpect(jsonPath("$.selfEnrollment").value(false));
    }

    @ParameterizedTest @ValueSource(strings = {"a", "INF 100", "<script>", "ABCDEFGHIJKLMNOPQRSTU"})
    void invalidCourseCodesAreRejected(String code) throws Exception {
        mvc.perform(post("/api/courses").with(user(TEACHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace(" inf999 ", code))).andExpect(status().isBadRequest());
        assertThat(courses.count()).isZero();
    }

    @Test void seedCreatesFourteenSeparateHvlAndUibRoomsWithTeacherAndWelcomeContent() throws Exception {
        seedCatalog();
        assertThat(courses.count()).isEqualTo(14); assertThat(enrollments.count()).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_items", Integer.class)).isEqualTo(14);
        mvc.perform(get(catalog("HVL")).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(9)));
        mvc.perform(get(catalog("UIB")).with(user(STUDENT))).andExpect(jsonPath("$", hasSize(5)));
        assertThat(service.catalog(Institution.HVL, STUDENT)).extracting(CatalogCourseResponse::courseCode)
                .contains("DAT100", "DAT250", "ING100", "ING202", "ING161", "ING162");
        assertThat(service.catalog(Institution.UIB, STUDENT)).extracting(CatalogCourseResponse::courseCode)
                .containsExactly("INF100", "INF101", "INF102", "INF112", "INF226");
        var uib = courses.findByCatalogKey("UIB:INF100").orElseThrow();
        service.enroll(uib.getId(), STUDENT);
        var modules = content.list(uib.getId(), STUDENT);
        assertThat(modules).hasSize(1); assertThat(modules.getFirst().items()).hasSize(1);
        assertThat(modules.getFirst().items().getFirst().published()).isTrue();
    }

    @Test void restartingSeedPreservesTeacherEditsClosedEnrollmentAndStudentMemberships() {
        seedCatalog();
        var course = courses.findByCatalogKey("HVL:DAT107").orElseThrow();
        service.enroll(course.getId(), STUDENT);
        service.update(course.getId(), new CreateCourseRequest("Egen databasetittel", "Egen beskrivelse", "Vår 2027", Institution.HVL, "DAT107", false), TEACHER);
        content.createModule(course.getId(), new ModuleRequest("Lærerens arbeid"), TEACHER);
        seedCatalog();
        assertThat(courses.count()).isEqualTo(14); assertThat(enrollments.count()).isEqualTo(15);
        var saved = service.findById(course.getId(), STUDENT);
        assertThat(saved.title()).isEqualTo("Egen databasetittel"); assertThat(saved.selfEnrollment()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM course_modules WHERE course_id = ?", Integer.class, course.getId())).isEqualTo(2);
    }

    @Test void existingSharedTemplateIsAdoptedWithContentAndMembershipsIntact() {
        var teacher = users.findByEmail(TEACHER).orElseThrow();
        var course = courses.save(Course.example("DAT250 – Programvareteknologi", "Eksisterende beskrivelse", "Høst 2026", "DAT250"));
        enrollments.save(new CourseEnrollment(course, teacher, CourseRole.TEACHER));
        enrollments.save(new CourseEnrollment(course, users.findByEmail(STUDENT).orElseThrow(), CourseRole.STUDENT));
        var module = content.createModule(course.getId(), new ModuleRequest("Eksisterende modul"), TEACHER);
        content.createPage(course.getId(), module.id(), new PageRequest("Lærerens side", "Behold dette", true, null, null), TEACHER);
        seedCatalog();
        assertThat(courses.count()).isEqualTo(14);
        assertThat(courses.findByCatalogKey("HVL:DAT250").orElseThrow().getId()).isEqualTo(course.getId());
        assertThat(content.list(course.getId(), STUDENT).getFirst().items().getFirst().body()).isEqualTo("Behold dette");
    }

    @Test void changingInstitutionOrCodeClearsTheOldOfficialReference() {
        seedCatalog(); var course = courses.findByCatalogKey("UIB:INF100").orElseThrow();
        var saved = service.update(course.getId(), new CreateCourseRequest("Nytt emne", "Tekst", "Høst 2026", Institution.HVL, "DAT999", true), TEACHER);
        assertThat(saved.officialUrl()).isNull();
    }

    @Test void concurrentSelfEnrollmentOnlyCreatesOneMembership() throws Exception {
        var course = create(Institution.UIB, true); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.enroll(course.id(), STUDENT); });
            var second = executor.submit(() -> { start.await(); return service.enroll(course.id(), STUDENT); });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).id()).isEqualTo(course.id());
            assertThat(second.get(15, TimeUnit.SECONDS).id()).isEqualTo(course.id());
        }
        assertThat(enrollments.count()).isEqualTo(2);
    }

    @Test void concurrentCatalogSeedingDoesNotDuplicateCourses() throws Exception {
        var teacher = users.findByEmail(TEACHER).orElseThrow(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); seeder.seed(teacher); return true; });
            var second = executor.submit(() -> { start.await(); seeder.seed(teacher); return true; });
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue(); assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(courses.count()).isEqualTo(14); assertThat(enrollments.count()).isEqualTo(14);
    }

    private CourseResponse create(Institution institution, boolean open) {
        return service.create(new CreateCourseRequest("Testemne", "Beskrivelse", "Høst 2026", institution, "TEST100", open), TEACHER);
    }
    private void seedCatalog() { seeder.seed(users.findByEmail(TEACHER).orElseThrow()); }
    private String catalog(String institution) { return "/api/courses/catalog?institution=" + institution; }
    private String enroll(long id) { return "/api/courses/" + id + "/enroll"; }
}
