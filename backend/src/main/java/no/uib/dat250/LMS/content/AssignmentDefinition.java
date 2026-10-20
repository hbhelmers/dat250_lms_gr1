package no.uib.dat250.LMS.content;

import java.time.Instant;
import jakarta.persistence.*;

@Entity @Table(name = "assignments")
public class AssignmentDefinition {
    @Id @Column(name = "content_item_id") private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId
    @JoinColumn(name = "content_item_id") private ContentItem content;
    private Instant deadline;
    @Column(nullable = false) private boolean acceptLate;
    protected AssignmentDefinition() {}
    public AssignmentDefinition(ContentItem content, AssignmentRequest request) { this.content = content; update(request); }
    public void update(AssignmentRequest request) {
        content.update(request.content()); deadline = request.deadline(); acceptLate = request.acceptLate();
    }
    public Long getId() { return id; }
    public ContentItem getContent() { return content; }
    public Instant getDeadline() { return deadline; }
    public boolean isAcceptLate() { return acceptLate; }
}
