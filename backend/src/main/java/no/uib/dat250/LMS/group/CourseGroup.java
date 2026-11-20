package no.uib.dat250.LMS.group;

import java.time.Instant;

import jakarta.persistence.*;
import no.uib.dat250.LMS.course.Course;

@Entity
@Table(name = "course_groups")
public class CourseGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private boolean open;

    @Column(nullable = false)
    private Instant createdAt;

    protected CourseGroup() {}

    CourseGroup(Course course, GroupDtos.GroupRequest request, Instant createdAt) {
        this.course = course;
        this.createdAt = createdAt;
        update(request);
    }

    void update(GroupDtos.GroupRequest request) {
        name = request.name().strip();
        capacity = request.capacity();
        open = request.open();
    }

    public Long getId() { return id; }
    public Course getCourse() { return course; }
    public String getName() { return name; }
    public int getCapacity() { return capacity; }
    public boolean isOpen() { return open; }
}
