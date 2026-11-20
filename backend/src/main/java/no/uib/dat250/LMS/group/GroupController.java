package no.uib.dat250.LMS.group;

import java.security.Principal;

import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import no.uib.dat250.LMS.group.GroupDtos.*;

@RestController
@RequestMapping("/api/courses/{courseId}/groups")
public class GroupController {
    private final GroupService groups;

    public GroupController(GroupService groups) { this.groups = groups; }

    @GetMapping
    public GroupOverview overview(@PathVariable Long courseId, Principal principal) {
        return groups.overview(courseId, principal.getName());
    }

    @PostMapping
    public GroupOverview create(@PathVariable Long courseId, @Valid @RequestBody GroupRequest request, Principal principal) {
        return groups.create(courseId, request, principal.getName());
    }

    @PutMapping("/{groupId}")
    public GroupOverview update(@PathVariable Long courseId, @PathVariable Long groupId, @Valid @RequestBody GroupRequest request, Principal principal) {
        return groups.update(courseId, groupId, request, principal.getName());
    }

    @DeleteMapping("/{groupId}")
    public GroupOverview delete(@PathVariable Long courseId, @PathVariable Long groupId, Principal principal) {
        return groups.delete(courseId, groupId, principal.getName());
    }

    @PostMapping("/{groupId}/members")
    public GroupOverview addMember(@PathVariable Long courseId, @PathVariable Long groupId, @Valid @RequestBody UserRequest request, Principal principal) {
        return groups.addMember(courseId, groupId, request.userId(), principal.getName());
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public GroupOverview removeMember(@PathVariable Long courseId, @PathVariable Long groupId, @PathVariable Long userId, Principal principal) {
        return groups.removeMember(courseId, groupId, userId, principal.getName());
    }

    @PostMapping("/{groupId}/join")
    public GroupOverview join(@PathVariable Long courseId, @PathVariable Long groupId, Principal principal) {
        return groups.joinOpenGroup(courseId, groupId, principal.getName());
    }

    @PutMapping("/seeking")
    public GroupOverview startSeeking(@PathVariable Long courseId, @Valid @RequestBody SeekingRequest request, Principal principal) {
        return groups.startSeeking(courseId, request, principal.getName());
    }

    @DeleteMapping("/seeking")
    public GroupOverview stopSeeking(@PathVariable Long courseId, Principal principal) {
        return groups.stopSeeking(courseId, principal.getName());
    }

    @PostMapping("/{groupId}/invitations")
    public GroupOverview invite(@PathVariable Long courseId, @PathVariable Long groupId, @Valid @RequestBody UserRequest request, Principal principal) {
        return groups.invite(courseId, groupId, request.userId(), principal.getName());
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public GroupOverview accept(@PathVariable Long courseId, @PathVariable Long invitationId, Principal principal) {
        return groups.acceptInvitation(courseId, invitationId, principal.getName());
    }

    @DeleteMapping("/invitations/{invitationId}")
    public GroupOverview decline(@PathVariable Long courseId, @PathVariable Long invitationId, Principal principal) {
        return groups.declineInvitation(courseId, invitationId, principal.getName());
    }
}
