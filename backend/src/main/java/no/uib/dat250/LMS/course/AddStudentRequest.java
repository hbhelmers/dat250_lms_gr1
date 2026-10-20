package no.uib.dat250.LMS.course;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import no.uib.dat250.LMS.user.RegistrationRequest;

public record AddStudentRequest(
        @NotBlank(message = "Skriv inn studentens e-postadresse.")
        @Email(message = "Skriv inn en gyldig e-postadresse.")
        @Size(max = 254, message = "E-postadressen kan ha maks 254 tegn.") String email) {
    public AddStudentRequest {
        email = RegistrationRequest.normalizeEmail(email);
    }
}
