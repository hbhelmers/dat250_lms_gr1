package no.uib.dat250.LMS.course;

public record CourseResponse(Long id, String title, String description, String semester, CourseRole role,
        Institution institution, String courseCode, boolean selfEnrollment, String officialUrl) {

    static CourseResponse from(Course course, CourseRole role) {
        return new CourseResponse(
                course.getId(), course.getTitle(), course.getDescription(), course.getSemester(), role,
                course.getInstitution(), course.getCourseCode(), course.isSelfEnrollment(), course.getOfficialUrl());
    }
}
