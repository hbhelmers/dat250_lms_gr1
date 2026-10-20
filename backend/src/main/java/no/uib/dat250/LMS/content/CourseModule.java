package no.uib.dat250.LMS.content;

import jakarta.persistence.*;
import no.uib.dat250.LMS.course.Course;

@Entity
@Table(name = "course_modules")
public class CourseModule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private int position;

    protected CourseModule() {}

    public CourseModule(Course course, String title, int position) {
        this.course = course;
        this.title = title;
        this.position = position;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public int getPosition() { return position; }
    public void rename(String title) { this.title = title; }
    public void setPosition(int position) { this.position = position; }
}
