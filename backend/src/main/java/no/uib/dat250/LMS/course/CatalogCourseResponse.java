package no.uib.dat250.LMS.course;

public record CatalogCourseResponse(Long id, String title, String description, String semester,
        Institution institution, String courseCode, String officialUrl, CourseRole membershipRole) {
    static CatalogCourseResponse from(Course course, CourseRole role) {
        return new CatalogCourseResponse(course.getId(), course.getTitle(), course.getDescription(), course.getSemester(),
                course.getInstitution(), course.getCourseCode(), course.getOfficialUrl(), role);
    }
}
