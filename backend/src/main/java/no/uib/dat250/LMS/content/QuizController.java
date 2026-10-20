package no.uib.dat250.LMS.content;

import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/modules/{moduleId}/quizzes")
public class QuizController {
    private final QuizService quizzes;
    public QuizController(QuizService quizzes) { this.quizzes = quizzes; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public QuizResponse create(@PathVariable Long courseId, @PathVariable Long moduleId,
            @Valid @RequestBody QuizRequest request, Principal principal) {
        return quizzes.create(courseId, moduleId, request, principal.getName());
    }
    @GetMapping("/{quizId}")
    public QuizResponse read(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long quizId, Principal principal) {
        return quizzes.read(courseId, moduleId, quizId, principal.getName());
    }
    @PutMapping("/{quizId}")
    public QuizResponse update(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long quizId,
            @Valid @RequestBody QuizRequest request, Principal principal) {
        return quizzes.update(courseId, moduleId, quizId, request, principal.getName());
    }
    @PostMapping("/{quizId}/attempts") @ResponseStatus(HttpStatus.CREATED)
    public QuizAttemptResponse submit(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long quizId,
            @Valid @RequestBody QuizSubmission request, Principal principal) {
        return quizzes.submit(courseId, moduleId, quizId, request, principal.getName());
    }
    @GetMapping("/{quizId}/attempts")
    public List<QuizAttemptResponse> results(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long quizId,
            Principal principal) {
        return quizzes.results(courseId, moduleId, quizId, principal.getName());
    }
}
