package no.uib.dat250.LMS.storage;

import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "lms.storage.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class ObjectCleanup {
    private final JdbcTemplate jdbc;
    private final FileStorage storage;
    public ObjectCleanup(JdbcTemplate jdbc, FileStorage storage) { this.jdbc = jdbc; this.storage = storage; }

    @Scheduled(fixedDelayString = "${lms.storage.cleanup.interval-ms:60000}", initialDelay = 60000)
    public void removeDeletedFiles() {
        for (var key : jdbc.queryForList("SELECT object_key FROM pending_object_deletions ORDER BY queued_at LIMIT 100", String.class)) {
            try {
                storage.delete(key);
                jdbc.update("DELETE FROM pending_object_deletions WHERE object_key = ?", key);
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger(ObjectCleanup.class).warn("File cleanup will retry when object storage is available.");
                return;
            }
        }
    }
}
