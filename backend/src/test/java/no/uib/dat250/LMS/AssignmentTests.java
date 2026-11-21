package no.uib.dat250.LMS;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.group.GroupDtos.GroupRequest;
import no.uib.dat250.LMS.group.GroupService;
import no.uib.dat250.LMS.storage.*;
import no.uib.dat250.LMS.user.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=",
        "lms.demo.accounts.enabled=false", "lms.messaging.enabled=false", "lms.storage.cleanup.interval-ms=3600000"})
@AutoConfigureMockMvc
@Import({TestDatabaseConfiguration.class, CourseContentTests.FixedTime.class})
class AssignmentTests {
    static final GenericContainer<?> OBJECTS = new GenericContainer<>("chrislusf/seaweedfs:4.48")
            .withCommand("mini", "-dir=/data", "-volume.max=10", "-master.volumeSizeLimitMB=128", "-master.telemetry=false",
                    "-webdav=false", "-admin.ui=false", "-s3.port.iceberg=0", "-s3.port.lance=0")
            .withEnv("AWS_ACCESS_KEY_ID", "assignment-test").withEnv("AWS_SECRET_ACCESS_KEY", "assignment-test-secret")
            .withEnv("S3_BUCKET", "lms-submissions").withExposedPorts(8333)
            .waitingFor(Wait.forLogMessage(".*All enabled components are running and ready to use:.*\\n", 1));
    static { OBJECTS.start(); }
    @DynamicPropertySource static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("lms.storage.endpoint", () -> "http://" + OBJECTS.getHost() + ":" + OBJECTS.getMappedPort(8333));
        registry.add("lms.storage.access-key", () -> "assignment-test");
        registry.add("lms.storage.secret-key", () -> "assignment-test-secret");
    }
    private static final String TEACHER = "teacher@example.test", STUDENT = "student@example.test", OTHER = "other@example.test";
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final byte[] PDF = "%PDF-1.7\nTest submission\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final String BODY = """
            {"title":"  Innlevering  ","body":"Forklar modellen.","published":true,"acceptLate":false}
            """;
    @Autowired MockMvc mvc;
    @Autowired CourseRepository courses;
    @Autowired LmsUserRepository users;
    @Autowired CourseService courseService;
    @Autowired CourseContentService content;
    @Autowired AssignmentService assignments;
    @Autowired AssignmentSubmissionRepository submissions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectCleanup cleanup;
    @Autowired GroupService groupService;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @MockitoSpyBean S3FileStorage storage;
    long courseId, moduleId;

    @BeforeEach void seed() {
        reset(storage);
        courses.deleteAll(); users.deleteAll(); cleanup.removeDeletedFiles();
        for (var email : List.of(TEACHER, STUDENT, OTHER)) users.save(new LmsUser(email, email, "unused", email.equals(TEACHER) ? CourseRole.TEACHER : CourseRole.STUDENT));
        courseId = courseService.create(new CreateCourseRequest("DAT250", "", "Høst 2026"), TEACHER).id();
        courseService.addStudent(courseId, new AddStudentRequest(STUDENT), TEACHER);
        courseService.addStudent(courseId, new AddStudentRequest(OTHER), TEACHER);
        moduleId = content.createModule(courseId, new ModuleRequest("Uke 1"), TEACHER).id();
    }

    @Test void createsAssignmentAlongsidePagesAndQuizzes() throws Exception {
        content.createPage(courseId, moduleId, new PageRequest("Side", "Tekst", true, null, null), TEACHER);
        mvc.perform(post(base()).with(user(TEACHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.content.title").value("Innlevering"))
                .andExpect(jsonPath("$.content.type").value("ASSIGNMENT")).andExpect(jsonPath("$.content.position").value(1));
        mvc.perform(get("/api/courses/" + courseId + "/modules").with(user(STUDENT)))
                .andExpect(jsonPath("$[0].items[1].type").value("ASSIGNMENT"));
    }

    @Test void actualS3UploadCanBeDownloadedByOwnerAndTeacherWithSafeHeaders() throws Exception {
        long id = create(null, false);
        var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        for (var email : List.of(STUDENT, TEACHER)) mvc.perform(get(filePath(id, saved.id())).with(user(email)))
                .andExpect(status().isOk()).andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")));
        var key = jdbc.queryForObject("select object_key from assignment_submissions where id = ?", String.class, saved.id());
        assertThat(storage.get(key)).isEqualTo(PDF);
        assertThat(key).doesNotContain("besvarelse.pdf");
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://" + OBJECTS.getHost() + ":" + OBJECTS.getMappedPort(8333)
                    + "/lms-submissions/" + key)).GET().build(), HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isEqualTo(403);
        }
    }

    @Test void studentsCannotSeeOtherStudentsFilesOrHistory() throws Exception {
        long id = create(null, false);
        var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        mvc.perform(get(filePath(id, saved.id())).with(user(OTHER))).andExpect(status().isNotFound());
        mvc.perform(get(path(id)).with(user(OTHER))).andExpect(jsonPath("$.submissions", hasSize(0)));
        mvc.perform(get(path(id)).with(user(STUDENT))).andExpect(jsonPath("$.submissions[0].studentEmail").isEmpty());
        mvc.perform(get(path(id)).with(user(TEACHER))).andExpect(jsonPath("$.submissions[0].studentEmail").value(STUDENT));
    }

    @Test void gradeAndFeedbackStayHiddenUntilTeacherPublishesAndCanBeWithdrawn() throws Exception {
        long id = create(null, false);
        var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        for (boolean published : List.of(false, true, false)) {
            mvc.perform(put(path(id) + "/submissions/" + saved.id() + "/grade").with(user(TEACHER)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"grade\":\"B\",\"feedback\":\"God drøfting\",\"published\":" + published + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.grade").value("B"));
            var result = mvc.perform(get(path(id)).with(user(STUDENT))).andExpect(status().isOk());
            if (published) result.andExpect(jsonPath("$.submissions[0].grade").value("B"))
                    .andExpect(jsonPath("$.submissions[0].feedback").value("God drøfting"));
            else result.andExpect(jsonPath("$.submissions[0].grade").isEmpty())
                    .andExpect(jsonPath("$.submissions[0].feedback").isEmpty()).andExpect(jsonPath("$.submissions[0].gradedAt").isEmpty());
        }
    }

    @Test void resubmissionKeepsEarlierFileAndPublishedAssessment() throws Exception {
        long id = create(null, false);
        var first = submit(id, STUDENT, UUID.randomUUID(), pdf());
        assignments.assess(courseId, moduleId, id, first.id(), new GradeRequest(AssignmentGrade.PASSED, "Bra", true), TEACHER);
        var second = submit(id, STUDENT, UUID.randomUUID(), new MockMultipartFile("file", "ny.pdf", "application/pdf", "%PDF-1.7\nNew\n%%EOF".getBytes()));
        assertThat(second.version()).isEqualTo(2); assertThat(second.grade()).isNull();
        mvc.perform(get(path(id)).with(user(STUDENT))).andExpect(jsonPath("$.submissions", hasSize(2)))
                .andExpect(jsonPath("$.submissions[0].version").value(2)).andExpect(jsonPath("$.submissions[1].grade").value("PASSED"));
        mvc.perform(get(filePath(id, first.id())).with(user(STUDENT))).andExpect(content().bytes(PDF));
    }

    @Test void networkRetryIsIdempotentButDifferentContentWithSameKeyIsRejected() throws Exception {
        long id = create(null, false); var key = UUID.randomUUID();
        var first = submit(id, STUDENT, key, pdf());
        var retry = submit(id, STUDENT, key, pdf());
        assertThat(retry.id()).isEqualTo(first.id()); assertThat(submissions.count()).isEqualTo(1);
        assertThatThrownBy(() -> submit(id, STUDENT, key, new MockMultipartFile("file", "different.pdf", "application/pdf", PDF)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verify(storage, times(1)).put(anyString(), any(), anyString());
    }

    @ParameterizedTest @ValueSource(strings = {"2026-10-20T10:00:00Z", "2026-10-20T09:59:59Z"})
    void deadlineIsEnforcedAtBoundary(String deadline) throws Exception {
        long id = create(Instant.parse(deadline), false);
        upload(id, STUDENT, pdf()).andExpect(status().isConflict()); assertThat(submissions.count()).isZero();
    }
    @Test void acceptsJustBeforeDeadlineAndMarksAllowedLateSubmissions() {
        long open = create(NOW.plusSeconds(1), false);
        assertThat(submit(open, STUDENT, UUID.randomUUID(), pdf()).late()).isFalse();
        long late = create(NOW, true);
        assertThat(submit(late, STUDENT, UUID.randomUUID(), pdf()).late()).isTrue();
    }

    @Test void hiddenAssignmentsRejectReadUploadAndDownloadButTeachersCanRead() throws Exception {
        long id = create(null, false);
        var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        for (var request : List.of(new AssignmentRequest("Oppgave", "Tekst", false, null, null, null, false),
                new AssignmentRequest("Oppgave", "Tekst", true, NOW.plusSeconds(1), null, null, false),
                new AssignmentRequest("Oppgave", "Tekst", true, null, NOW, null, false))) {
            assignments.update(courseId, moduleId, id, request, TEACHER);
            mvc.perform(get(path(id)).with(user(STUDENT))).andExpect(status().isNotFound());
            upload(id, STUDENT, pdf()).andExpect(status().isNotFound());
            mvc.perform(get(filePath(id, saved.id())).with(user(STUDENT))).andExpect(status().isNotFound());
            mvc.perform(get(path(id)).with(user(TEACHER))).andExpect(status().isOk());
        }
    }

    @Test void onlyTeachersCanCreateEditOrGradeAndTeachersCannotSubmit() throws Exception {
        long id = create(null, false); var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        for (var request : List.of(post(base()), put(path(id)))) mvc.perform(request.with(user(STUDENT)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(put(path(id) + "/submissions/" + saved.id() + "/grade").with(user(STUDENT)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"grade\":\"A\",\"feedback\":\"\",\"published\":true}"))
                .andExpect(status().isForbidden());
        upload(id, TEACHER, pdf()).andExpect(status().isForbidden());
    }

    @Test void authenticationCsrfAndEveryPathScopeAreEnforced() throws Exception {
        long id = create(null, false); var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        mvc.perform(get(path(id))).andExpect(status().isUnauthorized());
        mvc.perform(multipart(path(id) + "/submissions").file(pdf()).param("submissionId", UUID.randomUUID().toString()).with(user(STUDENT)))
                .andExpect(status().isForbidden());
        for (var target : List.of(path(id), filePath(id, saved.id()))) {
            mvc.perform(get(target).with(user("outsider@example.test"))).andExpect(status().isNotFound());
            mvc.perform(get(target.replace("/modules/" + moduleId, "/modules/999999")).with(user(TEACHER))).andExpect(status().isNotFound());
        }
        long other = create(null, false);
        mvc.perform(get(filePath(other, saved.id())).with(user(TEACHER))).andExpect(status().isNotFound());
        long page = content.createPage(courseId, moduleId, new PageRequest("Side", "Tekst", true, null, null), TEACHER).id();
        mvc.perform(get(path(page)).with(user(TEACHER))).andExpect(status().isNotFound());
        mvc.perform(get("/api/courses/" + courseId + "/modules/" + moduleId + "/pages/" + id).with(user(TEACHER))).andExpect(status().isNotFound());
    }

    @Test void docxIsAcceptedOnlyWhenArchiveContainsWordDocument() throws Exception {
        long id = create(null, false);
        byte[] docx = zip("[Content_Types].xml", "word/document.xml");
        upload(id, STUDENT, new MockMultipartFile("file", "besvarelse.DOCX", "application/octet-stream", docx)).andExpect(status().isCreated());
        upload(id, STUDENT, new MockMultipartFile("file", "not-word.docx", "application/zip", zip("readme.txt"))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings = {"script.html", "program.exe", "falsk.pdf", "falsk.docx"})
    void rejectsUnsupportedOrDisguisedFiles(String filename) throws Exception {
        upload(create(null, false), STUDENT, new MockMultipartFile("file", filename, "application/pdf", "<script>alert(1)</script>".getBytes()))
                .andExpect(status().isBadRequest()); assertThat(submissions.count()).isZero();
    }
    @Test void rejectsEmptyAndOversizedFilesAndNormalizesUnsafeFilename() throws Exception {
        long id = create(null, false);
        upload(id, STUDENT, new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])).andExpect(status().isBadRequest());
        upload(id, STUDENT, new MockMultipartFile("file", "huge.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1])).andExpect(status().isPayloadTooLarge());
        upload(id, STUDENT, new MockMultipartFile("file", "C:\\fakepath\\..\\safe\r\n.pdf", "text/html", PDF))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.filename").value("safe.pdf"));
    }

    @Test void validatesMetadataAndGradePayloads() throws Exception {
        for (var body : List.of(BODY.replace("Forklar modellen.", ""), BODY.replace("true", "null"),
                BODY.replace("false", "null"), BODY.replace("\"acceptLate\":false", "\"acceptLate\":false,\"visibleFrom\":\"2026-10-21T00:00:00Z\",\"visibleUntil\":\"2026-10-20T00:00:00Z\""))) {
            mvc.perform(post(base()).with(user(TEACHER)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        long id = create(null, false); var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        for (var body : List.of("{\"grade\":null,\"feedback\":\"\",\"published\":true}",
                "{\"grade\":\"A\",\"feedback\":\"\",\"published\":null}", "{\"grade\":\"Z\",\"feedback\":\"\",\"published\":true}"))
            mvc.perform(put(path(id) + "/submissions/" + saved.id() + "/grade").with(user(TEACHER)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test void storageFailureDoesNotCreatePhantomSubmission() throws Exception {
        long id = create(null, false);
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Storage offline"))
                .when(storage).put(anyString(), any(), anyString());
        upload(id, STUDENT, pdf()).andExpect(status().isServiceUnavailable()); assertThat(submissions.count()).isZero();
        reset(storage);
        assertThat(submit(id, STUDENT, UUID.randomUUID(), pdf()).version()).isEqualTo(1);
    }

    @Test void databaseRollbackRemovesTheUploadedObject() {
        long id = create(null, false);
        jdbc.execute("ALTER TABLE assignment_submissions ADD CONSTRAINT test_reject_file CHECK (filename <> 'rollback.pdf')");
        try {
            assertThatThrownBy(() -> submit(id, STUDENT, UUID.randomUUID(), new MockMultipartFile("file", "rollback.pdf", "application/pdf", PDF)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            var key = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(storage).put(key.capture(), any(), anyString());
            verify(storage).delete(key.getValue());
            assertThat(submissions.count()).isZero();
            assertThatThrownBy(() -> storage.get(key.getValue())).isInstanceOf(ResponseStatusException.class);
        } finally { jdbc.execute("ALTER TABLE assignment_submissions DROP CONSTRAINT test_reject_file"); }
    }

    @Test void deletingModuleCascadesMetadataAndRetriesObjectCleanup() {
        long id = create(null, false); var saved = submit(id, STUDENT, UUID.randomUUID(), pdf());
        var key = jdbc.queryForObject("select object_key from assignment_submissions where id = ?", String.class, saved.id());
        content.deleteModule(courseId, moduleId, TEACHER);
        assertThat(submissions.count()).isZero();
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)).when(storage).delete(key);
        cleanup.removeDeletedFiles();
        assertThat(jdbc.queryForObject("select count(*) from pending_object_deletions", Integer.class)).isEqualTo(1);
        reset(storage); cleanup.removeDeletedFiles();
        assertThat(jdbc.queryForObject("select count(*) from pending_object_deletions", Integer.class)).isZero();
        assertThatThrownBy(() -> storage.get(key)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void concurrentUploadsHaveDistinctSequentialVersions() throws Exception {
        long id = create(null, false);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await(); return submit(id, STUDENT, UUID.randomUUID(), pdf());
            })).toList();
            start.countDown();
            assertThat(List.of(futures.get(0).get(30, TimeUnit.SECONDS).version(), futures.get(1).get(30, TimeUnit.SECONDS).version()))
                    .containsExactlyInAnyOrder(1, 2);
        }
    }
    @Test void groupSubmissionAndGradeAreSharedByAllMembers() throws Exception {
        long id = assignments.create(courseId, moduleId, new AssignmentRequest("Gruppeoppgave", "Lag noe", true, null, null, null, false,
                true, "https://git.app.uib.no/dat250/starter.git"), TEACHER).content().id();
        assertThatThrownBy(() -> submit(id, STUDENT, UUID.randomUUID(), pdf())).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("gruppe");
        var overview = groupService.create(courseId, new GroupRequest("Gruppe 1", 4, true), TEACHER);
        long groupId = overview.groups().getFirst().id();
        groupService.joinOpenGroup(courseId, groupId, STUDENT);
        groupService.joinOpenGroup(courseId, groupId, OTHER);

        var first = submit(id, STUDENT, UUID.randomUUID(), pdf());
        var second = submit(id, OTHER, UUID.randomUUID(), new MockMultipartFile("file", "kode.zip", "application/zip", zip("Main.java")));
        assertThat(second.version()).isEqualTo(2);
        assignments.assess(courseId, moduleId, id, second.id(), new GradeRequest(AssignmentGrade.B, "Bra jobba", true), TEACHER);

        var seenByOther = assignments.read(courseId, moduleId, id, STUDENT);
        assertThat(seenByOther.groupName()).isEqualTo("Gruppe 1");
        assertThat(seenByOther.starterRepoUrl()).isEqualTo("https://git.app.uib.no/dat250/starter.git");
        assertThat(seenByOther.submissions()).extracting(SubmissionResponse::id).containsExactly(second.id(), first.id());
        assertThat(seenByOther.submissions().getFirst().grade()).isEqualTo(AssignmentGrade.B);
        mvc.perform(get(filePath(id, second.id())).with(user(STUDENT))).andExpect(status().isOk());
        assertThat(assignments.read(courseId, moduleId, id, TEACHER).submissions().getFirst().groupName()).isEqualTo("Gruppe 1");
    }

    @Test void apiClientsUsingBasicAuthDoNotNeedCsrfToken() throws Exception {
        jdbc.update("update users set password_hash = ? where email = ?", passwords.encode("api-secret-1"), TEACHER);
        // No .with(csrf()): a third-party client authenticating per request should not need a CSRF token.
        mvc.perform(post(base()).with(httpBasic(TEACHER, "api-secret-1")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        // Cookie/session-based requests without a token are still rejected.
        mvc.perform(post(base()).with(user(TEACHER)).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    private long create(Instant deadline, boolean acceptLate) {
        return assignments.create(courseId, moduleId, new AssignmentRequest("Oppgave", "Forklar modellen", true, null, null, deadline, acceptLate), TEACHER).content().id();
    }
    private SubmissionResponse submit(long id, String email, UUID key, MockMultipartFile file) {
        return assignments.submit(courseId, moduleId, id, key, file, email);
    }
    private org.springframework.test.web.servlet.ResultActions upload(long id, String email, MockMultipartFile file) throws Exception {
        return mvc.perform(multipart(path(id) + "/submissions").file(file).param("submissionId", UUID.randomUUID().toString()).with(user(email)).with(csrf()));
    }
    private MockMultipartFile pdf() { return new MockMultipartFile("file", "besvarelse.pdf", "application/pdf", PDF); }
    private String base() { return "/api/courses/" + courseId + "/modules/" + moduleId + "/assignments"; }
    private String path(long id) { return base() + "/" + id; }
    private String filePath(long id, long submissionId) { return path(id) + "/submissions/" + submissionId + "/file"; }
    private byte[] zip(String... entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var name : entries) { zip.putNextEntry(new ZipEntry(name)); zip.write("<xml/>".getBytes()); zip.closeEntry(); }
        }
        return output.toByteArray();
    }
}
