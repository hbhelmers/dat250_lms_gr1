package no.uib.dat250.LMS.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoAccessController {
    private final ExampleSetupService examples;
    public DemoAccessController(ExampleSetupService examples) { this.examples = examples; }
    @GetMapping("/api/demo-access")
    public ExampleSetupService.DemoAccess access() { return examples.access(); }
}
