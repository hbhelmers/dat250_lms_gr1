package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;

@Entity
@Table(name = "quizzes")
public class QuizDefinition {
    @Id @Column(name = "content_item_id") private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "content_item_id") private ContentItem content;
    @Column(nullable = false) private int maxAttempts;
    private Instant deadline;
    @Column(nullable = false) private boolean resultsVisible;
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") private List<QuizQuestion> questions = new ArrayList<>();

    protected QuizDefinition() {}
    public QuizDefinition(ContentItem content, QuizRequest request) { this.content = content; update(request, true); }
    public void update(QuizRequest request, boolean replaceQuestions) {
        content.update(request.content());
        maxAttempts = request.maxAttempts(); deadline = request.deadline(); resultsVisible = request.resultsVisible();
        if (replaceQuestions) {
            questions.clear();
            for (int index = 0; index < request.questions().size(); index++) {
                questions.add(new QuizQuestion(this, request.questions().get(index), index));
            }
        }
    }
    public boolean sameQuestions(QuizRequest request) {
        if (questions.size() != request.questions().size()) return false;
        for (int index = 0; index < questions.size(); index++) {
            if (!questions.get(index).matches(request.questions().get(index))) return false;
        }
        return true;
    }
    public Long getId() { return id; }
    public ContentItem getContent() { return content; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant getDeadline() { return deadline; }
    public boolean isResultsVisible() { return resultsVisible; }
    public List<QuizQuestion> getQuestions() { return questions; }
}
