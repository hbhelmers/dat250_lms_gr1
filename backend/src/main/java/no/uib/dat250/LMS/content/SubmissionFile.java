package no.uib.dat250.LMS.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

record SubmissionFile(String filename, String mediaType, byte[] bytes, String sha256) {
    static SubmissionFile read(MultipartFile file) {
        if (file.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Velg en fil som ikke er tom.");
        if (file.getSize() > 10 * 1024 * 1024) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "Filen kan være maksimalt 10 MB.");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().replace('\\', '/');
        filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cc}\\p{Cf}]", "").strip();
        if (filename.isBlank() || filename.length() > 255) throw error(HttpStatus.BAD_REQUEST, "Bruk et filnavn på 1–255 tegn.");
        try {
            byte[] bytes = file.getBytes();
            String lower = filename.toLowerCase(Locale.ROOT);
            String type;
            if (lower.endsWith(".pdf") && bytes.length >= 5
                    && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) type = "application/pdf";
            else if (lower.endsWith(".docx") && isWordDocument(bytes)) type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            else if (lower.endsWith(".zip") && bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4)
                type = "application/zip"; // source code for coding exercises
            else throw error(HttpStatus.BAD_REQUEST, "Last opp en PDF, et Word-dokument (.docx) eller en .zip med kode. Filinnholdet må samsvare med filtypen.");
            return new SubmissionFile(filename, type, bytes, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException failure) { throw error(HttpStatus.BAD_REQUEST, "Kunne ikke lese filen. Velg den på nytt."); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static boolean isWordDocument(byte[] bytes) throws IOException {
        boolean types = false, document = false;
        long expanded = 0;
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var buffer = new byte[8192];
            int entries = 0;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 2000) return false;
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    if (expanded > 50L * 1024 * 1024) return false;
                }
                types |= entry.getName().equals("[Content_Types].xml");
                document |= entry.getName().equals("word/document.xml");
            }
        }
        return types && document;
    }
    private static ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}
