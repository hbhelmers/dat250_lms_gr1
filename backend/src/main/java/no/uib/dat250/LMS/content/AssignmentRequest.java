package no.uib.dat250.LMS.content;

import java.time.Instant;
import jakarta.validation.constraints.*;

public record AssignmentRequest(@NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 100_000) String body, @NotNull Boolean published,
        Instant visibleFrom, Instant visibleUntil, Instant deadline, @NotNull Boolean acceptLate,
        Boolean groupWork,
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Startkoden må være en http(s)-lenke.") String starterRepoUrl) {
    public AssignmentRequest(String title, String body, Boolean published, Instant visibleFrom, Instant visibleUntil,
            Instant deadline, Boolean acceptLate) {
        this(title, body, published, visibleFrom, visibleUntil, deadline, acceptLate, false, null);
    }
    @AssertTrue(message = "Sluttidspunktet må være etter starttidspunktet.")
    public boolean isVisibilityWindowValid() {
        return visibleFrom == null || visibleUntil == null || visibleFrom.isBefore(visibleUntil);
    }
    public PageRequest content() { return new PageRequest(title, body, published, visibleFrom, visibleUntil); }
}
