package no.uib.dat250.LMS.content;

import java.time.Instant;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PageRequest(
        @NotBlank(message = "Skriv inn en sidetittel.")
        @Size(max = 255, message = "Sidetittelen kan ha maks 255 tegn.") String title,
        @NotBlank(message = "Skriv inn innholdet på siden.")
        @Size(max = 100_000, message = "Sideinnholdet kan ha maks 100 000 tegn.") String body,
        @NotNull(message = "Velg om siden er publisert.") Boolean published,
        Instant visibleFrom, Instant visibleUntil) {

    @AssertTrue(message = "Sluttidspunktet må være etter starttidspunktet.")
    public boolean isVisibilityWindowValid() {
        return visibleFrom == null || visibleUntil == null || visibleFrom.isBefore(visibleUntil);
    }
}
