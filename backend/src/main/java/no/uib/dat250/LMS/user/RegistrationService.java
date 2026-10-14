package no.uib.dat250.LMS.user;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final LmsUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(LmsUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegistrationRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new EmailAlreadyRegisteredException();
        }
        var user = new LmsUser(request.name(), request.email(), passwordEncoder.encode(request.password()));
        try {
            return UserResponse.from(users.saveAndFlush(user));
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
