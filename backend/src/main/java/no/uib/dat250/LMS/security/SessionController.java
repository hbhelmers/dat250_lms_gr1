package no.uib.dat250.LMS.security;

import java.security.Principal;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {

    @GetMapping("/api/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/api/session")
    public SessionResponse session(Principal principal) {
        return new SessionResponse(principal.getName());
    }

    public record CsrfResponse(String headerName, String token) {
    }

    public record SessionResponse(String username) {
    }
}
