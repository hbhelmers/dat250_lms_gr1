package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;
import no.uib.dat250.LMS.user.LmsUser;

@Entity @Table(name = "assignment_submissions")
public class AssignmentSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assignment_id") private AssignmentDefinition assignment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private LmsUser user;
    @Column(nullable = false) private int version;
    @Column(nullable = false) private UUID submissionId;
    @Column(nullable = false) private Instant submittedAt;
    @Column(nullable = false) private boolean late;
    @Column(nullable = false, length = 100) private String objectKey;
    @Column(nullable = false, length = 255) private String filename;
    @Column(nullable = false, length = 100) private String mediaType;
    @Column(nullable = false) private long fileSize;
    @Column(nullable = false, length = 64) private String sha256;
    @Enumerated(EnumType.STRING) @Column(length = 16) private AssignmentGrade grade;
    @Column(nullable = false, columnDefinition = "TEXT") private String feedback = "";
    @Column(nullable = false) private boolean gradePublished;
    private Instant gradedAt;

    protected AssignmentSubmission() {}
    public AssignmentSubmission(AssignmentDefinition assignment, LmsUser user, int version, UUID submissionId,
            Instant submittedAt, boolean late, String objectKey, SubmissionFile file) {
        this.assignment = assignment; this.user = user; this.version = version; this.submissionId = submissionId;
        this.submittedAt = submittedAt; this.late = late; this.objectKey = objectKey;
        filename = file.filename(); mediaType = file.mediaType(); fileSize = file.bytes().length; sha256 = file.sha256();
    }
    public void assess(GradeRequest request, Instant now) {
        grade = request.grade(); feedback = request.feedback(); gradePublished = request.published(); gradedAt = now;
    }
    public Long getId() { return id; }
    public LmsUser getUser() { return user; }
    public int getVersion() { return version; }
    public Instant getSubmittedAt() { return submittedAt; }
    public boolean isLate() { return late; }
    public String getObjectKey() { return objectKey; }
    public String getFilename() { return filename; }
    public String getMediaType() { return mediaType; }
    public long getFileSize() { return fileSize; }
    public String getSha256() { return sha256; }
    public AssignmentGrade getGrade() { return grade; }
    public String getFeedback() { return feedback; }
    public boolean isGradePublished() { return gradePublished; }
    public Instant getGradedAt() { return gradedAt; }
}
