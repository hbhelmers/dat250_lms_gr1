package no.uib.dat250.LMS.group;

import java.time.Instant;

import jakarta.persistence.*;
import no.uib.dat250.LMS.user.LmsUser;

@Entity
@Table(name = "group_invitations")
public class GroupInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private CourseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private LmsUser invitee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private LmsUser inviter;

    @Column(nullable = false)
    private Instant createdAt;

    protected GroupInvitation() {}

    GroupInvitation(CourseGroup group, LmsUser invitee, LmsUser inviter, Instant createdAt) {
        this.group = group;
        this.invitee = invitee;
        this.inviter = inviter;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public CourseGroup getGroup() { return group; }
    public LmsUser getInvitee() { return invitee; }
    public LmsUser getInviter() { return inviter; }
}
