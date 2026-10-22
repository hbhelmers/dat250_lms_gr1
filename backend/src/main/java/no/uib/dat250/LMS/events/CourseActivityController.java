package no.uib.dat250.LMS.events;

import java.security.Principal;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/courses/{courseId}/activity")
public class CourseActivityController {
    private final CourseActivityService activity;
    public CourseActivityController(CourseActivityService activity) { this.activity = activity; }
    @GetMapping
    public ResponseEntity<List<CourseActivityService.Activity>> list(@PathVariable Long courseId, Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(activity.list(courseId, principal.getName()));
    }
}
