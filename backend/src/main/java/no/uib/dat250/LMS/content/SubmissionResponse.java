package no.uib.dat250.LMS.content;
import java.time.Instant;

public record SubmissionResponse(Long id, int version, Instant submittedAt, boolean late, String filename,
        long fileSize, Long studentId, String studentName, String studentEmail, String groupName, AssignmentGrade grade,
        String feedback, boolean gradePublished, Instant gradedAt) {
    public static SubmissionResponse from(AssignmentSubmission submission, boolean teacher) { return from(submission, teacher, null); }
    public static SubmissionResponse from(AssignmentSubmission submission, boolean teacher, String groupName) {
        boolean visible = teacher || submission.isGradePublished();
        return new SubmissionResponse(submission.getId(), submission.getVersion(), submission.getSubmittedAt(),
                submission.isLate(), submission.getFilename(), submission.getFileSize(),
                teacher ? submission.getUser().getId() : null, teacher ? submission.getUser().getName() : null,
                teacher ? submission.getUser().getEmail() : null, groupName, visible ? submission.getGrade() : null,
                visible ? submission.getFeedback() : null, submission.isGradePublished(), visible ? submission.getGradedAt() : null);
    }
}
