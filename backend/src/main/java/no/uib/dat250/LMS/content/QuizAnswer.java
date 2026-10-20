package no.uib.dat250.LMS.content;

import jakarta.persistence.*;

@Entity
@Table(name = "quiz_answers")
public class QuizAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "attempt_id") private QuizAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "question_id") private QuizQuestion question;
    @Column(nullable = false) private int selectedOption;
    @Column(nullable = false) private boolean correct;

    protected QuizAnswer() {}
    public QuizAnswer(QuizAttempt attempt, QuizQuestion question, int selectedOption) {
        this.attempt = attempt; this.question = question; this.selectedOption = selectedOption;
        correct = selectedOption == question.getCorrectOption();
    }
    public QuizQuestion getQuestion() { return question; }
    public int getSelectedOption() { return selectedOption; }
    public boolean isCorrect() { return correct; }
}
