package no.uib.dat250.LMS.group;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import no.uib.dat250.LMS.course.CourseAccess;
import no.uib.dat250.LMS.course.CourseEnrollment;
import no.uib.dat250.LMS.course.CourseEnrollmentRepository;
import no.uib.dat250.LMS.course.CourseRole;
import no.uib.dat250.LMS.group.GroupDtos.*;
import no.uib.dat250.LMS.user.LmsUser;

/**
 * Teacher-made groups plus "Finn gruppe": students join open groups, mark themselves as looking for a group,
 * and invite people who are looking. Every write takes the course lock, so capacity checks cannot race.
 */
@Service
@Transactional(readOnly = true)
public class GroupService {
    private final CourseAccess access;
    private final CourseEnrollmentRepository enrollments;
    private final CourseGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final GroupSeekerRepository seekers;
    private final GroupInvitationRepository invitations;
    private final Clock clock;

    public GroupService(CourseAccess access, CourseEnrollmentRepository enrollments, CourseGroupRepository groups,
            GroupMembershipRepository memberships, GroupSeekerRepository seekers, GroupInvitationRepository invitations, Clock clock) {
        this.access = access;
        this.enrollments = enrollments;
        this.groups = groups;
        this.memberships = memberships;
        this.seekers = seekers;
        this.invitations = invitations;
        this.clock = clock;
    }

    public GroupOverview overview(Long courseId, String email) {
        return overview(access.requireMember(courseId, email));
    }

    // Teacher actions

