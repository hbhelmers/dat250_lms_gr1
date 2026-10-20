package no.uib.dat250.LMS.content;

import java.util.List;
import jakarta.validation.constraints.*;

public record QuizQuestionRequest(
        @NotBlank @Size(max = 2000) String prompt,
        @NotNull @Size(min = 2, max = 8) List<@NotBlank @Size(max = 1000) String> options,
        @NotNull @Min(0) @Max(7) Integer correctOption,
        @Size(max = 2000) String hint) {
    @AssertTrue(message = "Velg et gyldig riktig svar og bruk ulike svaralternativer.")
    public boolean isOptionsValid() {
        return options == null || correctOption == null || options.stream().anyMatch(option -> option == null)
                || (correctOption < options.size() && options.stream().map(String::strip).distinct().count() == options.size());
    }
}
