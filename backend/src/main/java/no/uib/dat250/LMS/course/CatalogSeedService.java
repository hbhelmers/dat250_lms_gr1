package no.uib.dat250.LMS.course;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import no.uib.dat250.LMS.content.*;
import no.uib.dat250.LMS.user.LmsUser;

@Service
public class CatalogSeedService {
    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final CourseModuleRepository modules;
    private final ContentItemRepository items;
    private final JdbcTemplate jdbc;
    public CatalogSeedService(CourseRepository courses, CourseEnrollmentRepository enrollments,
            CourseModuleRepository modules, ContentItemRepository items, JdbcTemplate jdbc) {
        this.courses = courses; this.enrollments = enrollments; this.modules = modules; this.items = items; this.jdbc = jdbc;
    }

    @Transactional
    public void seed(LmsUser teacher) {
        if (teacher.getPreferredRole() != CourseRole.TEACHER) throw new IllegalArgumentException("Catalog examples require a teacher account.");
        jdbc.execute("SELECT pg_advisory_xact_lock(2502026)");
        var existingExamples = enrollments.findCoursesForUser(teacher.getEmail()).stream()
                .filter(enrollment -> enrollment.getRole() == CourseRole.TEACHER).map(CourseEnrollment::getCourse)
                .filter(course -> course.getExampleTemplate() != null).toList();
        for (var example : EXAMPLES) {
            String key = example.institution() + ":" + example.code();
            if (courses.findByCatalogKey(key).isPresent()) continue;
            // Reuse the shared DAT100/DAT250 learning rooms without replacing existing content or enrollments.
            var course = example.institution() == Institution.HVL ? existingExamples.stream()
                    .filter(candidate -> example.code().equals(candidate.getExampleTemplate()) && candidate.getCatalogKey() == null)
                    .findFirst().orElse(null) : null;
            if (course == null) {
                course = courses.save(new Course(example.code() + " – " + example.title(), example.description(), "2026/2027"));
                enrollments.save(new CourseEnrollment(course, teacher, CourseRole.TEACHER));
                var module = modules.save(new CourseModule(course, "Kom i gang", 0));
                items.save(new ContentItem(module, new PageRequest("Velkommen til " + example.code(),
                        "## " + example.title() + "\n\n" + example.description()
                        + "\n\nDette er et eksempelemne i Lær. Undervisningsinnhold og oppgaver her er demoinnhold."
                        + "\n\nLæreren kan legge til sider, quizer og innleveringsoppgaver i dette emnerommet.", true, null, null), 0));
            }
            if ("DAT250 – Programvareteknologi".equals(course.getTitle()))
                course.update(example.code() + " – " + example.title(), course.getDescription(), course.getSemester());
            course.configureCatalog(example.institution(), example.code(), true);
            course.markCatalogExample(key, example.source());
            courses.save(course);
        }
    }

    private static final String HVL_DATA = "https://www.hvl.no/studier/studieprogram/dataingenior/2026h/utdanningsplan/";
    private static final String HVL_ENGINEERING = "https://www.hvl.no/studier/studieprogram/elektroingenior-haugesund/2026h/utdanningsplan/";
    private static final String UIB_COMPUTING = "https://www4.uib.no/program/informatikk-datateknologi-bachelor";
    private record Example(Institution institution, String code, String title, String description, String source) {}
    private static final List<Example> EXAMPLES = List.of(
            new Example(Institution.HVL, "DAT100", "Grunnleggende programmering", "Eksempelemne om variabler, valg, løkker og grunnleggende programmering.", HVL_DATA),
            new Example(Institution.HVL, "DAT102", "Algoritmer og datastrukturer", "Eksempelemne om datastrukturer, søking, sortering og algoritmer.", HVL_DATA),
            new Example(Institution.HVL, "DAT107", "Databaser", "Eksempelemne om datamodellering, SQL og relasjonsdatabaser.", "https://www.hvl.no/studier/studieprogram/emne/2026/dat107"),
            new Example(Institution.HVL, "DAT108", "Programmering og webapplikasjoner", "Eksempelemne om programmering og utvikling av webapplikasjoner.", HVL_DATA),
            new Example(Institution.HVL, "DAT250", "Avanserte programvareteknologier", "Eksempelemne om programvarearkitektur, databaser og distribuerte applikasjoner.", "https://www.hvl.no/studier/studieprogram/emne/2025/dat250"),
            new Example(Institution.HVL, "ING100", "Ingeniørfagleg innføringsemne", "Eksempelemne med en introduksjon til ingeniørfaget og praktisk problemløsing.", HVL_DATA),
            new Example(Institution.HVL, "ING202", "Programmering for ingeniørar", "Eksempelemne om programmering brukt til å løse ingeniørfaglige problemer.", HVL_ENGINEERING),
            new Example(Institution.HVL, "ING161", "Kjemi for ingeniørar", "Eksempelemne om kjemiske prinsipper i ingeniørfaget.", HVL_ENGINEERING),
            new Example(Institution.HVL, "ING162", "Mekanikk for ingeniørar", "Eksempelemne om krefter, bevegelse og mekanikk i ingeniørfaget.", HVL_ENGINEERING),
            new Example(Institution.UIB, "INF100", "Programmering 1", "Eksempelemne med en praktisk innføring i programmering.", UIB_COMPUTING),
            new Example(Institution.UIB, "INF101", "Programmering 2", "Eksempelemne om objektorientering, programdesign og testing.", UIB_COMPUTING),
            new Example(Institution.UIB, "INF102", "Algoritmar, datastrukturar og programmering", "Eksempelemne om effektive algoritmer og datastrukturer.", UIB_COMPUTING),
            new Example(Institution.UIB, "INF112", "Systemkonstruksjon", "Eksempelemne om utvikling av større programvaresystemer i team.", UIB_COMPUTING),
            new Example(Institution.UIB, "INF226", "Programvaresikkerheit", "Eksempelemne om sikker utvikling og sårbarheter i programvare.", UIB_COMPUTING));
}
