package no.uib.dat250.LMS.user;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank(message = "Skriv inn navnet ditt.")
        @Size(max = 120, message = "Navnet kan ha maks 120 tegn.") String name,
        @NotBlank(message = "Skriv inn e-postadressen din.")
        @Email(message = "Skriv inn en gyldig e-postadresse.")
        @Size(max = 254, message = "E-postadressen kan ha maks 254 tegn.") String email,
        @NotBlank(message = "Velg et passord.")
        @Size(min = 15, max = 128, message = "Passordet må ha mellom 15 og 128 tegn.") String password) {

    public RegistrationRequest {
        name = name == null ? null : name.strip();
        email = normalizeEmail(email);
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
