package no.uib.dat250.LMS.group;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CourseGroupRepository extends JpaRepository<CourseGroup, Long> {
    List<CourseGroup> findByCourseIdOrderByNameAscIdAsc(Long courseId);
    Optional<CourseGroup> findByIdAndCourseId(Long id, Long courseId);
    boolean existsByCourseIdAndNameIgnoreCase(Long courseId, String name);
    boolean existsByCourseIdAndNameIgnoreCaseAndIdNot(Long courseId, String name, Long id);
}

interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {
    @Query("select m from GroupMembership m join fetch m.user where m.course.id = :courseId order by m.joinedAt, m.id")
    List<GroupMembership> findForCourse(@Param("courseId") Long courseId);

    Optional<GroupMembership> findByCourseIdAndUserId(Long courseId, Long userId);
    boolean existsByCourseIdAndUserId(Long courseId, Long userId);
    long countByGroupId(Long groupId);
}

interface GroupSeekerRepository extends JpaRepository<GroupSeeker, Long> {
    @Query("select s from GroupSeeker s join fetch s.user where s.course.id = :courseId order by s.createdAt, s.id")
    List<GroupSeeker> findForCourse(@Param("courseId") Long courseId);

    Optional<GroupSeeker> findByCourseIdAndUserId(Long courseId, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupSeeker s where s.course.id = :courseId and s.user.id = :userId")
    void deleteForStudent(@Param("courseId") Long courseId, @Param("userId") Long userId);
}

interface GroupInvitationRepository extends JpaRepository<GroupInvitation, Long> {
    @Query("select i from GroupInvitation i join fetch i.group g join fetch i.inviter where g.course.id = :courseId and i.invitee.id = :userId order by i.createdAt, i.id")
    List<GroupInvitation> findReceived(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("select i.invitee.id from GroupInvitation i where i.group.id = :groupId")
    List<Long> findInviteeIds(@Param("groupId") Long groupId);

    @Query("select i from GroupInvitation i join fetch i.group g where i.id = :id and g.course.id = :courseId and i.invitee.id = :userId")
    Optional<GroupInvitation> findReceived(@Param("id") Long id, @Param("courseId") Long courseId, @Param("userId") Long userId);

    boolean existsByGroupIdAndInviteeId(Long groupId, Long inviteeId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupInvitation i where i.invitee.id = :userId and i.group.id in (select g.id from CourseGroup g where g.course.id = :courseId)")
    void deleteForInvitee(@Param("courseId") Long courseId, @Param("userId") Long userId);
}
