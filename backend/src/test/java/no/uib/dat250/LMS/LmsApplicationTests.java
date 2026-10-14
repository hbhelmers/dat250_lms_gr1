package no.uib.dat250.LMS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import no.uib.dat250.LMS.course.CourseRepository;
import no.uib.dat250.LMS.user.LmsUserRepository;
import no.uib.dat250.LMS.user.RegistrationRequest;
import no.uib.dat250.LMS.user.RegistrationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "POSTGRES_PASSWORD=test-password"
})
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class LmsApplicationTests {

    private static final String VALID_COURSE = """
            {"title":"  Databaser  ","description":"  Lær om ORM.  ","semester":"  Høst 2026  "}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CourseRepository repository;

    @Autowired
    private LmsUserRepository users;

    @Autowired
    private RegistrationService registration;

    @BeforeEach
    void clearTestDatabase() {
        repository.deleteAll();
        users.deleteAll();
    }

    @Test
    void contextLoads() {
    }

    @Test
    void anonymousClientsCannotReadCourses() throws Exception {
        mvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousClientsCannotCreateCourses() throws Exception {
        mvc.perform(post("/api/courses").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_COURSE))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    @WithMockUser
    void coursesStartEmpty() throws Exception {
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void createdCourseIsCommittedAndCanBeReadAgain() throws Exception {
        var result = mvc.perform(post("/api/courses").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_COURSE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/courses/\\d+")))
                .andExpect(jsonPath("$.title").value("Databaser"))
                .andExpect(jsonPath("$.description").value("Lær om ORM."))
                .andExpect(jsonPath("$.semester").value("Høst 2026"))
                .andReturn();

        assertThat(repository.count()).isEqualTo(1);
        mvc.perform(get(result.getResponse().getHeader("Location")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Databaser"));
        mvc.perform(get("/api/courses"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Databaser"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"title\":\" \",\"description\":\"Beskrivelse\",\"semester\":\"Høst 2026\"}",
            "{\"title\":\"Databaser\",\"description\":\" \",\"semester\":\"Høst 2026\"}",
            "{\"title\":\"Databaser\",\"description\":\"Beskrivelse\",\"semester\":\" \"}"
    })
    @WithMockUser
    void requiredFieldsAreValidated(String body) throws Exception {
        mvc.perform(post("/api/courses").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
        assertThat(repository.count()).isZero();
    }

    @Test
    @WithMockUser
    void overlongTitleIsRejectedBeforeReachingTheDatabase() throws Exception {
        String body = "{\"title\":\"" + "a".repeat(256)
                + "\",\"description\":\"Beskrivelse\",\"semester\":\"Høst 2026\"}";
        mvc.perform(post("/api/courses").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        assertThat(repository.count()).isZero();
    }

    @Test
    @WithMockUser
    void creatingCoursesRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(VALID_COURSE))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    @WithMockUser
    void unknownCourseReturnsNotFound() throws Exception {
        mvc.perform(get("/api/courses/999999"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Fant ikke emnet."));
    }

    @Test
    void browserCanLogInAndUseItsSession() throws Exception {
        var user = registration.register(new RegistrationRequest(
                "Testlærer", "teacher@example.test", "test-login-password"));
        var result = mvc.perform(formLogin("/api/login")
                        .user("teacher@example.test").password("test-login-password"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);

        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.id()))
                .andExpect(jsonPath("$.name").value("Testlærer"))
                .andExpect(jsonPath("$.email").value("teacher@example.test"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void incorrectPasswordIsRejected() throws Exception {
        registration.register(new RegistrationRequest("Testlærer", "teacher@example.test", "test-login-password"));
        mvc.perform(formLogin("/api/login").user("teacher@example.test").password("incorrect"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/login").param("username", "teacher@example.test")
                        .param("password", "test-login-password"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfTokenCanBeRequestedBeforeLogin() throws Exception {
        mvc.perform(get("/api/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
