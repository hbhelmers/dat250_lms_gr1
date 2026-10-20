package no.uib.dat250.LMS.content;

import java.util.List;

public record ModuleResponse(Long id, String title, int position, List<ContentResponse> items) {
    static ModuleResponse from(CourseModule module, List<ContentResponse> items) {
        return new ModuleResponse(module.getId(), module.getTitle(), module.getPosition(), items);
    }
}
