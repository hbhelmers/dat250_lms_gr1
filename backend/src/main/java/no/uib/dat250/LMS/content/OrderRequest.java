package no.uib.dat250.LMS.content;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderRequest(
        @NotNull @Size(max = 10_000) List<@NotNull @Positive Long> ids) {}
