package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.List;

public record QuizAttemptResponse(Long id, int number, Instant submittedAt, Integer score, int total,
        String studentName, String studentEmail, List<Feedback> answers) {
    public record Feedback(Long questionId, int selectedOption, boolean correct, int correctOption, String hint) {}
    static QuizAttemptResponse from(QuizAttempt attempt, boolean showResults, boolean teacher) {
        return new QuizAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getSubmittedAt(),
                showResults ? attempt.getScore() : null, attempt.getTotal(),
                teacher ? attempt.getUser().getName() : null, teacher ? attempt.getUser().getEmail() : null,
                showResults ? attempt.getAnswers().stream().map(answer -> new Feedback(answer.getQuestion().getId(),
                        answer.getSelectedOption(), answer.isCorrect(), answer.getQuestion().getCorrectOption(),
                        answer.isCorrect() ? "" : answer.getQuestion().getHint())).toList() : List.of());
    }
}
