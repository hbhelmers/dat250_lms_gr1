package no.uib.dat250.LMS.content;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/modules")
public class CourseContentController {
    private final CourseContentService content;

    public CourseContentController(CourseContentService content) { this.content = content; }

    @GetMapping
    public List<ModuleResponse> list(@PathVariable Long courseId, Principal principal) {
        return content.list(courseId, principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleResponse createModule(@PathVariable Long courseId, @Valid @RequestBody ModuleRequest request, Principal principal) {
        return content.createModule(courseId, request, principal.getName());
    }

    @PutMapping("/order")
    public List<ModuleResponse> reorderModules(@PathVariable Long courseId, @Valid @RequestBody OrderRequest request, Principal principal) {
        return content.reorderModules(courseId, request, principal.getName());
    }

    @PutMapping("/{moduleId}")
    public ModuleResponse rename(@PathVariable Long courseId, @PathVariable Long moduleId,
            @Valid @RequestBody ModuleRequest request, Principal principal) {
        return content.renameModule(courseId, moduleId, request, principal.getName());
    }

    @DeleteMapping("/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModule(@PathVariable Long courseId, @PathVariable Long moduleId, Principal principal) {
        content.deleteModule(courseId, moduleId, principal.getName());
    }

    @PostMapping("/{moduleId}/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public ContentResponse createPage(@PathVariable Long courseId, @PathVariable Long moduleId,
            @Valid @RequestBody PageRequest request, Principal principal) {
        return content.createPage(courseId, moduleId, request, principal.getName());
    }

    @GetMapping("/{moduleId}/pages/{pageId}")
    public ContentResponse page(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long pageId,
            Principal principal) {
        return content.page(courseId, moduleId, pageId, principal.getName());
    }

    @PutMapping("/{moduleId}/pages/{pageId}")
    public ContentResponse updatePage(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long pageId,
            @Valid @RequestBody PageRequest request, Principal principal) {
        return content.updatePage(courseId, moduleId, pageId, request, principal.getName());
    }

    @DeleteMapping("/{moduleId}/pages/{pageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePage(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long pageId, Principal principal) {
        content.deletePage(courseId, moduleId, pageId, principal.getName());
    }

    @DeleteMapping("/{moduleId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long courseId, @PathVariable Long moduleId, @PathVariable Long itemId, Principal principal) {
        content.deleteItem(courseId, moduleId, itemId, principal.getName());
    }

    @PutMapping({"/{moduleId}/pages/order", "/{moduleId}/items/order"})
    public ModuleResponse reorderPages(@PathVariable Long courseId, @PathVariable Long moduleId,
            @Valid @RequestBody OrderRequest request, Principal principal) {
        return content.reorderPages(courseId, moduleId, request, principal.getName());
    }
}
