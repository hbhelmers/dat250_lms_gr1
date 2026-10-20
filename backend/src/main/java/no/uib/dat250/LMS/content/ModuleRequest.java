package no.uib.dat250.LMS.content;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModuleRequest(
        @NotBlank(message = "Skriv inn en modultittel.")
        @Size(max = 200, message = "Modultittelen kan ha maks 200 tegn.") String title) {}
