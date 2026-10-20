package no.uib.dat250.LMS.content;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentItemRepository extends JpaRepository<ContentItem, Long> {
    List<ContentItem> findByModuleIdOrderByPositionAscIdAsc(Long moduleId);
    Optional<ContentItem> findByIdAndModuleId(Long id, Long moduleId);

    @Query("select i from ContentItem i join fetch i.module where i.module.course.id = :courseId order by i.module.position, i.position, i.id")
    List<ContentItem> findForCourse(@Param("courseId") Long courseId);
}
