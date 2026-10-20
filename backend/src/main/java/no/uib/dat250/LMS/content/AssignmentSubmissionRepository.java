package no.uib.dat250.LMS.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, Long> {
    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtDescIdDesc(Long assignmentId);
    List<AssignmentSubmission> findByAssignmentIdAndUserIdOrderByVersionDesc(Long assignmentId, Long userId);
    Optional<AssignmentSubmission> findByAssignmentIdAndUserIdAndSubmissionId(Long assignmentId, Long userId, UUID submissionId);
    Optional<AssignmentSubmission> findByIdAndAssignmentId(Long id, Long assignmentId);
    int countByAssignmentIdAndUserId(Long assignmentId, Long userId);
}
