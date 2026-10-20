package no.uib.dat250.LMS.course;

public record CourseResponse(Long id, String title, String description, String semester, CourseRole role) {

    static CourseResponse from(Course course, CourseRole role) {
        return new CourseResponse(
                course.getId(), course.getTitle(), course.getDescription(), course.getSemester(), role);
    }
}
