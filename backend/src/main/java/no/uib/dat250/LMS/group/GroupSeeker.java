package no.uib.dat250.LMS.group;

import java.time.Instant;

import jakarta.persistence.*;
import no.uib.dat250.LMS.course.Course;
import no.uib.dat250.LMS.user.LmsUser;

/** A student who has ticked "Ser etter gruppe" in a course, with a short note to the others. */
@Entity
@Table(name = "group_seekers")
public class GroupSeeker {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private LmsUser user;

    @Column(nullable = false, length = 280)
    private String note;

    @Column(nullable = false)
    private Instant createdAt;

    protected GroupSeeker() {}

    GroupSeeker(Course course, LmsUser user, String note, Instant createdAt) {
        this.course = course;
        this.user = user;
        this.note = note;
        this.createdAt = createdAt;
    }

    void setNote(String note) { this.note = note; }
    public LmsUser getUser() { return user; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
