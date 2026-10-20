package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.*;
import no.uib.dat250.LMS.user.LmsUser;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quiz_id") private QuizDefinition quiz;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private LmsUser user;
    @Column(nullable = false) private int attemptNumber;
    @Column(nullable = false) private UUID submissionId;
    @Column(nullable = false) private Instant submittedAt;
    @Column(nullable = false) private int score;
    @Column(nullable = false) private int total;
    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL) @OrderBy("id ASC")
    private List<QuizAnswer> answers = new ArrayList<>();

    protected QuizAttempt() {}
    public QuizAttempt(QuizDefinition quiz, LmsUser user, int number, UUID submissionId, Instant at, Map<Long, Integer> selected) {
        this.quiz = quiz; this.user = user; attemptNumber = number; this.submissionId = submissionId; submittedAt = at;
        total = quiz.getQuestions().size();
        for (var question : quiz.getQuestions()) {
            var answer = new QuizAnswer(this, question, selected.get(question.getId()));
            answers.add(answer);
            if (answer.isCorrect()) score++;
        }
    }
    public Long getId() { return id; }
    public LmsUser getUser() { return user; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getScore() { return score; }
    public int getTotal() { return total; }
    public List<QuizAnswer> getAnswers() { return answers; }
}