    @Transactional
    public GroupOverview create(Long courseId, GroupRequest request, String email) {
        var course = access.lockForTeacher(courseId, email);
        if (groups.existsByCourseIdAndNameIgnoreCase(courseId, request.name().strip())) throw duplicateName();
        groups.save(new CourseGroup(course, request, clock.instant()));
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview update(Long courseId, Long groupId, GroupRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var group = requireGroup(courseId, groupId);
        if (groups.existsByCourseIdAndNameIgnoreCaseAndIdNot(courseId, request.name().strip(), groupId)) throw duplicateName();
        if (request.capacity() < memberships.countByGroupId(groupId)) throw error(HttpStatus.BAD_REQUEST,
                "Gruppen har flere medlemmer enn den nye plassgrensen. Fjern medlemmer først.");
        group.update(request);
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview delete(Long courseId, Long groupId, String email) {
        access.lockForTeacher(courseId, email);
        groups.delete(requireGroup(courseId, groupId));
        groups.flush();
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview addMember(Long courseId, Long groupId, Long userId, String email) {
        access.lockForTeacher(courseId, email);
        var student = enrollments.findMembers(courseId).stream()
                .filter(member -> member.getUser().getId().equals(userId) && member.getRole() == CourseRole.STUDENT)
                .findFirst().orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke studenten i emnet."));
        join(requireGroup(courseId, groupId), student.getUser());
        return overview(courseId, email);
    }

    /** Teachers remove anyone; a student may only remove themselves (leave the group). */
    @Transactional
    public GroupOverview removeMember(Long courseId, Long groupId, Long userId, String email) {
        var me = access.lockForMember(courseId, email);
        if (me.getRole() != CourseRole.TEACHER && !me.getUser().getId().equals(userId))
            throw error(HttpStatus.FORBIDDEN, "Du kan bare melde deg selv ut av en gruppe.");
        var membership = memberships.findByCourseIdAndUserId(courseId, userId)
                .filter(member -> member.getGroup().getId().equals(groupId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke medlemmet i gruppen."));
        memberships.delete(membership);
        return overview(courseId, email);
    }

    // Student actions

    @Transactional
    public GroupOverview joinOpenGroup(Long courseId, Long groupId, String email) {
        var me = lockForStudent(courseId, email);
        var group = requireGroup(courseId, groupId);
        if (!group.isOpen()) throw error(HttpStatus.CONFLICT, "Gruppen er ikke åpen. Du kan bli med hvis et medlem inviterer deg.");
        join(group, me.getUser());
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview startSeeking(Long courseId, SeekingRequest request, String email) {
        var me = lockForStudent(courseId, email);
        if (memberships.existsByCourseIdAndUserId(courseId, me.getUser().getId()))
            throw error(HttpStatus.CONFLICT, "Du er allerede med i en gruppe.");
        String note = request.note() == null ? "" : request.note().strip();
        seekers.findByCourseIdAndUserId(courseId, me.getUser().getId()).ifPresentOrElse(seeker -> seeker.setNote(note),
                () -> seekers.save(new GroupSeeker(me.getCourse(), me.getUser(), note, clock.instant())));
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview stopSeeking(Long courseId, String email) {
        var me = lockForStudent(courseId, email);
        seekers.deleteForStudent(courseId, me.getUser().getId());
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview invite(Long courseId, Long groupId, Long userId, String email) {
        var me = lockForStudent(courseId, email);
        var group = requireGroup(courseId, groupId);
        memberships.findByCourseIdAndUserId(courseId, me.getUser().getId())
                .filter(member -> member.getGroup().getId().equals(groupId))
                .orElseThrow(() -> error(HttpStatus.FORBIDDEN, "Bare medlemmer av gruppen kan invitere."));
        var seeker = seekers.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT, "Studenten ser ikke etter gruppe lenger."));
        requireSpace(group);
        if (!invitations.existsByGroupIdAndInviteeId(groupId, userId))
            invitations.save(new GroupInvitation(group, seeker.getUser(), me.getUser(), clock.instant()));
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview acceptInvitation(Long courseId, Long invitationId, String email) {
        var me = lockForStudent(courseId, email);
        var invitation = invitations.findReceived(invitationId, courseId, me.getUser().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke invitasjonen."));
        join(invitation.getGroup(), me.getUser());
        return overview(courseId, email);
    }

    @Transactional
    public GroupOverview declineInvitation(Long courseId, Long invitationId, String email) {
        var me = access.lockForMember(courseId, email);
        invitations.delete(invitations.findReceived(invitationId, courseId, me.getUser().getId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke invitasjonen.")));
        return overview(courseId, email);
    }

    // Shared rules

    /** The single way into a group: checks the rules, then clears the student's search and other invitations. */
    private void join(CourseGroup group, LmsUser student) {
        Long courseId = group.getCourse().getId();
        if (memberships.existsByCourseIdAndUserId(courseId, student.getId()))
            throw error(HttpStatus.CONFLICT, student.getName() + " er allerede med i en gruppe i emnet.");
        requireSpace(group);
        memberships.save(new GroupMembership(group, student, clock.instant()));
        seekers.deleteForStudent(courseId, student.getId());
        invitations.deleteForInvitee(courseId, student.getId());
    }

    private void requireSpace(CourseGroup group) {
        if (memberships.countByGroupId(group.getId()) >= group.getCapacity()) throw error(HttpStatus.CONFLICT, "Gruppen er full.");
    }

    private CourseEnrollment lockForStudent(Long courseId, String email) {
        var me = access.lockForMember(courseId, email);
        if (me.getRole() != CourseRole.STUDENT) throw error(HttpStatus.FORBIDDEN, "Dette er for studenter i emnet.");
        return me;
    }

    private CourseGroup requireGroup(Long courseId, Long groupId) {
        return groups.findByIdAndCourseId(groupId, courseId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Fant ikke gruppen."));
    }

    private GroupOverview overview(CourseEnrollment me) {
        Long courseId = me.getCourse().getId();
        Long myId = me.getUser().getId();
        boolean teacher = me.getRole() == CourseRole.TEACHER;
        var allMemberships = memberships.findForCourse(courseId);
        Long myGroupId = allMemberships.stream().filter(member -> member.getUser().getId().equals(myId))
                .map(member -> member.getGroup().getId()).findFirst().orElse(null);

        var groupViews = groups.findByCourseIdOrderByNameAscIdAsc(courseId).stream().map(group -> new GroupView(
                group.getId(), group.getName(), group.getCapacity(), group.isOpen(),
                allMemberships.stream().filter(member -> member.getGroup().getId().equals(group.getId()))
                        .map(member -> person(member.getUser(), teacher)).toList())).toList();

        Set<Long> invitedByMyGroup = myGroupId == null ? Set.of() : new HashSet<>(invitations.findInviteeIds(myGroupId));
        var allSeekers = seekers.findForCourse(courseId);
        var mine = allSeekers.stream().filter(seeker -> seeker.getUser().getId().equals(myId)).findFirst();
        var seekerViews = allSeekers.stream().filter(seeker -> !seeker.getUser().getId().equals(myId))
                .map(seeker -> new SeekerView(seeker.getUser().getId(), seeker.getUser().getName(),
                        teacher ? seeker.getUser().getEmail() : null, seeker.getNote(), seeker.getCreatedAt(),
                        invitedByMyGroup.contains(seeker.getUser().getId()))).toList();

        var invitationViews = invitations.findReceived(courseId, myId).stream().map(invitation -> new InvitationView(
                invitation.getId(), invitation.getGroup().getId(), invitation.getGroup().getName(),
                invitation.getInviter().getName())).toList();

        List<Person> ungrouped = List.of();
        if (teacher) {
            Set<Long> grouped = allMemberships.stream().map(member -> member.getUser().getId()).collect(Collectors.toSet());
            ungrouped = enrollments.findMembers(courseId).stream()
                    .filter(member -> member.getRole() == CourseRole.STUDENT && !grouped.contains(member.getUser().getId()))
                    .map(member -> person(member.getUser(), true)).toList();
        }
        return new GroupOverview(teacher, myId, myGroupId, mine.isPresent(), mine.map(GroupSeeker::getNote).orElse(null),
                groupViews, seekerViews, invitationViews, ungrouped);
    }

    private static Person person(LmsUser user, boolean withEmail) {
        return new Person(user.getId(), user.getName(), withEmail ? user.getEmail() : null);
    }

    private static ResponseStatusException duplicateName() {
        return error(HttpStatus.CONFLICT, "Det finnes allerede en gruppe med dette navnet i emnet.");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
