package no.uib.dat250.LMS.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {
    private final RegistrationService registration;

    public RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    @PostMapping("/api/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegistrationRequest request) {
        return registration.register(request);
    }
}
