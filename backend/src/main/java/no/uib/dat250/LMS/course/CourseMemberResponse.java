package no.uib.dat250.LMS.course;

public record CourseMemberResponse(Long userId, String name, String email, CourseRole role) {
    static CourseMemberResponse from(CourseEnrollment enrollment) {
        var user = enrollment.getUser();
        return new CourseMemberResponse(user.getId(), user.getName(), user.getEmail(), enrollment.getRole());
    }
}
