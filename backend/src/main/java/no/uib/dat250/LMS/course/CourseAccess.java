package no.uib.dat250.LMS.course;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CourseAccess {
    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;

    public CourseAccess(CourseRepository courses, CourseEnrollmentRepository enrollments) {
        this.courses = courses;
        this.enrollments = enrollments;
    }

    public CourseEnrollment requireMember(Long courseId, String email) {
        return enrollments.findMembership(courseId, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke emnet."));
    }

    public void requireTeacher(Long courseId, String email) {
        if (requireMember(courseId, email).getRole() != CourseRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Du må være lærer i emnet for å gjøre dette.");
        }
    }

    // Call inside a write transaction; all ordering and membership writes share this lock.
    public Course lockForTeacher(Long courseId, String email) {
        var membership = lockForMember(courseId, email);
        if (membership.getRole() != CourseRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Du må være lærer i emnet for å gjøre dette.");
        }
        return membership.getCourse();
    }

    public CourseEnrollment lockForMember(Long courseId, String email) {
        courses.findForUpdate(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke emnet."));
        return requireMember(courseId, email);
    }
}
