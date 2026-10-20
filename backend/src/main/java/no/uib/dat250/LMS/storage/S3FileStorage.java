package no.uib.dat250.LMS.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import jakarta.annotation.PreDestroy;

@Component
public class S3FileStorage implements FileStorage {
    private final S3Client client;
    private final String bucket;

    public S3FileStorage(@Value("${lms.storage.endpoint}") String endpoint,
            @Value("${lms.storage.bucket}") String bucket,
            @Value("${lms.storage.access-key}") String accessKey,
            @Value("${lms.storage.secret-key}") String secretKey) {
        this.bucket = bucket;
        client = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.US_EAST_1)
                .forcePathStyle(true).credentialsProvider(() -> AwsBasicCredentials.create(accessKey, secretKey))
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(Duration.ofSeconds(8))).build();
    }
    @Override public void put(String key, byte[] bytes, String mediaType) {
        try { client.putObject(request -> request.bucket(bucket).key(key).contentType(mediaType), RequestBody.fromBytes(bytes)); }
        catch (SdkException | IllegalArgumentException failure) { throw unavailable(); }
    }
    @Override public byte[] get(String key) {
        try { return client.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray(); }
        catch (SdkException | IllegalArgumentException failure) { throw unavailable(); }
    }
    @Override public void delete(String key) {
        try { client.deleteObject(request -> request.bucket(bucket).key(key)); }
        catch (SdkException | IllegalArgumentException failure) { throw unavailable(); }
    }
    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Fillagringen er utilgjengelig. Start Docker-tjenestene og prøv igjen.");
    }
    @PreDestroy void close() { client.close(); }
}
