package no.uib.dat250.LMS.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import no.uib.dat250.LMS.course.CourseRole;

@Entity
@Table(name = "users")
public class LmsUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CourseRole preferredRole = CourseRole.STUDENT;

    protected LmsUser() {
    }

    public LmsUser(String name, String email, String passwordHash) {
        this(name, email, passwordHash, CourseRole.STUDENT);
    }

    public LmsUser(String name, String email, String passwordHash, CourseRole preferredRole) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.preferredRole = preferredRole;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public CourseRole getPreferredRole() { return preferredRole; }
}
