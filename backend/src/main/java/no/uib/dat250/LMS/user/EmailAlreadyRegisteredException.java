package no.uib.dat250.LMS.user;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException() {
        super("Denne e-postadressen er allerede registrert. Prøv å logge inn.");
    }
}
