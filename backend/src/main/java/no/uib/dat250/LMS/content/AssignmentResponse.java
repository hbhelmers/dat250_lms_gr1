package no.uib.dat250.LMS.content;
import java.time.Instant;
import java.util.List;
public record AssignmentResponse(ContentResponse content, Instant deadline, boolean acceptLate,
        List<SubmissionResponse> submissions) {}
