package no.uib.dat250.LMS.content;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizRepository extends JpaRepository<QuizDefinition, Long> {}
