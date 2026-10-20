package no.uib.dat250.LMS.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public void update(String title, String description, String semester) {
        this.title = title;
        this.description = description;
        this.semester = semester;
    }

    void markLegacyOwnershipAssigned() { legacyOwnerPending = false; }
}
