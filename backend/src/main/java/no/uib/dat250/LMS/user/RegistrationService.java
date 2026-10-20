package no.uib.dat250.LMS.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import no.uib.dat250.LMS.course.LegacyCourseOwnership;

@Service
public class RegistrationService {
    private final LmsUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final LegacyCourseOwnership legacyCourses;
    private final ExampleSetupService examples;

    public RegistrationService(LmsUserRepository users, PasswordEncoder passwordEncoder, LegacyCourseOwnership legacyCourses, ExampleSetupService examples) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.legacyCourses = legacyCourses;
        this.examples = examples;
    }

    @Transactional
    public UserResponse register(RegistrationRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }
        var user = new LmsUser(request.name(), request.email(), passwordEncoder.encode(request.password()), request.preferredRole());
        try {
            users.saveAndFlush(user);
            legacyCourses.assignTo(user);
            if (request.includeExamples()) examples.addExamples(user);
            return UserResponse.from(user);
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint also protects against simultaneous registrations.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "users_email_unique".equals(violation.getConstraintName())) {
                    throw new EmailAlreadyRegisteredException();
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(String email) {
        return users.findByEmail(email).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logg inn igjen."));
    }
}
