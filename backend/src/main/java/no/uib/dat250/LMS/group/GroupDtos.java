package no.uib.dat250.LMS.group;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GroupDtos {
    private GroupDtos() {}

    public record GroupRequest(
            @NotBlank(message = "Skriv inn et gruppenavn.") @Size(max = 100, message = "Gruppenavnet kan ha maks 100 tegn.") String name,
            @NotNull(message = "Velg hvor mange som kan være i gruppen.") @Min(value = 1, message = "En gruppe må ha plass til minst én.")
            @Max(value = 50, message = "En gruppe kan ha maks 50 medlemmer.") Integer capacity,
            @NotNull(message = "Velg om gruppen er åpen.") Boolean open) {}

    public record UserRequest(@NotNull(message = "Velg en student.") Long userId) {}

    public record SeekingRequest(@Size(max = 280, message = "Notatet kan ha maks 280 tegn.") String note) {}

    /** Everything the Grupper tab shows, so every action can return the new state. E-mails are only filled in for teachers. */
    public record GroupOverview(boolean teacher, Long myUserId, Long myGroupId, boolean seeking, String myNote,
            List<GroupView> groups, List<SeekerView> seekers, List<InvitationView> invitations, List<Person> ungroupedStudents) {}

    public record Person(Long userId, String name, String email) {}
    public record GroupView(Long id, String name, int capacity, boolean open, List<Person> members) {}
    public record SeekerView(Long userId, String name, String email, String note, Instant since, boolean invited) {}
    public record InvitationView(Long id, Long groupId, String groupName, String invitedBy) {}
}
