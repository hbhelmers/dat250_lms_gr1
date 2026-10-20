package no.uib.dat250.LMS.course;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import no.uib.dat250.LMS.user.LmsUser;
import no.uib.dat250.LMS.user.LmsUserRepository;
import no.uib.dat250.LMS.user.RegistrationRequest;

@Service
@Transactional
public class LegacyCourseOwnership implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LegacyCourseOwnership.class);
    private final CourseRepository courses;
    private final CourseEnrollmentRepository enrollments;
    private final LmsUserRepository users;
    private final String teacherEmail;

    public LegacyCourseOwnership(CourseRepository courses, CourseEnrollmentRepository enrollments,
            LmsUserRepository users, @Value("${LMS_LEGACY_TEACHER_EMAIL:}") String teacherEmail) {
        this.courses = courses;
        this.enrollments = enrollments;
        this.users = users;
        this.teacherEmail = RegistrationRequest.normalizeEmail(teacherEmail);
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (teacherEmail.isBlank()) { return; }
        var teacher = users.findByEmail(teacherEmail);
        if (teacher.isPresent()) {
            assignTo(teacher.get());
        } else {
            log.info("Legacy courses await registration of the configured teacher account.");
        }
    }

    public void assignTo(LmsUser user) {
        if (teacherEmail.isBlank() || !teacherEmail.equals(user.getEmail())) { return; }
        for (var course : courses.findLegacyCoursesForUpdate()) {
            enrollments.save(new CourseEnrollment(course, user, CourseRole.TEACHER));
            course.markLegacyOwnershipAssigned();
        }
    }
}
