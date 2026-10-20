package no.uib.dat250.LMS.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {
    List<CourseModule> findByCourseIdOrderByPositionAscIdAsc(Long courseId);
    Optional<CourseModule> findByIdAndCourseId(Long id, Long courseId);
}
