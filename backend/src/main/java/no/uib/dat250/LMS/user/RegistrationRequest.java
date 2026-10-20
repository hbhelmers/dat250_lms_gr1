package no.uib.dat250.LMS.user;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.uib.dat250.LMS.course.CourseRole;

public record RegistrationRequest(
        @NotBlank(message = "Skriv inn navnet ditt.")
        @Size(max = 120, message = "Navnet kan ha maks 120 tegn.") String name,
        @NotBlank(message = "Skriv inn e-postadressen din.")
        @Email(message = "Skriv inn en gyldig e-postadresse.")
        @Size(max = 254, message = "E-postadressen kan ha maks 254 tegn.") String email,
        @ValidPassword String password, CourseRole preferredRole, Boolean includeExamples) {

    public RegistrationRequest {
        name = name == null ? null : name.strip();
        email = normalizeEmail(email);
        preferredRole = preferredRole == null ? CourseRole.STUDENT : preferredRole;
        includeExamples = Boolean.TRUE.equals(includeExamples);
    }

    public RegistrationRequest(String name, String email, String password) {
        this(name, email, password, CourseRole.STUDENT, false);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    // Keep credentials out of logs if a request is accidentally formatted.
    @Override
    public String toString() {
        return "RegistrationRequest[redacted]";
    }
}
