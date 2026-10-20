package no.uib.dat250.LMS.content;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quiz_id") private QuizDefinition quiz;
    @Column(nullable = false) private int position;
    @Column(nullable = false, length = 2000) private String prompt;
    @Column(nullable = false, length = 2000) private String hint;
    @Column(nullable = false) private int correctOption;
    @ElementCollection @CollectionTable(name = "quiz_options", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "position") @Column(name = "label", nullable = false, length = 1000)
    private List<String> options = new ArrayList<>();

    protected QuizQuestion() {}
    public QuizQuestion(QuizDefinition quiz, QuizQuestionRequest request, int position) {
        this.quiz = quiz; this.position = position;
        prompt = request.prompt().strip(); hint = request.hint() == null ? "" : request.hint().strip();
        correctOption = request.correctOption(); options.addAll(request.options().stream().map(String::strip).toList());
    }
    public boolean matches(QuizQuestionRequest request) {
        return prompt.equals(request.prompt().strip()) && hint.equals(request.hint() == null ? "" : request.hint().strip())
                && correctOption == request.correctOption() && options.equals(request.options().stream().map(String::strip).toList());
    }
    public Long getId() { return id; }
    public String getPrompt() { return prompt; }
    public String getHint() { return hint; }
    public int getCorrectOption() { return correctOption; }
    public List<String> getOptions() { return options; }
}
