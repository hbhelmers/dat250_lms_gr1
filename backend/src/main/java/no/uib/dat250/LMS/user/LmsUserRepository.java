package no.uib.dat250.LMS.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsUserRepository extends JpaRepository<LmsUser, Long> {
    Optional<LmsUser> findByEmail(String email);
    boolean existsByEmail(String email);
}
