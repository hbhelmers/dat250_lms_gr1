package no.uib.dat250.LMS.content;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record QuizSubmission(@NotNull UUID submissionId,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Answer> answers) {
    public record Answer(@NotNull @Positive Long questionId, @NotNull @Min(0) @Max(7) Integer option) {}
}
