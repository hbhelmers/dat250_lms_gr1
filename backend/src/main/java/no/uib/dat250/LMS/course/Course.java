package no.uib.dat250.LMS.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 50)
    private String semester;

    @Column(name = "legacy_owner_pending", nullable = false)
    private boolean legacyOwnerPending;

    @Column(length = 32)
    private String exampleTemplate;

    @Enumerated(EnumType.STRING) @Column(length = 8)
    private Institution institution;
    @Column(length = 20) private String courseCode;
    @Column(nullable = false) private boolean selfEnrollment;
    @Column(length = 512) private String officialUrl;
    @Column(length = 32, unique = true) private String catalogKey;

    protected Course() {
    }

    public Course(String title, String description, String semester) {
        this.title = title;
        this.description = description;
        this.semester = semester;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSemester() { return semester; }
    public String getExampleTemplate() { return exampleTemplate; }
    public Institution getInstitution() { return institution; }
    public String getCourseCode() { return courseCode; }
    public boolean isSelfEnrollment() { return selfEnrollment; }
    public String getOfficialUrl() { return officialUrl; }
    public String getCatalogKey() { return catalogKey; }

    public void configureCatalog(Institution institution, String courseCode, boolean selfEnrollment) {
        if (this.institution != institution || !java.util.Objects.equals(this.courseCode, courseCode)) officialUrl = null;
        this.institution = institution; this.courseCode = courseCode; this.selfEnrollment = selfEnrollment;
    }
    public void markCatalogExample(String key, String officialUrl) {
        this.catalogKey = key; this.officialUrl = officialUrl;
    }

    public static Course example(String title, String description, String semester, String template) {
        var course = new Course(title, description, semester);
        course.exampleTemplate = template;
        return course;
    }

    public void update(String title, String description, String semester) {
        this.title = title;
        this.description = description;
        this.semester = semester;
    }

    void markLegacyOwnershipAssigned() { legacyOwnerPending = false; }
}
