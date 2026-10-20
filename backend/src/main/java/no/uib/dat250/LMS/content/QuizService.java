package no.uib.dat250.LMS.content;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import no.uib.dat250.LMS.course.CourseAccess;
import no.uib.dat250.LMS.course.CourseEnrollment;
import no.uib.dat250.LMS.course.CourseRole;

@Service
@Transactional(readOnly = true)
public class QuizService {
    private final CourseAccess access;
    private final CourseModuleRepository modules;
    private final ContentItemRepository items;
    private final QuizRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final Clock clock;

    public QuizService(CourseAccess access, CourseModuleRepository modules, ContentItemRepository items,
            QuizRepository quizzes, QuizAttemptRepository attempts, Clock clock) {
        this.access = access; this.modules = modules; this.items = items;
        this.quizzes = quizzes; this.attempts = attempts; this.clock = clock;
    }

    @Transactional
    public QuizResponse create(Long courseId, Long moduleId, QuizRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var module = requireModule(courseId, moduleId);
        int position = items.findByModuleIdOrderByPositionAscIdAsc(moduleId).size();
        var item = items.save(new ContentItem(module, ContentType.QUIZ, request.content(), position));
        var quiz = quizzes.saveAndFlush(new QuizDefinition(item, request));
        return response(quiz, access.requireMember(courseId, email));
    }

    public QuizResponse read(Long courseId, Long moduleId, Long quizId, String email) {
        var member = access.requireMember(courseId, email);
        var quiz = requireQuiz(courseId, moduleId, quizId);
        requireVisible(quiz, member);
        return response(quiz, member);
    }

    @Transactional
    public QuizResponse update(Long courseId, Long moduleId, Long quizId, QuizRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var quiz = requireQuiz(courseId, moduleId, quizId);
        boolean locked = attempts.existsByQuizId(quizId);
        if (locked && !quiz.sameQuestions(request)) throw error(HttpStatus.CONFLICT,
                "Spørsmål og fasit er låst fordi quizen har innleverte forsøk. Lag en ny quiz for å endre dem.");
        quiz.update(request, !locked);
        quizzes.saveAndFlush(quiz);
        return response(quiz, access.requireMember(courseId, email));
    }

    @Transactional
    public QuizAttemptResponse submit(Long courseId, Long moduleId, Long quizId, QuizSubmission request, String email) {
        // Serializes submissions and teacher edits, including concurrent final attempts.
        var member = access.lockForMember(courseId, email);
        if (member.getRole() != CourseRole.STUDENT) throw error(HttpStatus.FORBIDDEN, "Bare studenter kan levere quizforsøk.");
        var quiz = requireQuiz(courseId, moduleId, quizId);
        requireVisible(quiz, member);
        var selected = validateAnswers(quiz, request);
        var existing = attempts.findByQuizIdAndUserIdAndSubmissionId(quizId, member.getUser().getId(), request.submissionId());
        if (existing.isPresent()) {
            var previous = existing.get().getAnswers().stream().collect(Collectors.toMap(
                    answer -> answer.getQuestion().getId(), QuizAnswer::getSelectedOption));
            if (!previous.equals(selected)) throw error(HttpStatus.CONFLICT, "Dette forsøket er allerede levert med andre svar.");
            return QuizAttemptResponse.from(existing.get(), quiz.isResultsVisible(), false);
        }
        var now = clock.instant();
        if (quiz.getDeadline() != null && !now.isBefore(quiz.getDeadline())) {
            throw error(HttpStatus.CONFLICT, "Innleveringsfristen er passert.");
        }
        int count = attempts.countByQuizIdAndUserId(quizId, member.getUser().getId());
        if (count >= quiz.getMaxAttempts()) throw error(HttpStatus.CONFLICT, "Du har brukt opp forsøkene dine.");
        var attempt = attempts.saveAndFlush(new QuizAttempt(quiz, member.getUser(), count + 1, request.submissionId(), now, selected));
        return QuizAttemptResponse.from(attempt, quiz.isResultsVisible(), false);
    }

    public List<QuizAttemptResponse> results(Long courseId, Long moduleId, Long quizId, String email) {
        access.requireTeacher(courseId, email);
        requireQuiz(courseId, moduleId, quizId);
        return attempts.findByQuizIdOrderBySubmittedAtDescIdDesc(quizId).stream()
                .map(attempt -> QuizAttemptResponse.from(attempt, true, true)).toList();
    }

    private QuizResponse response(QuizDefinition quiz, CourseEnrollment member) {
        boolean teacher = member.getRole() == CourseRole.TEACHER;
        var questions = quiz.getQuestions().stream().map(question -> new QuizResponse.Question(question.getId(),
                question.getPrompt(), List.copyOf(question.getOptions()), teacher ? question.getCorrectOption() : null,
                teacher ? question.getHint() : null)).toList();
        var history = teacher ? List.<QuizAttemptResponse>of() : attempts.findByQuizIdAndUserIdOrderByAttemptNumberDesc(
                quiz.getId(), member.getUser().getId()).stream()
                .map(attempt -> QuizAttemptResponse.from(attempt, quiz.isResultsVisible(), false)).toList();
        return new QuizResponse(ContentResponse.from(quiz.getContent()), quiz.getMaxAttempts(), quiz.getDeadline(),
                quiz.isResultsVisible(), teacher && attempts.existsByQuizId(quiz.getId()), questions, history);
    }

    private Map<Long, Integer> validateAnswers(QuizDefinition quiz, QuizSubmission request) {
        var questions = quiz.getQuestions().stream().collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        Map<Long, Integer> selected = new HashMap<>();
        for (var answer : request.answers()) {
            var question = questions.get(answer.questionId());
            if (question == null || answer.option() < 0 || answer.option() >= question.getOptions().size()
                    || selected.putIfAbsent(answer.questionId(), answer.option()) != null) {
                throw error(HttpStatus.BAD_REQUEST, "Velg ett gyldig svar per spørsmål i denne quizen.");
            }
        }
        if (selected.size() != questions.size()) throw error(HttpStatus.BAD_REQUEST, "Svar på alle spørsmål før du leverer.");
        return selected;
    }

    private CourseModule requireModule(Long courseId, Long moduleId) {
        return modules.findByIdAndCourseId(moduleId, courseId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke modulen."));
    }
    private QuizDefinition requireQuiz(Long courseId, Long moduleId, Long quizId) {
        requireModule(courseId, moduleId);
        items.findByIdAndModuleId(quizId, moduleId).filter(item -> item.getType() == ContentType.QUIZ)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke quizen."));
        return quizzes.findById(quizId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke quizen."));
    }
    private void requireVisible(QuizDefinition quiz, CourseEnrollment member) {
        if (member.getRole() != CourseRole.TEACHER && !quiz.getContent().visibleAt(clock.instant())) {
            throw error(HttpStatus.NOT_FOUND, "Fant ikke quizen.");
        }
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
