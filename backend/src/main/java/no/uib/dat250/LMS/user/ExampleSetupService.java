package no.uib.dat250.LMS.user;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.course.*;

@Service
@Transactional
public class ExampleSetupService {
    public static final String TEACHER_EMAIL = "laerer@lms.test";
    public static final String STUDENT_EMAIL = "student@lms.test";
    public static final String DEMO_PASSWORD = "demo123!";
    private final LmsUserRepository users;
    private final PasswordEncoder encoder;
    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final CourseModuleRepository modules;
    private final ContentItemRepository items;
    private final QuizRepository quizzes;
    private final AssignmentRepository assignments;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean enabled;

    public ExampleSetupService(LmsUserRepository users, PasswordEncoder encoder, CourseRepository courses,
            CourseEnrollmentRepository enrollments, CourseModuleRepository modules, ContentItemRepository items,
            QuizRepository quizzes, AssignmentRepository assignments, JdbcTemplate jdbc, Clock clock,
            @Value("${lms.demo.accounts.enabled:false}") boolean enabled) {
        this.users = users; this.encoder = encoder; this.courses = courses; this.enrollments = enrollments;
        this.modules = modules; this.items = items; this.quizzes = quizzes; this.assignments = assignments; this.jdbc = jdbc; this.clock = clock; this.enabled = enabled;
    }

    public void initializeDemoAccounts() {
        if (!enabled) return;
        lockSetup();
        var teacher = ensureTeacher();
        if (!users.existsByEmail(STUDENT_EMAIL)) {
            var student = users.saveAndFlush(new LmsUser("Eksempelstudent", STUDENT_EMAIL,
                    encoder.encode(DEMO_PASSWORD), CourseRole.STUDENT));
            enrollInExamples(student, teacher);
        }
    }

