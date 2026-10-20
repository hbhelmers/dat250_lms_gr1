package no.uib.dat250.LMS;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;
import no.uib.dat250.LMS.user.LmsUser;
import no.uib.dat250.LMS.user.LmsUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=" })
@AutoConfigureMockMvc
@Import({ TestDatabaseConfiguration.class, CourseContentTests.FixedTime.class })
class CourseContentTests {
    private static final String TEACHER = "teacher@example.test";
    private static final String STUDENT = "student@example.test";
    private static final String OUTSIDER = "outsider@example.test";
    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final String PAGE_BODY = "{\"title\":\"  Innføring  \",\"body\":\"## Velkommen\\n\\nLær **Java**.\",\"published\":false}";

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary Clock fixedContentClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }

    @Autowired private MockMvc mvc;
    @Autowired private CourseRepository courses;
    @Autowired private CourseService courseService;
    @Autowired private LmsUserRepository users;
    @Autowired private CourseContentService content;
    @Autowired private CourseModuleRepository modules;
    @Autowired private ContentItemRepository items;
    private long courseId;

    @BeforeEach
    void seedCourseAndMembers() {
        courses.deleteAll();
        users.deleteAll();
        // HTTP authentication itself is covered by the registration/session tests.
        for (var email : List.of(TEACHER, STUDENT, OUTSIDER)) users.save(new LmsUser(email, email, "unused"));
        courseId = courseService.create(new CreateCourseRequest("DAT250", "Innholdstest", "Høst 2026"), TEACHER).id();
        courseService.addStudent(courseId, new AddStudentRequest(STUDENT), TEACHER);
    }

    @Test
    void teacherCanCreateAndRenamePersistentModules() throws Exception {
        mvc.perform(write(post(base()), TEACHER, "{\"title\":\"  Uke 1  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Uke 1"))
                .andExpect(jsonPath("$.position").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
        long module = modules.findAll().getFirst().getId();
        mvc.perform(write(put(base() + "/" + module), TEACHER, "{\"title\":\"Introduksjon\"}"))
                .andExpect(status().isOk());
        assertThat(modules.findById(module).orElseThrow().getTitle()).isEqualTo("Introduksjon");
        mvc.perform(get(base()).with(user(TEACHER))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Introduksjon"));
    }

    @Test
    void pagesPersistMarkdownAndCanBeEditedPublishedAndUnpublished() throws Exception {
        long module = module("Uke 1");
        mvc.perform(write(post(pages(module)), TEACHER, PAGE_BODY))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Innføring"))
                .andExpect(jsonPath("$.type").value("PAGE")).andExpect(jsonPath("$.published").value(false));
        long page = items.findAll().getFirst().getId();
        String published = PAGE_BODY.replace("false", "true").replace("Java", "JPA");
        mvc.perform(write(put(pages(module) + "/" + page), TEACHER, published)).andExpect(status().isOk());
        assertThat(items.findById(page).orElseThrow().getBody()).isEqualTo("## Velkommen\n\nLær **JPA**.");
        mvc.perform(get(pages(module) + "/" + page).with(user(STUDENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(true));
        mvc.perform(write(put(pages(module) + "/" + page), TEACHER, PAGE_BODY)).andExpect(status().isOk());
        mvc.perform(get(pages(module) + "/" + page).with(user(STUDENT))).andExpect(status().isNotFound());
    }

    @Test
    void studentsOnlySeeModulesWithVisiblePagesWhileTeachersSeeAllDrafts() throws Exception {
        module("Tom modul");
        long draft = module("Kun utkast");
        long draftPage = page(draft, "Hemmelig utkast", false, null, null);
        long visible = module("Publisert modul");
        page(visible, "Les dette", true, null, null);
        page(visible, "Ikke klar", false, null, null);
        mvc.perform(get(base()).with(user(STUDENT))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(visible))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].items[0].title").value("Les dette"));
        mvc.perform(get(base()).with(user(TEACHER))).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[1].items[0].body").value("Sideinnhold"));
        mvc.perform(get(pages(draft) + "/" + draftPage).with(user(TEACHER))).andExpect(status().isOk());
        mvc.perform(get(pages(draft) + "/" + draftPage).with(user(STUDENT))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "NULL,NULL,true", "2026-10-20T10:00:00Z,NULL,true", "2026-10-20T10:00:01Z,NULL,false",
        "NULL,2026-10-20T10:00:00Z,false", "NULL,2026-10-20T10:00:01Z,true",
        "2026-10-20T09:00:00Z,2026-10-20T11:00:00Z,true",
        "2026-10-19T09:00:00Z,2026-10-19T11:00:00Z,false"
    }, nullValues = "NULL")
    void visibilityWindowIsEnforcedInBothListAndDirectRead(String from, String until, boolean visible) throws Exception {
        long module = module("Tidsstyrt");
        long page = page(module, "Tidsstyrt side", true, instant(from), instant(until));
        mvc.perform(get(base()).with(user(STUDENT)))
                .andExpect(jsonPath("$", hasSize(visible ? 1 : 0)));
        mvc.perform(get(pages(module) + "/" + page).with(user(STUDENT)))
                .andExpect(visible ? status().isOk() : status().isNotFound());
        mvc.perform(get(pages(module) + "/" + page).with(user(TEACHER))).andExpect(status().isOk());
    }

    @Test
    void studentCannotUseAnyTeacherMutationEvenWithForgedRole() throws Exception {
        long module = module("Uke 1");
        long page = page(module, "Side", true, null, null);
        var requests = List.of(
                write(post(base()), STUDENT, "{\"title\":\"Forfalsket\",\"role\":\"TEACHER\"}"),
                write(put(base() + "/" + module), STUDENT, "{\"title\":\"Endret\"}"),
                delete(base() + "/" + module).with(user(STUDENT)).with(csrf()),
                write(put(base() + "/order"), STUDENT, order(module)),
                write(post(pages(module)), STUDENT, PAGE_BODY),
                write(put(pages(module) + "/" + page), STUDENT, PAGE_BODY),
                delete(pages(module) + "/" + page).with(user(STUDENT)).with(csrf()),
                write(put(pages(module) + "/order"), STUDENT, order(page)));
        for (var request : requests) mvc.perform(request).andExpect(status().isForbidden());
        assertThat(modules.count()).isEqualTo(1);
        assertThat(items.count()).isEqualTo(1);
        assertThat(items.findById(page).orElseThrow().isPublished()).isTrue();
    }

    @Test
    void outsiderAndAnonymousCannotReadOrCreateCourseContent() throws Exception {
        long module = module("Uke 1");
        long page = page(module, "Side", true, null, null);
        mvc.perform(get(base()).with(user(OUTSIDER))).andExpect(status().isNotFound());
        mvc.perform(get(pages(module) + "/" + page).with(user(OUTSIDER))).andExpect(status().isNotFound());
        mvc.perform(write(post(base()), OUTSIDER, "{\"title\":\"Innbrudd\"}")).andExpect(status().isNotFound());
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(post(base()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherWritesStillRequireCsrf() throws Exception {
        long module = module("Uke 1");
        mvc.perform(post(pages(module)).with(user(TEACHER)).contentType(MediaType.APPLICATION_JSON).content(PAGE_BODY))
                .andExpect(status().isForbidden());
        mvc.perform(delete(base() + "/" + module).with(user(TEACHER))).andExpect(status().isForbidden());
        assertThat(modules.count()).isEqualTo(1);
        assertThat(items.count()).isZero();
    }

    @Test
    void moduleAndPageIdsMustBelongToTheCourseAndModuleInTheUrl() throws Exception {
        long module = module("Første modul");
        long page = page(module, "Første side", true, null, null);
        long otherModule = module("Annen modul");
        long otherCourse = courseService.create(new CreateCourseRequest("Annet emne", "", "Høst 2026"), TEACHER).id();
        String wrongCourseModule = "/api/courses/" + otherCourse + "/modules/" + module;
        mvc.perform(write(put(wrongCourseModule), TEACHER, "{\"title\":\"Feil emne\"}")).andExpect(status().isNotFound());
        mvc.perform(delete(wrongCourseModule).with(user(TEACHER)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(write(post(wrongCourseModule + "/pages"), TEACHER, PAGE_BODY)).andExpect(status().isNotFound());
        mvc.perform(get(wrongCourseModule + "/pages/" + page).with(user(TEACHER))).andExpect(status().isNotFound());
        mvc.perform(get(pages(otherModule) + "/" + page).with(user(TEACHER))).andExpect(status().isNotFound());
        mvc.perform(write(put(pages(otherModule) + "/" + page), TEACHER, PAGE_BODY)).andExpect(status().isNotFound());
        mvc.perform(delete(pages(otherModule) + "/" + page).with(user(TEACHER)).with(csrf())).andExpect(status().isNotFound());
        assertThat(items.findById(page).orElseThrow().getTitle()).isEqualTo("Første side");
    }

    @Test
    void modulesAndPagesHavePersistentIndependentOrdering() throws Exception {
        long first = module("Første");
        long second = module("Andre");
        long a = page(first, "A", true, null, null);
        long b = page(first, "B", true, null, null);
        long c = page(second, "C", true, null, null);
        mvc.perform(write(put(base() + "/order"), TEACHER, order(second, first))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second));
        mvc.perform(write(put(pages(first) + "/order"), TEACHER, order(b, a))).andExpect(status().isOk());
        mvc.perform(get(base()).with(user(STUDENT))).andExpect(jsonPath("$[0].items[0].id").value(c))
                .andExpect(jsonPath("$[1].items[0].id").value(b)).andExpect(jsonPath("$[1].items[1].id").value(a));
        assertThat(modules.findByCourseIdOrderByPositionAscIdAsc(courseId)).extracting(CourseModule::getPosition).containsExactly(0, 1);
        assertThat(items.findByModuleIdOrderByPositionAscIdAsc(first)).extracting(ContentItem::getId).containsExactly(b, a);
    }

    @Test
    void incompleteDuplicateAndForeignOrderListsAreRejectedWithoutChangingData() throws Exception {
        long first = module("Første");
        long second = module("Andre");
        long a = page(first, "A", true, null, null);
        long b = page(first, "B", true, null, null);
        long foreign = page(second, "C", true, null, null);
        for (var ids : List.of(order(), order(first), order(first, first), order(first, 999999L))) {
            mvc.perform(write(put(base() + "/order"), TEACHER, ids)).andExpect(status().isBadRequest());
        }
        for (var ids : List.of(order(), order(a), order(a, a), order(a, foreign), "{\"ids\":[null]}", "{\"ids\":[-1]}", "{}")) {
            mvc.perform(write(put(pages(first) + "/order"), TEACHER, ids)).andExpect(status().isBadRequest());
        }
        assertThat(items.findByModuleIdOrderByPositionAscIdAsc(first)).extracting(ContentItem::getId).containsExactly(a, b);
        assertThat(modules.findByCourseIdOrderByPositionAscIdAsc(courseId)).extracting(CourseModule::getId).containsExactly(first, second);
    }

    @Test
    void deletionCascadesAndClosesOrderingGapsBeforeAppending() throws Exception {
        long first = module("Første");
        long second = module("Andre");
        long a = page(first, "A", true, null, null);
        long b = page(first, "B", true, null, null);
        page(second, "C", true, null, null);
        mvc.perform(delete(pages(first) + "/" + a).with(user(TEACHER)).with(csrf())).andExpect(status().isNoContent());
        long appended = page(first, "D", true, null, null);
        assertThat(items.findByModuleIdOrderByPositionAscIdAsc(first)).extracting(ContentItem::getId).containsExactly(b, appended);
        assertThat(items.findByModuleIdOrderByPositionAscIdAsc(first)).extracting(ContentItem::getPosition).containsExactly(0, 1);
        mvc.perform(delete(base() + "/" + first).with(user(TEACHER)).with(csrf())).andExpect(status().isNoContent());
        module("Tredje");
        assertThat(items.count()).isEqualTo(1);
        assertThat(modules.findByCourseIdOrderByPositionAscIdAsc(courseId)).extracting(CourseModule::getPosition).containsExactly(0, 1);
        courses.deleteById(courseId);
        assertThat(modules.count()).isZero();
        assertThat(items.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "{\"title\":\" \"}", "{\"title\":null}" })
    void invalidModuleTitlesReturnFieldErrors(String body) throws Exception {
        mvc.perform(write(post(base()), TEACHER, body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        assertThat(modules.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"title\":\" \",\"body\":\"Tekst\",\"published\":false}",
        "{\"title\":\"Side\",\"body\":\" \",\"published\":false}",
        "{\"title\":\"Side\",\"body\":\"Tekst\"}",
        "{\"title\":\"Side\",\"body\":\"Tekst\",\"published\":true,\"visibleFrom\":\"2026-10-20T10:00:00Z\",\"visibleUntil\":\"2026-10-20T10:00:00Z\"}",
        "{\"title\":\"Side\",\"body\":\"Tekst\",\"published\":false,\"visibleFrom\":\"2026-10-21T10:00:00Z\",\"visibleUntil\":\"2026-10-20T10:00:00Z\"}"
    })
    void invalidPagesAndTimeWindowsCannotBeSaved(String body) throws Exception {
        mvc.perform(write(post(pages(module("Modul"))), TEACHER, body)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());
        assertThat(items.count()).isZero();
    }

    @Test
    void fieldLengthLimitsAreValidatedBeforeDatabaseWrites() throws Exception {
        mvc.perform(write(post(base()), TEACHER, "{\"title\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        long module = module("Modul");
        mvc.perform(write(post(pages(module)), TEACHER, PAGE_BODY.replace("  Innføring  ", "x".repeat(256))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        mvc.perform(write(post(pages(module)), TEACHER, "{\"title\":\"Side\",\"body\":\"" + "x".repeat(100001) + "\",\"published\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.body").exists());
        assertThat(items.count()).isZero();
    }

    @Test
    void contentTypeAndPositionCannotBeChosenByTheClient() throws Exception {
        long module = module("Modul");
        mvc.perform(write(post(pages(module)), TEACHER, PAGE_BODY.replace("false}", "false,\"type\":\"QUIZ\",\"position\":99}")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("PAGE"))
                .andExpect(jsonPath("$.position").value(0));
    }

    @Test
    void simultaneousModuleCreationsGetDistinctPositions() throws Exception {
        concurrently(() -> module("Samtidig modul"));
        assertThat(modules.findByCourseIdOrderByPositionAscIdAsc(courseId)).extracting(CourseModule::getPosition).containsExactly(0, 1);
    }

    @Test
    void simultaneousPageCreationsGetDistinctPositions() throws Exception {
        long module = module("Modul");
        concurrently(() -> page(module, "Samtidig side", false, null, null));
        assertThat(items.findByModuleIdOrderByPositionAscIdAsc(module)).extracting(ContentItem::getPosition).containsExactly(0, 1);
    }

    private long module(String title) { return content.createModule(courseId, new ModuleRequest(title), TEACHER).id(); }
    private long page(long module, String title, boolean published, Instant from, Instant until) {
        return content.createPage(courseId, module, new PageRequest(title, "Sideinnhold", published, from, until), TEACHER).id();
    }
    private String base() { return "/api/courses/" + courseId + "/modules"; }
    private String pages(long module) { return base() + "/" + module + "/pages"; }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String email, String body) {
        return request.with(user(email)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private static String order(Long... ids) { return "{\"ids\":" + List.of(ids) + "}"; }
    private static void concurrently(Runnable action) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var jobs = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                action.run();
                return true;
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var job : jobs) assertThat(job.get(20, TimeUnit.SECONDS)).isTrue();
        }
    }
}
