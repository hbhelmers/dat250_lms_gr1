package no.uib.dat250.LMS.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    boolean existsByQuizId(Long quizId);
    int countByQuizIdAndUserId(Long quizId, Long userId);
    Optional<QuizAttempt> findByQuizIdAndUserIdAndSubmissionId(Long quizId, Long userId, UUID submissionId);
    List<QuizAttempt> findByQuizIdAndUserIdOrderByAttemptNumberDesc(Long quizId, Long userId);
    List<QuizAttempt> findByQuizIdOrderBySubmittedAtDescIdDesc(Long quizId);
}
