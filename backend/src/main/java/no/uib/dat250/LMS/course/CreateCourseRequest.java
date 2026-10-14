package no.uib.dat250.LMS.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCourseRequest(
        @NotBlank(message = "Skriv inn en tittel.")
        @Size(max = 255, message = "Tittelen kan ha maksimalt 255 tegn.")
        String title,

        @NotBlank(message = "Skriv inn en beskrivelse.")
        @Size(max = 10000, message = "Beskrivelsen kan ha maksimalt 10 000 tegn.")
        String description,

        @NotBlank(message = "Skriv inn et semester.")
        @Size(max = 50, message = "Semesteret kan ha maksimalt 50 tegn.")
        String semester) {
}
