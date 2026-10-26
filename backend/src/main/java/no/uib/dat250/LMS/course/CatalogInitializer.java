package no.uib.dat250.LMS.course;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import no.uib.dat250.LMS.user.ExampleSetupService;
import no.uib.dat250.LMS.user.LmsUserRepository;

@Component
@ConditionalOnProperty(name = {"lms.demo.accounts.enabled", "lms.catalog.seed.enabled"}, havingValue = "true")
public class CatalogInitializer implements ApplicationRunner {
    private final ExampleSetupService examples;
    private final CatalogSeedService catalog;
    private final LmsUserRepository users;
    public CatalogInitializer(ExampleSetupService examples, CatalogSeedService catalog, LmsUserRepository users) {
        this.examples = examples; this.catalog = catalog; this.users = users;
    }
    @Override public void run(ApplicationArguments arguments) {
        examples.initializeDemoAccounts();
        catalog.seed(users.findByEmail(ExampleSetupService.TEACHER_EMAIL).orElseThrow());
    }
}
