package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.List;

public record QuizResponse(ContentResponse content, int maxAttempts, Instant deadline, boolean resultsVisible,
        boolean questionsLocked, List<Question> questions, List<QuizAttemptResponse> attempts) {
    public record Question(Long id, String prompt, List<String> options, Integer correctOption, String hint) {}
}
