package no.uib.dat250.LMS.course;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import no.uib.dat250.LMS.user.LmsUserRepository;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository repository;
    private final CourseEnrollmentRepository enrollments;
    private final LmsUserRepository users;

    public CourseService(CourseRepository repository, CourseEnrollmentRepository enrollments, LmsUserRepository users) {
        this.repository = repository;
        this.enrollments = enrollments;
        this.users = users;
    }

    public List<CourseResponse> findAll(String email) {
        return enrollments.findCoursesForUser(email).stream()
                .map(enrollment -> CourseResponse.from(enrollment.getCourse(), enrollment.getRole())).toList();
    }

    public CourseResponse findById(Long id, String email) {
        var membership = requireMembership(id, email);
        return CourseResponse.from(membership.getCourse(), membership.getRole());
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request, String email) {
        var user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logg inn igjen."));
        Course course = new Course(
                request.title().strip(), request.description().strip(), request.semester().strip());
        repository.save(course);
        enrollments.save(new CourseEnrollment(course, user, CourseRole.TEACHER));
        return CourseResponse.from(course, CourseRole.TEACHER);
    }

    @Transactional
    public CourseResponse update(Long id, CreateCourseRequest request, String email) {
        var course = lockCourse(id);
        requireTeacher(id, email);
        course.update(request.title().strip(), request.description().strip(), request.semester().strip());
        return CourseResponse.from(course, CourseRole.TEACHER);
    }

    public List<CourseMemberResponse> members(Long id, String email) {
        requireTeacher(id, email);
        return enrollments.findMembers(id).stream().map(CourseMemberResponse::from).toList();
    }

    @Transactional
    public CourseMemberResponse addStudent(Long id, AddStudentRequest request, String email) {
        var course = lockCourse(id);
        requireTeacher(id, email);
        var student = users.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fant ingen konto med denne e-postadressen. Studenten må registrere seg først."));
        if (enrollments.existsByCourseIdAndUserId(id, student.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Brukeren er allerede medlem av emnet.");
        }
        var enrollment = enrollments.save(new CourseEnrollment(course, student, CourseRole.STUDENT));
        return CourseMemberResponse.from(enrollment);
    }

    private Course lockCourse(Long id) {
        return repository.findForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke emnet."));
    }

    private CourseEnrollment requireMembership(Long id, String email) {
        return enrollments.findMembership(id, email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke emnet."));
    }

    private void requireTeacher(Long id, String email) {
        if (requireMembership(id, email).getRole() != CourseRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Du må være lærer i emnet for å gjøre dette.");
        }
    }
}
