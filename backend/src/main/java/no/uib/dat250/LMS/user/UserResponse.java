package no.uib.dat250.LMS.user;

public record UserResponse(Long id, String name, String email) {
    public static UserResponse from(LmsUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
