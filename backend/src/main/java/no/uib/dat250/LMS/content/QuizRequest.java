package no.uib.dat250.LMS.content;

import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record QuizRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull @Size(max = 100_000) String body,
        @NotNull Boolean published, Instant visibleFrom, Instant visibleUntil,
        @NotNull @Min(1) @Max(20) Integer maxAttempts, Instant deadline,
        @NotNull Boolean resultsVisible,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid QuizQuestionRequest> questions) {
    @AssertTrue(message = "Sluttidspunktet må være etter starttidspunktet.")
    public boolean isVisibilityWindowValid() {
        return visibleFrom == null || visibleUntil == null || visibleFrom.isBefore(visibleUntil);
    }
    public PageRequest content() { return new PageRequest(title, body, published, visibleFrom, visibleUntil); }
}
