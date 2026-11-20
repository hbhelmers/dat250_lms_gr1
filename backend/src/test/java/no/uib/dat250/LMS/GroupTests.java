package no.uib.dat250.LMS;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import no.uib.dat250.LMS.course.AddStudentRequest;
import no.uib.dat250.LMS.course.CourseRepository;
import no.uib.dat250.LMS.course.CourseRole;
import no.uib.dat250.LMS.course.CourseService;
import no.uib.dat250.LMS.course.CreateCourseRequest;
import no.uib.dat250.LMS.user.LmsUser;
import no.uib.dat250.LMS.user.LmsUserRepository;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = { "spring.config.import=", "POSTGRES_PASSWORD=test-password", "LMS_LEGACY_TEACHER_EMAIL=",
        "lms.demo.accounts.enabled=false", "lms.messaging.enabled=false" })
@AutoConfigureMockMvc
@Import(TestDatabaseConfiguration.class)
class GroupTests {
    private static final String TEACHER = "teacher@example.test";
    private static final String ADA = "ada@example.test";
    private static final String BO = "bo@example.test";
    private static final String CY = "cy@example.test";
    private static final String OUTSIDER = "outsider@example.test";

    @Autowired private MockMvc mvc;
    @Autowired private CourseRepository courses;
    @Autowired private CourseService courseService;
    @Autowired private LmsUserRepository users;
    private long courseId;

    @BeforeEach
    void seedCourse() {
        courses.deleteAll();
        users.deleteAll();
        for (var email : List.of(TEACHER, ADA, BO, CY, OUTSIDER)) {
            String name = email.substring(0, email.indexOf('@'));
            users.save(new LmsUser(name, email, "unused", email.equals(TEACHER) ? CourseRole.TEACHER : CourseRole.STUDENT));
        }
        courseId = courseService.create(new CreateCourseRequest("DAT250", "Gruppetest", "Høst 2026"), TEACHER).id();
        for (var student : List.of(ADA, BO, CY)) courseService.addStudent(courseId, new AddStudentRequest(student), TEACHER);
    }

