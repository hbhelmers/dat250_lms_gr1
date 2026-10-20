package no.uib.dat250.LMS.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {
    @Query("select e from CourseEnrollment e join fetch e.course where e.user.email = :email order by e.course.id desc")
    List<CourseEnrollment> findCoursesForUser(@Param("email") String email);

    @Query("select e from CourseEnrollment e join fetch e.course where e.course.id = :courseId and e.user.email = :email")
    Optional<CourseEnrollment> findMembership(@Param("courseId") Long courseId, @Param("email") String email);

    @Query("select e from CourseEnrollment e join fetch e.user where e.course.id = :courseId order by e.role, e.user.name, e.id")
    List<CourseEnrollment> findMembers(@Param("courseId") Long courseId);

    boolean existsByCourseIdAndUserId(Long courseId, Long userId);
}
