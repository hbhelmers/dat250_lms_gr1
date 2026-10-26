package no.uib.dat250.LMS.course;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCatalogKey(String catalogKey);

    @Query("select c from Course c where c.institution = :institution and c.selfEnrollment = true order by c.courseCode, c.title, c.id")
    List<Course> findCatalog(@Param("institution") Institution institution);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.legacyOwnerPending = true order by c.id")
    List<Course> findLegacyCoursesForUpdate();
}
