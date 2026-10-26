package no.uib.dat250.LMS.course;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final CourseAccess access;

    public CourseService(CourseRepository repository, CourseEnrollmentRepository enrollments,
            LmsUserRepository users, CourseAccess access) {
        this.repository = repository;
        this.enrollments = enrollments;
        this.users = users;
        this.access = access;
    }

    public List<CourseResponse> findAll(String email) {
        return enrollments.findCoursesForUser(email).stream()
                .map(enrollment -> CourseResponse.from(enrollment.getCourse(), enrollment.getRole())).toList();
    }

    public CourseResponse findById(Long id, String email) {
        var membership = access.requireMember(id, email);
        return CourseResponse.from(membership.getCourse(), membership.getRole());
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request, String email) {
        var user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logg inn igjen."));
        if (user.getPreferredRole() != CourseRole.TEACHER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bare lærerkontoer kan opprette emner. Finn et emne og meld deg på som student.");
        }
        Course course = new Course(
                request.title().strip(), request.description().strip(), request.semester().strip());
        course.configureCatalog(request.institution() == null ? Institution.HVL : request.institution(),
                request.courseCode(), request.selfEnrollment() == null || request.selfEnrollment());
        repository.save(course);
        enrollments.save(new CourseEnrollment(course, user, CourseRole.TEACHER));
        return CourseResponse.from(course, CourseRole.TEACHER);
    }

    @Transactional
    public CourseResponse update(Long id, CreateCourseRequest request, String email) {
        var course = access.lockForTeacher(id, email);
        course.update(request.title().strip(), request.description().strip(), request.semester().strip());
        course.configureCatalog(request.institution() == null ? course.getInstitution() : request.institution(),
                request.courseCode() == null ? course.getCourseCode() : request.courseCode(),
                request.selfEnrollment() == null ? course.isSelfEnrollment() : request.selfEnrollment());
        if (course.isSelfEnrollment() && course.getInstitution() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Velg HVL eller UiB før emnet åpnes for påmelding.");
        }
        return CourseResponse.from(course, CourseRole.TEACHER);
    }

    public List<CourseMemberResponse> members(Long id, String email) {
        access.requireTeacher(id, email);
        return enrollments.findMembers(id).stream().map(CourseMemberResponse::from).toList();
    }

    public List<CatalogCourseResponse> catalog(Institution institution, String email) {
        users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logg inn igjen."));
        Map<Long, CourseRole> memberships = enrollments.findCoursesForUser(email).stream()
                .collect(Collectors.toMap(enrollment -> enrollment.getCourse().getId(), CourseEnrollment::getRole));
        return repository.findCatalog(institution).stream()
                .map(course -> CatalogCourseResponse.from(course, memberships.get(course.getId()))).toList();
    }

    @Transactional
    public CourseResponse enroll(Long id, String email) {
        var user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logg inn igjen."));
        var course = repository.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke et tilgjengelig emne."));
        var existing = enrollments.findMembership(id, email);
        if (existing.isPresent()) return CourseResponse.from(course, existing.get().getRole());
        if (!course.isSelfEnrollment() || course.getInstitution() == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Emnet er ikke åpent for påmelding.");
        enrollments.saveAndFlush(new CourseEnrollment(course, user, CourseRole.STUDENT));
        return CourseResponse.from(course, CourseRole.STUDENT);
    }

    @Transactional
    public CourseMemberResponse addStudent(Long id, AddStudentRequest request, String email) {
        var course = access.lockForTeacher(id, email);
        var student = users.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fant ingen konto med denne e-postadressen. Studenten må registrere seg først."));
        if (enrollments.existsByCourseIdAndUserId(id, student.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Brukeren er allerede medlem av emnet.");
        }
        var enrollment = enrollments.save(new CourseEnrollment(course, student, CourseRole.STUDENT));
        return CourseMemberResponse.from(enrollment);
    }

}
