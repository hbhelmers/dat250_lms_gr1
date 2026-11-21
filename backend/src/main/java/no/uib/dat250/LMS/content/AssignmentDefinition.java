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
    @Column(nullable = false) private boolean groupWork;
    @Column(length = 500) private String starterRepoUrl;
    protected AssignmentDefinition() {}
    public AssignmentDefinition(ContentItem content, AssignmentRequest request) { this.content = content; update(request); }
    public void update(AssignmentRequest request) {
        content.update(request.content()); deadline = request.deadline(); acceptLate = request.acceptLate();
        groupWork = Boolean.TRUE.equals(request.groupWork());
        starterRepoUrl = request.starterRepoUrl() == null || request.starterRepoUrl().isBlank() ? null : request.starterRepoUrl().strip();
    }
    public Long getId() { return id; }
    public ContentItem getContent() { return content; }
    public Instant getDeadline() { return deadline; }
    public boolean isAcceptLate() { return acceptLate; }
    public boolean isGroupWork() { return groupWork; }
    public String getStarterRepoUrl() { return starterRepoUrl; }
}
