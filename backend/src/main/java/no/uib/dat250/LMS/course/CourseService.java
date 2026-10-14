package no.uib.dat250.LMS.course;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository repository;

    public CourseService(CourseRepository repository) {
        this.repository = repository;
    }

    public List<CourseResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream().map(CourseResponse::from).toList();
    }

    public CourseResponse findById(Long id) {
        return repository.findById(id).map(CourseResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fant ikke emnet."));
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest request) {
        Course course = new Course(
                request.title().strip(), request.description().strip(), request.semester().strip());
        return CourseResponse.from(repository.save(course));
    }
}
