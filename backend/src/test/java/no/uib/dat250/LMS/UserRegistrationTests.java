package no.uib.dat250.LMS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import no.uib.dat250.LMS.course.CourseRepository;
import no.uib.dat250.LMS.user.LmsUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = { "spring.config.import=", "POSTGRES_PASSWORD=test-password", "lms.demo.accounts.enabled=false" })
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class UserRegistrationTests {
    private static final String PASSWORD = "et-langt-testpassord-2026";
    private static final String VALID_USER = """
            {"name":"  Ada Student  ","email":"  Ada@Example.Test  ","password":"et-langt-testpassord-2026"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LmsUserRepository users;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void clearTestDatabase() {
        courses.deleteAll();
        users.deleteAll();
    }

    @Test
    void registrationPersistsNormalizedUserWithHashAndDoesNotLogIn() throws Exception {
        mvc.perform(post("/api/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Ada Student"))
                .andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(users.count()).isEqualTo(1);
        var user = users.findByEmail("ada@example.test").orElseThrow();
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("{pbkdf2-sha256-600k}");
        assertThat(encoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
        mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsRejectedRegardlessOfCaseOrSurroundingSpaces() throws Exception {
        registerUser(VALID_USER);
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_USER.replace("Ada@Example.Test", "ADA@EXAMPLE.TEST")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        assertThat(users.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    void invalidRegistrationDoesNotCreateUser(String body, String field) throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors." + field).exists());
        assertThat(users.count()).isZero();
    }

    static Stream<Arguments> invalidRegistrations() {
        return Stream.of(
                Arguments.of(VALID_USER.replace("  Ada Student  ", "   "), "name"),
                Arguments.of(VALID_USER.replace("  Ada Student  ", "a".repeat(121)), "name"),
                Arguments.of(VALID_USER.replace("  Ada@Example.Test  ", "not-an-email"), "email"),
                Arguments.of(VALID_USER.replace("  Ada@Example.Test  ", "  "), "email"),
                Arguments.of(VALID_USER.replace("  Ada@Example.Test  ", "a".repeat(245) + "@example.test"), "email"),
                Arguments.of(VALID_USER.replace(PASSWORD, "short"), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, "abcdef!"), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, "abcdefgh"), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, "abcdefg "), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, "aaaaaa🌱"), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, " ".repeat(20)), "password"),
                Arguments.of(VALID_USER.replace(PASSWORD, "a".repeat(129)), "password"),
                Arguments.of("{}", "email"),
                Arguments.of("{\"name\":null,\"email\":null,\"password\":null}", "password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefg!", "1234567?", "!!!!!!!!", "abcdefg🌱"})
    void eightCharactersAndASpecialCharacterAreEnough(String password) throws Exception {
        registerUser(VALID_USER.replace(PASSWORD, password));
        login("ada@example.test", password);
    }

    @Test
    void longPasswordsHaveNoAdditionalLengthRestriction() throws Exception {
        String password = "a".repeat(256) + "!";
        registerUser(VALID_USER.replace(PASSWORD, password));
        login("ada@example.test", password);
    }

    @Test
    void exampleEnrollmentIsRejectedAndRolledBackWhenExamplesAreDisabled() throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(VALID_USER.replace("\"name\":", "\"includeExamples\":true,\"name\":")))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isZero();
        mvc.perform(get("/api/demo-access")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.password").isEmpty());
    }

    @Test
    void registrationRequiresCsrfProtection() throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(VALID_USER))
                .andExpect(status().isForbidden());
        assertThat(users.count()).isZero();
    }

    @Test
    void registeredUserCanLogInWithNormalizedEmailAndReadCourses() throws Exception {
        registerUser(VALID_USER);
        var result = mvc.perform(formLogin("/api/login").user("  ADA@EXAMPLE.TEST  ").password(PASSWORD))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/session").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("ada@example.test"))
                .andExpect(jsonPath("$.name").value("Ada Student"));
        mvc.perform(get("/api/courses").session(session)).andExpect(status().isOk());
    }

    @Test
    void differentAccountsHaveIndependentSessions() throws Exception {
        registerUser(VALID_USER);
        registerUser(VALID_USER.replace("Ada Student", "Bo Student").replace("Ada@Example.Test", "bo@example.test"));
        var ada = login("ada@example.test", PASSWORD);
        var bo = login("bo@example.test", PASSWORD);
        mvc.perform(post("/api/logout").session(ada).with(csrf())).andExpect(status().isNoContent());
        assertThat(ada.isInvalid()).isTrue();
        mvc.perform(get("/api/session").session(bo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bo Student"));
    }

    @Test
    void longUnicodePasswordIsNotTruncatedAndSpacesAreSignificant() throws Exception {
        String password = "  læring-🌱-".repeat(8);
        registerUser(VALID_USER.replace(PASSWORD, password));
        mvc.perform(formLogin("/api/login").user("ada@example.test").password(password.strip()))
                .andExpect(status().isUnauthorized());
        mvc.perform(formLogin("/api/login").user("ada@example.test").password(password))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownEmailCannotLogIn() throws Exception {
        mvc.perform(formLogin("/api/login").user("unknown@example.test").password(PASSWORD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRegistrationsOnlyCreateOneAccount() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerConcurrently(start));
            var second = executor.submit(() -> registerConcurrently(start));
            start.countDown();
            assertThat(new int[] { first.get(), second.get() }).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(users.count()).isEqualTo(1);
    }

    private int registerConcurrently(CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_USER)).andReturn().getResponse().getStatus();
    }

    private void registerUser(String body) throws Exception {
        mvc.perform(post("/api/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(formLogin("/api/login").user(email).password(password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
