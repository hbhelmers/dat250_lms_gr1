package no.uib.dat250.LMS.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "lms.demo.accounts.enabled", havingValue = "true")
public class DemoAccountsInitializer implements ApplicationRunner {
    private final ExampleSetupService examples;
    public DemoAccountsInitializer(ExampleSetupService examples) { this.examples = examples; }
    @Override public void run(ApplicationArguments arguments) { examples.initializeDemoAccounts(); }
}
