package no.uib.dat250.LMS.content;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import no.uib.dat250.LMS.course.CourseAccess;
import no.uib.dat250.LMS.course.CourseRole;

@Service
@Transactional(readOnly = true)
public class CourseContentService {
    private final CourseAccess access;
    private final CourseModuleRepository modules;
    private final ContentItemRepository items;
    private final Clock clock;

    public CourseContentService(CourseAccess access, CourseModuleRepository modules,
            ContentItemRepository items, Clock clock) {
        this.access = access;
        this.modules = modules;
        this.items = items;
        this.clock = clock;
    }

    public List<ModuleResponse> list(Long courseId, String email) {
        boolean teacher = access.requireMember(courseId, email).getRole() == CourseRole.TEACHER;
        var now = clock.instant();
        Map<Long, List<ContentResponse>> visible = items.findForCourse(courseId).stream()
                .filter(item -> teacher || item.visibleAt(now))
                .collect(Collectors.groupingBy(item -> item.getModule().getId(),
                        Collectors.mapping(ContentResponse::from, Collectors.toList())));
        return modules.findByCourseIdOrderByPositionAscIdAsc(courseId).stream()
                .filter(module -> teacher || visible.containsKey(module.getId()))
                .map(module -> ModuleResponse.from(module, visible.getOrDefault(module.getId(), List.of())))
                .toList();
    }

    @Transactional
    public ModuleResponse createModule(Long courseId, ModuleRequest request, String email) {
        var course = access.lockForTeacher(courseId, email);
        int position = modules.findByCourseIdOrderByPositionAscIdAsc(courseId).size();
        var module = modules.save(new CourseModule(course, request.title().strip(), position));
        return ModuleResponse.from(module, List.of());
    }

    @Transactional
    public ModuleResponse renameModule(Long courseId, Long moduleId, ModuleRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var module = requireModule(courseId, moduleId);
        module.rename(request.title().strip());
        return moduleResponse(module);
    }

    @Transactional
    public void deleteModule(Long courseId, Long moduleId, String email) {
        access.lockForTeacher(courseId, email);
        modules.delete(requireModule(courseId, moduleId));
        var remaining = modules.findByCourseIdOrderByPositionAscIdAsc(courseId);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPosition(index);
    }

    @Transactional
    public List<ModuleResponse> reorderModules(Long courseId, OrderRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var ordered = requestedOrder(modules.findByCourseIdOrderByPositionAscIdAsc(courseId), request.ids(), CourseModule::getId);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setPosition(index);
        return list(courseId, email);
    }

    @Transactional
    public ContentResponse createPage(Long courseId, Long moduleId, PageRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var module = requireModule(courseId, moduleId);
        int position = items.findByModuleIdOrderByPositionAscIdAsc(moduleId).size();
        return ContentResponse.from(items.save(new ContentItem(module, request, position)));
    }

    public ContentResponse page(Long courseId, Long moduleId, Long pageId, String email) {
        boolean teacher = access.requireMember(courseId, email).getRole() == CourseRole.TEACHER;
        requireModule(courseId, moduleId);
        var page = requirePage(moduleId, pageId);
        if (!teacher && !page.visibleAt(clock.instant())) throw notFound("Fant ikke siden.");
        return ContentResponse.from(page);
    }

    @Transactional
    public ContentResponse updatePage(Long courseId, Long moduleId, Long pageId, PageRequest request, String email) {
        access.lockForTeacher(courseId, email);
        requireModule(courseId, moduleId);
        var page = requirePage(moduleId, pageId);
        page.update(request);
        return ContentResponse.from(page);
    }

    @Transactional
    public void deletePage(Long courseId, Long moduleId, Long pageId, String email) {
        access.lockForTeacher(courseId, email);
        requireModule(courseId, moduleId);
        items.delete(requirePage(moduleId, pageId));
        reindexItems(moduleId);
    }

    @Transactional
    public void deleteItem(Long courseId, Long moduleId, Long itemId, String email) {
        access.lockForTeacher(courseId, email);
        requireModule(courseId, moduleId);
        items.delete(items.findByIdAndModuleId(itemId, moduleId).orElseThrow(() -> notFound("Fant ikke innholdet.")));
        reindexItems(moduleId);
    }

    private void reindexItems(Long moduleId) {
        var remaining = items.findByModuleIdOrderByPositionAscIdAsc(moduleId);
        for (int index = 0; index < remaining.size(); index++) remaining.get(index).setPosition(index);
    }

    @Transactional
    public ModuleResponse reorderPages(Long courseId, Long moduleId, OrderRequest request, String email) {
        access.lockForTeacher(courseId, email);
        var module = requireModule(courseId, moduleId);
        var ordered = requestedOrder(items.findByModuleIdOrderByPositionAscIdAsc(moduleId), request.ids(), ContentItem::getId);
        for (int index = 0; index < ordered.size(); index++) ordered.get(index).setPosition(index);
        return ModuleResponse.from(module, ordered.stream().map(ContentResponse::from).toList());
    }

    private CourseModule requireModule(Long courseId, Long moduleId) {
        return modules.findByIdAndCourseId(moduleId, courseId).orElseThrow(() -> notFound("Fant ikke modulen."));
    }

    private ContentItem requirePage(Long moduleId, Long pageId) {
        return items.findByIdAndModuleId(pageId, moduleId).filter(item -> item.getType() == ContentType.PAGE)
                .orElseThrow(() -> notFound("Fant ikke siden."));
    }

    private ModuleResponse moduleResponse(CourseModule module) {
        return ModuleResponse.from(module, items.findByModuleIdOrderByPositionAscIdAsc(module.getId()).stream()
                .map(ContentResponse::from).toList());
    }

    private static <T> List<T> requestedOrder(List<T> current, List<Long> ids, Function<T, Long> id) {
        var byId = current.stream().collect(Collectors.toMap(id, Function.identity()));
        if (ids.size() != current.size() || !new HashSet<>(ids).equals(byId.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Rekkefølgen må inneholde hvert element én gang. Oppdater oversikten og prøv igjen.");
        }
        return ids.stream().map(byId::get).toList();
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
