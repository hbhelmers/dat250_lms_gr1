package no.uib.dat250.LMS.group;

import java.time.Instant;

import jakarta.persistence.*;
import no.uib.dat250.LMS.course.Course;
import no.uib.dat250.LMS.user.LmsUser;

@Entity
@Table(name = "course_group_members")
public class GroupMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private CourseGroup group;

    // Stored next to the group so the database can enforce one group per student per course.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private LmsUser user;

    @Column(nullable = false)
    private Instant joinedAt;

    protected GroupMembership() {}

    GroupMembership(CourseGroup group, LmsUser user, Instant joinedAt) {
        this.group = group;
        this.course = group.getCourse();
        this.user = user;
        this.joinedAt = joinedAt;
    }

    public CourseGroup getGroup() { return group; }
    public LmsUser getUser() { return user; }
}
