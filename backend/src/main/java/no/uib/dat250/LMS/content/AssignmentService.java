package no.uib.dat250.LMS.content;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import no.uib.dat250.LMS.course.CourseAccess;
import no.uib.dat250.LMS.course.CourseEnrollment;
import no.uib.dat250.LMS.course.CourseRole;
import no.uib.dat250.LMS.storage.FileStorage;

@Service @Transactional(readOnly = true)
public class AssignmentService {
    private final CourseAccess access;
    private final CourseModuleRepository modules;
    private final ContentItemRepository items;
    private final AssignmentRepository assignments;
    private final AssignmentSubmissionRepository submissions;
    private final FileStorage storage;
    private final Clock clock;
    public AssignmentService(CourseAccess access, CourseModuleRepository modules, ContentItemRepository items,
            AssignmentRepository assignments, AssignmentSubmissionRepository submissions, FileStorage storage, Clock clock) {
        this.access = access; this.modules = modules; this.items = items; this.assignments = assignments;
        this.submissions = submissions; this.storage = storage; this.clock = clock;
    }

    @Transactional
    public AssignmentResponse create(Long courseId, Long moduleId, AssignmentRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var module = modules.findByIdAndCourseId(moduleId, courseId).orElseThrow(() -> missing());
        var item = items.save(new ContentItem(module, ContentType.ASSIGNMENT, request.content(),
                items.findByModuleIdOrderByPositionAscIdAsc(moduleId).size()));
        var assignment = assignments.saveAndFlush(new AssignmentDefinition(item, request));
        return response(assignment, access.requireMember(courseId, email));
    }

    public AssignmentResponse read(Long courseId, Long moduleId, Long assignmentId, String email) {
        var member = access.requireMember(courseId, email);
        var assignment = requireAssignment(courseId, moduleId, assignmentId);
        requireVisible(assignment, member);
        return response(assignment, member);
    }

    @Transactional
    public AssignmentResponse update(Long courseId, Long moduleId, Long assignmentId, AssignmentRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var assignment = requireAssignment(courseId, moduleId, assignmentId);
        assignment.update(request);
        assignments.saveAndFlush(assignment);
        return response(assignment, access.requireMember(courseId, email));
    }

    @Transactional
    public SubmissionResponse submit(Long courseId, Long moduleId, Long assignmentId, UUID submissionId,
            MultipartFile upload, String email) {
        var member = access.lockForMember(courseId, email);
        if (member.getRole() != CourseRole.STUDENT) throw error(HttpStatus.FORBIDDEN, "Bare studenter kan levere oppgaver.");
        var assignment = requireAssignment(courseId, moduleId, assignmentId);
        requireVisible(assignment, member);
        var file = SubmissionFile.read(upload);
        var existing = submissions.findByAssignmentIdAndUserIdAndSubmissionId(assignmentId, member.getUser().getId(), submissionId);
        if (existing.isPresent()) {
            var previous = existing.get();
            if (!previous.getSha256().equals(file.sha256()) || !previous.getFilename().equals(file.filename()))
                throw error(HttpStatus.CONFLICT, "Denne innleveringen er allerede lagret med en annen fil.");
            return SubmissionResponse.from(previous, false);
        }
        var now = clock.instant();
        boolean late = assignment.getDeadline() != null && !now.isBefore(assignment.getDeadline());
        if (late && !assignment.isAcceptLate()) throw error(HttpStatus.CONFLICT, "Innleveringsfristen er passert.");
        var key = "submissions/" + UUID.randomUUID();
        // Remove uploaded bytes if metadata cannot commit. Existing versions remain untouched.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try { storage.delete(key); }
                    catch (RuntimeException failure) { LoggerFactory.getLogger(AssignmentService.class).warn("Could not remove a rolled back upload."); }
                }
            }
        });
        storage.put(key, file.bytes(), file.mediaType());
        var submission = submissions.saveAndFlush(new AssignmentSubmission(assignment, member.getUser(),
                submissions.countByAssignmentIdAndUserId(assignmentId, member.getUser().getId()) + 1,
                submissionId, now, late, key, file));
        return SubmissionResponse.from(submission, false);
    }

    @Transactional
    public SubmissionResponse assess(Long courseId, Long moduleId, Long assignmentId, Long submissionId,
            GradeRequest request, String email) {
        access.lockForTeacher(courseId, email);
        requireAssignment(courseId, moduleId, assignmentId);
        var submission = requireSubmission(assignmentId, submissionId);
        submission.assess(request, clock.instant());
        return SubmissionResponse.from(submission, true);
    }

    public Download download(Long courseId, Long moduleId, Long assignmentId, Long submissionId, String email) {
        var member = access.requireMember(courseId, email);
        var assignment = requireAssignment(courseId, moduleId, assignmentId);
        requireVisible(assignment, member);
        var submission = requireSubmission(assignmentId, submissionId);
        if (member.getRole() != CourseRole.TEACHER && !submission.getUser().getId().equals(member.getUser().getId())) throw missing();
        return new Download(submission.getFilename(), storage.get(submission.getObjectKey()));
    }

    private AssignmentResponse response(AssignmentDefinition assignment, CourseEnrollment member) {
        boolean teacher = member.getRole() == CourseRole.TEACHER;
        var history = teacher ? submissions.findByAssignmentIdOrderBySubmittedAtDescIdDesc(assignment.getId())
                : submissions.findByAssignmentIdAndUserIdOrderByVersionDesc(assignment.getId(), member.getUser().getId());
        return new AssignmentResponse(ContentResponse.from(assignment.getContent()), assignment.getDeadline(), assignment.isAcceptLate(),
                history.stream().map(submission -> SubmissionResponse.from(submission, teacher)).toList());
    }
    private AssignmentDefinition requireAssignment(Long courseId, Long moduleId, Long assignmentId) {
        modules.findByIdAndCourseId(moduleId, courseId).orElseThrow(() -> missing());
        items.findByIdAndModuleId(assignmentId, moduleId).filter(item -> item.getType() == ContentType.ASSIGNMENT).orElseThrow(() -> missing());
        return assignments.findById(assignmentId).orElseThrow(() -> missing());
    }
    private AssignmentSubmission requireSubmission(Long assignmentId, Long submissionId) {
        return submissions.findByIdAndAssignmentId(submissionId, assignmentId).orElseThrow(() -> missing());
    }
    private void requireVisible(AssignmentDefinition assignment, CourseEnrollment member) {
        if (member.getRole() != CourseRole.TEACHER && !assignment.getContent().visibleAt(clock.instant())) throw missing();
    }
    private static ResponseStatusException missing() { return error(HttpStatus.NOT_FOUND, "Fant ikke oppgaven eller innleveringen."); }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
    public record Download(String filename, byte[] bytes) {}
}
