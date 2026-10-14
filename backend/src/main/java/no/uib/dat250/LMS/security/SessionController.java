package no.uib.dat250.LMS.security;

import java.security.Principal;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import no.uib.dat250.LMS.user.RegistrationService;
import no.uib.dat250.LMS.user.UserResponse;

@RestController
public class SessionController {
    private final RegistrationService users;

    public SessionController(RegistrationService users) {
        this.users = users;
    }

    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/api/session")
    public UserResponse session(Principal principal) {
        return users.currentUser(principal.getName());
    }

    public record CsrfResponse(String headerName, String token) {
    }

}