    @Test
    void onlyTeachersCreateGroupsAndOutsidersSeeNothing() throws Exception {
        createGroup("Gruppe 1", 3, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(1))).andExpect(jsonPath("$.groups[0].name").value("Gruppe 1"))
                .andExpect(jsonPath("$.ungroupedStudents", hasSize(3)));
        createGroup("gruppe 1", 3, true).andExpect(status().isConflict());
        mvc.perform(write(post(base()), ADA, group("Min gruppe", 3, true))).andExpect(status().isForbidden());
        mvc.perform(get(base()).with(user(OUTSIDER))).andExpect(status().isNotFound());
        mvc.perform(write(post(base()), TEACHER, group(" ", 0, true))).andExpect(status().isBadRequest());
    }

    @Test
    void studentsJoinOpenGroupsWithSpaceAndOnlyOneGroup() throws Exception {
        long open = groupId(createGroup("Åpen", 1, true));
        long closed = groupId(createGroup("Lukket", 3, false));
        long other = groupId(createGroup("Annen", 3, true));

        mvc.perform(write(post(base() + "/" + closed + "/join"), ADA, "")).andExpect(status().isConflict());
        mvc.perform(write(post(base() + "/" + open + "/join"), ADA, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.myGroupId").value(open));
        mvc.perform(write(post(base() + "/" + open + "/join"), BO, "")).andExpect(status().isConflict()); // full
        mvc.perform(write(post(base() + "/" + other + "/join"), ADA, "")).andExpect(status().isConflict()); // already in a group
        mvc.perform(write(post(base() + "/" + open + "/join"), TEACHER, "")).andExpect(status().isForbidden());

        mvc.perform(write(delete(base() + "/" + open + "/members/" + userId(BO)), ADA, "")).andExpect(status().isForbidden());
        mvc.perform(write(delete(base() + "/" + open + "/members/" + userId(ADA)), ADA, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.myGroupId").value(nullValue()));
    }

    @Test
    void seekersAreVisibleWithTheirNoteButNotTheirEmail() throws Exception {
        mvc.perform(write(put(base() + "/seeking"), ADA, "{\"note\":\"  Vil jobbe med backend, helst på dagtid. \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seeking").value(true))
                .andExpect(jsonPath("$.myNote").value("Vil jobbe med backend, helst på dagtid."))
                .andExpect(jsonPath("$.seekers", hasSize(0)));
        mvc.perform(get(base()).with(user(BO))).andExpect(status().isOk())
                .andExpect(jsonPath("$.seekers", hasSize(1))).andExpect(jsonPath("$.seekers[0].name").value("ada"))
                .andExpect(jsonPath("$.seekers[0].note").value("Vil jobbe med backend, helst på dagtid."))
                .andExpect(jsonPath("$.seekers[0].email").value(nullValue()));
        mvc.perform(get(base()).with(user(TEACHER))).andExpect(jsonPath("$.seekers[0].email").value(ADA));
        mvc.perform(write(put(base() + "/seeking"), ADA, "{\"note\":\"" + "x".repeat(281) + "\"}")).andExpect(status().isBadRequest());
        mvc.perform(write(delete(base() + "/seeking"), ADA, "")).andExpect(status().isOk()).andExpect(jsonPath("$.seeking").value(false));
    }

    @Test
    void membersInviteSeekersAndAcceptingJoinsTheGroup() throws Exception {
        long closed = groupId(createGroup("Lukket", 3, false));
        long other = groupId(createGroup("Annen", 3, false));
        mvc.perform(write(post(base() + "/" + closed + "/members"), TEACHER, "{\"userId\":" + userId(ADA) + "}")).andExpect(status().isOk());
        mvc.perform(write(post(base() + "/" + other + "/members"), TEACHER, "{\"userId\":" + userId(CY) + "}")).andExpect(status().isOk());

        // Bo is not looking for a group yet, and Bo cannot invite to a group Bo is not in.
        mvc.perform(write(post(base() + "/" + closed + "/invitations"), ADA, "{\"userId\":" + userId(BO) + "}")).andExpect(status().isConflict());
        mvc.perform(write(put(base() + "/seeking"), BO, "{\"note\":\"Hei!\"}")).andExpect(status().isOk());
        mvc.perform(write(post(base() + "/" + other + "/invitations"), ADA, "{\"userId\":" + userId(BO) + "}")).andExpect(status().isForbidden());

        mvc.perform(write(post(base() + "/" + closed + "/invitations"), ADA, "{\"userId\":" + userId(BO) + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.seekers[0].invited").value(true));
        mvc.perform(write(post(base() + "/" + other + "/invitations"), CY, "{\"userId\":" + userId(BO) + "}")).andExpect(status().isOk());

        var invitations = mvc.perform(get(base()).with(user(BO))).andExpect(jsonPath("$.invitations", hasSize(2)))
                .andExpect(jsonPath("$.invitations[0].groupName").value("Lukket"))
                .andExpect(jsonPath("$.invitations[0].invitedBy").value("ada")).andReturn().getResponse().getContentAsString();
        long fromCy = ((Number) JsonPath.read(invitations, "$.invitations[1].id")).longValue();
        long fromAda = ((Number) JsonPath.read(invitations, "$.invitations[0].id")).longValue();

        mvc.perform(write(delete(base() + "/invitations/" + fromCy), BO, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.invitations", hasSize(1)));
        mvc.perform(write(post(base() + "/invitations/" + fromAda + "/accept"), CY, "")).andExpect(status().isNotFound());
        mvc.perform(write(post(base() + "/invitations/" + fromAda + "/accept"), BO, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.myGroupId").value(closed)).andExpect(jsonPath("$.seeking").value(false))
                .andExpect(jsonPath("$.invitations", hasSize(0)));
        mvc.perform(get(base()).with(user(ADA))).andExpect(jsonPath("$.seekers", hasSize(0)))
                .andExpect(jsonPath("$.groups[?(@.id == " + closed + ")].members[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("ada", "bo")))
                .andExpect(jsonPath("$.groups[0].members[0].email").value(nullValue()));
    }

    @Test
    void joiningAGroupClearsSeekingAndOtherInvitations() throws Exception {
        long open = groupId(createGroup("Åpen", 3, true));
        long closed = groupId(createGroup("Lukket", 3, false));
        mvc.perform(write(post(base() + "/" + closed + "/members"), TEACHER, "{\"userId\":" + userId(ADA) + "}"));
        mvc.perform(write(put(base() + "/seeking"), BO, "{\"note\":\"\"}")).andExpect(status().isOk());
        mvc.perform(write(post(base() + "/" + closed + "/invitations"), ADA, "{\"userId\":" + userId(BO) + "}")).andExpect(status().isOk());

        mvc.perform(write(post(base() + "/" + open + "/join"), BO, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.seeking").value(false)).andExpect(jsonPath("$.invitations", hasSize(0)));
        mvc.perform(write(put(base() + "/seeking"), BO, "{\"note\":\"\"}")).andExpect(status().isConflict());
    }

    @Test
    void teacherManagesMembersAndCapacity() throws Exception {
        long group = groupId(createGroup("Gruppe", 2, false));
        mvc.perform(write(post(base() + "/" + group + "/members"), TEACHER, "{\"userId\":" + userId(ADA) + "}")).andExpect(status().isOk());
        mvc.perform(write(post(base() + "/" + group + "/members"), TEACHER, "{\"userId\":" + userId(BO) + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.ungroupedStudents", hasSize(1)));
        mvc.perform(write(post(base() + "/" + group + "/members"), TEACHER, "{\"userId\":" + userId(CY) + "}")).andExpect(status().isConflict());
        mvc.perform(write(post(base() + "/" + group + "/members"), TEACHER, "{\"userId\":" + userId(OUTSIDER) + "}")).andExpect(status().isNotFound());
        mvc.perform(write(put(base() + "/" + group), TEACHER, group("Gruppe", 1, false))).andExpect(status().isBadRequest());

        mvc.perform(write(delete(base() + "/" + group + "/members/" + userId(BO)), TEACHER, "")).andExpect(status().isOk());
        mvc.perform(write(put(base() + "/" + group), TEACHER, group("Gruppe A", 1, true))).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].name").value("Gruppe A")).andExpect(jsonPath("$.groups[0].open").value(true));
        mvc.perform(write(delete(base() + "/" + group), TEACHER, "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(0))).andExpect(jsonPath("$.ungroupedStudents", hasSize(3)));
    }

    private ResultActions createGroup(String name, int capacity, boolean open) throws Exception {
        return mvc.perform(write(post(base()), TEACHER, group(name, capacity, open)));
    }

    private long groupId(ResultActions created) throws Exception {
        String json = created.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$.groups[*].id");
        return ids.stream().mapToLong(Number::longValue).max().orElseThrow();
    }

    private long userId(String email) { return users.findByEmail(email).orElseThrow().getId(); }

    private String base() { return "/api/courses/" + courseId + "/groups"; }

    private static String group(String name, int capacity, boolean open) {
        return "{\"name\":\"" + name + "\",\"capacity\":" + capacity + ",\"open\":" + open + "}";
    }

    private static MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder request, String email, String body) {
        var builder = request.with(user(email)).with(csrf());
        return body.isEmpty() ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