    public void addExamples(LmsUser user) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Eksempelinnhold er ikke aktivert på denne serveren.");
        lockSetup();
        if (user.getPreferredRole() == CourseRole.TEACHER) createTeacherExamples(user);
        else enrollInExamples(user, ensureTeacher());
    }

    public DemoAccess access() {
        return enabled ? new DemoAccess(true, TEACHER_EMAIL, STUDENT_EMAIL, DEMO_PASSWORD)
                : new DemoAccess(false, null, null, null);
    }

    public record DemoAccess(boolean enabled, String teacherEmail, String studentEmail, String password) {}

    private void lockSetup() {
        // One shared teacher is created even when several students register at once.
        jdbc.execute("SELECT pg_advisory_xact_lock(2502026)");
    }

    private LmsUser ensureTeacher() {
        var existing = users.findByEmail(TEACHER_EMAIL);
        if (existing.isPresent()) return existing.get();
        var teacher = users.saveAndFlush(new LmsUser("Eksempellærer", TEACHER_EMAIL,
                encoder.encode(DEMO_PASSWORD), CourseRole.TEACHER));
        createTeacherExamples(teacher);
        return teacher;
    }

    private void enrollInExamples(LmsUser student, LmsUser teacher) {
        for (var course : teacherExamples(teacher)) {
            if (!enrollments.existsByCourseIdAndUserId(course.getId(), student.getId())) {
                enrollments.save(new CourseEnrollment(course, student, CourseRole.STUDENT));
            }
        }
    }

    private List<Course> teacherExamples(LmsUser teacher) {
        return enrollments.findCoursesForUser(teacher.getEmail()).stream().filter(e -> e.getRole() == CourseRole.TEACHER)
                .map(CourseEnrollment::getCourse).filter(course -> course.getExampleTemplate() != null).toList();
    }

    private void createTeacherExamples(LmsUser teacher) {
        var existing = teacherExamples(teacher).stream().map(Course::getExampleTemplate).toList();
        var today = LocalDate.now(clock);
        String semester = (today.getMonthValue() <= 6 ? "Vår " : "Høst ") + today.getYear();
        if (!existing.contains("DAT250")) {
            var course = exampleCourse(teacher, "DAT250 – Programvareteknologi", "Eksempelfag: bygg et læringssystem med Java, React og PostgreSQL.", semester, "DAT250");
            var first = modules.save(new CourseModule(course, "Uke 1 – Kom i gang", 0));
            page(first, "Velkommen til DAT250", "## Velkommen til læringsrommet\n\nDette er et **eksempelfag** dere kan bruke til å prøve systemet.\n\n- Les undervisningssidene.\n- Ta en quiz og få tilbakemelding.\n- Utforsk hvordan moduler og emner fungerer.\n\nLæreren kan redigere og legge til eget innhold.", 0, true);
            page(first, "Slik fungerer prosjektet", "## Tre deler som jobber sammen\n\n**React** viser brukergrensesnittet. **Spring Boot** håndterer forespørsler og tilgang. **PostgreSQL** lagrer brukere og innhold.\n\n```text\nFrontend → Backend → Database\n```\n\nRollene student og lærer bestemmes for hvert enkelt emne.", 1, true);
            quiz(first, "Sjekk kunnskapen – LMS", 2, List.of(
                    new QuizQuestionRequest("Hva lagrer dataene i prosjektet?", List.of("PostgreSQL", "React", "Vite"), 0, "Databasen kjører som en egen Docker-container."),
                    new QuizQuestionRequest("Hvem kan publisere innhold i et emne?", List.of("Alle innloggede brukere", "Læreren i emnet"), 1, "Tilgangen bestemmes av rollen i emnet.")));
            var second = modules.save(new CourseModule(course, "Uke 2 – Databaser", 1));
            page(second, "Entiteter og relasjoner", "## Fra Java til databasen\n\nEn entitet representerer noe vi vil lagre, for eksempel en bruker eller et emne.\n\nEn innmelding kobler **brukeren**, **emnet** og **rollen** sammen. Det gjør at samme person kan ha ulike roller i ulike emner.", 0, true);
            page(second, "Arbeidsnotater til læreren", "## Utkast\n\nDette er en side som bare læreren ser. Rediger den og publiser når innholdet er klart.", 1, false);
            assignment(second, "Innlevering – modeller et læringssystem", "## Din første innlevering\n\nLag en enkel datamodell for brukere, emner og innmeldinger. Forklar relasjonene med egne ord.\n\nLever svaret som **PDF eller DOCX**. Læreren vurderer innleveringen og publiserer en tilbakemelding.", 2);
        }
        if (!existing.contains("DAT100")) {
            var course = exampleCourse(teacher, "DAT100 – Grunnleggende programmering", "Eksempelfag: bli kjent med variabler, datatyper, valg og løkker i Java.", semester, "DAT100");
            var first = modules.save(new CourseModule(course, "Uke 1 – Variabler og datatyper", 0));
            page(first, "De første variablene", "## Gi verdiene et navn\n\nVariabler lar oss lagre og bruke verdier.\n\n```java\nint antall = 3;\nString navn = \"Ada\";\nboolean ferdig = false;\n```\n\n`int` brukes til heltall, `String` til tekst og `boolean` til sannhetsverdier.", 0, true);
            quiz(first, "Prøv deg på Java", 1, List.of(
                    new QuizQuestionRequest("Hvilken datatype passer til et heltall?", List.of("int", "boolean", "String"), 0, "Tenk på variabelen antall fra undervisningssiden."),
                    new QuizQuestionRequest("Hvilke verdier kan en boolean ha?", List.of("Alle heltall", "true eller false"), 1, "En boolean representerer om noe er sant eller usant.")));
            var second = modules.save(new CourseModule(course, "Uke 2 – Valg og løkker", 1));
            page(second, "Gjenta med en løkke", "## La programmet gjenta arbeidet\n\nEn for-løkke kjører kode flere ganger.\n\n```java\nfor (int i = 0; i < 3; i++) {\n    System.out.println(i);\n}\n```\n\nEksempelet skriver ut 0, 1 og 2.", 0, true);
            assignment(second, "Innlevering – variabler og løkker", "## Forklar en løkke\n\nSkriv et lite Java-program med en variabel og en løkke. Forklar hva programmet skriver ut.\n\nLever kode og forklaring i én **PDF eller DOCX**.", 1);
        }
    }

    private Course exampleCourse(LmsUser teacher, String title, String description, String semester, String template) {
        var course = courses.save(Course.example(title, description, semester, template));
        enrollments.saveAndFlush(new CourseEnrollment(course, teacher, CourseRole.TEACHER));
        return course;
    }
    private void page(CourseModule module, String title, String body, int position, boolean published) {
        items.save(new ContentItem(module, new PageRequest(title, body, published, null, null), position));
    }
    private void quiz(CourseModule module, String title, int position, List<QuizQuestionRequest> questions) {
        var request = new QuizRequest(title, "Test det du har lært. Du har tre forsøk og får tilbakemelding etter innlevering.",
                true, null, null, 3, null, true, questions);
        var item = items.save(new ContentItem(module, ContentType.QUIZ, request.content(), position));
        quizzes.save(new QuizDefinition(item, request));
    }
    private void assignment(CourseModule module, String title, String body, int position) {
        var request = new AssignmentRequest(title, body, true, null, null, null, false);
        var item = items.save(new ContentItem(module, ContentType.ASSIGNMENT, request.content(), position));
        assignments.save(new AssignmentDefinition(item, request));
    }
}
