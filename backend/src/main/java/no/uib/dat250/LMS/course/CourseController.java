package no.uib.dat250.LMS.course;

import java.net.URI;
import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService service;

    public CourseController(CourseService service) {
        this.service = service;
    }

    @GetMapping
    public List<CourseResponse> findAll(Principal principal) {
        return service.findAll(principal.getName());
    }

    @GetMapping("/{id}")
    public CourseResponse findById(@PathVariable Long id, Principal principal) {
        return service.findById(id, principal.getName());
    }

    @GetMapping("/catalog")
    public List<CatalogCourseResponse> catalog(@RequestParam Institution institution, Principal principal) {
        return service.catalog(institution, principal.getName());
    }

    @PostMapping("/{id}/enroll")
    public CourseResponse enroll(@PathVariable Long id, Principal principal) {
        return service.enroll(id, principal.getName());
    }

    @PostMapping
    public ResponseEntity<CourseResponse> create(@Valid @RequestBody CreateCourseRequest request, Principal principal) {
        CourseResponse course = service.create(request, principal.getName());
        return ResponseEntity.created(URI.create("/api/courses/" + course.id())).body(course);
    }

    @PutMapping("/{id}")
    public CourseResponse update(@PathVariable Long id, @Valid @RequestBody CreateCourseRequest request, Principal principal) {
        return service.update(id, request, principal.getName());
    }

    @GetMapping("/{id}/members")
    public List<CourseMemberResponse> members(@PathVariable Long id, Principal principal) {
        return service.members(id, principal.getName());
    }

    @PostMapping("/{id}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public CourseMemberResponse addStudent(@PathVariable Long id, @Valid @RequestBody AddStudentRequest request,
            Principal principal) {
        return service.addStudent(id, request, principal.getName());
    }
}
