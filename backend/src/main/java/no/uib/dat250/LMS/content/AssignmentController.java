package no.uib.dat250.LMS.content;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/assignments")
public class AssignmentController {
    private final AssignmentService assignments;
    public AssignmentController(AssignmentService assignments) { this.assignments = assignments; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse create(@PathVariable Long courseId, @PathVariable Long moduleId,
            @Valid @RequestBody AssignmentRequest request, Principal principal) {
        return assignments.create(courseId, moduleId, request, principal.getName());
    }
    @GetMapping("/{assignmentId}")
    public AssignmentResponse read(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long assignmentId, Principal principal) {
        return assignments.read(courseId, moduleId, assignmentId, principal.getName());
    }
    @PutMapping("/{assignmentId}")
    public AssignmentResponse update(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long assignmentId,
            @Valid @RequestBody AssignmentRequest request, Principal principal) {
        return assignments.update(courseId, moduleId, assignmentId, request, principal.getName());
    }
    @PostMapping(value = "/{assignmentId}/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse submit(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long assignmentId,
            @RequestParam UUID submissionId, @RequestParam MultipartFile file, Principal principal) {
        return assignments.submit(courseId, moduleId, assignmentId, submissionId, file, principal.getName());
    }
    @PutMapping("/{assignmentId}/submissions/{submissionId}/grade")
    public SubmissionResponse assess(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long assignmentId,
            @PathVariable Long submissionId, @Valid @RequestBody GradeRequest request, Principal principal) {
        return assignments.assess(courseId, moduleId, assignmentId, submissionId, request, principal.getName());
    }
    @GetMapping("/{assignmentId}/submissions/{submissionId}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long assignmentId,
            @PathVariable Long submissionId, Principal principal) {
        var file = assignments.download(courseId, moduleId, assignmentId, submissionId, principal.getName());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
                .contentLength(file.bytes().length).body(file.bytes());
    }
}
