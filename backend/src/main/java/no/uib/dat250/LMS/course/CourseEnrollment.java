package no.uib.dat250.LMS.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import no.uib.dat250.LMS.user.LmsUser;

@Entity
@Table(name = "course_enrollments")
public class CourseEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private LmsUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourseRole role;

    protected CourseEnrollment() {
    }

    public CourseEnrollment(Course course, LmsUser user, CourseRole role) {
        this.course = course;
        this.user = user;
        this.role = role;
    }

    public Course getCourse() { return course; }
    public LmsUser getUser() { return user; }
    public CourseRole getRole() { return role; }
}
