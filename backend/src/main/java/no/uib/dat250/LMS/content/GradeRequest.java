package no.uib.dat250.LMS.content;
import jakarta.validation.constraints.*;
public record GradeRequest(@NotNull AssignmentGrade grade, @NotNull @Size(max = 10_000) String feedback,
        @NotNull Boolean published) {}
