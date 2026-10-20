package no.uib.dat250.LMS.user;

import no.uib.dat250.LMS.course.CourseRole;

public record UserResponse(Long id, String name, String email, CourseRole preferredRole) {
    public static UserResponse from(LmsUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPreferredRole());
    }
}
