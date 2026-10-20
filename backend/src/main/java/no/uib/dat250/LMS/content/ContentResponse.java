package no.uib.dat250.LMS.content;

import java.time.Instant;

public record ContentResponse(Long id, Long moduleId, ContentType type, String title, String body,
        int position, boolean published, Instant visibleFrom, Instant visibleUntil) {
    static ContentResponse from(ContentItem item) {
        return new ContentResponse(item.getId(), item.getModule().getId(), item.getType(), item.getTitle(),
                item.getBody(), item.getPosition(), item.isPublished(), item.getVisibleFrom(), item.getVisibleUntil());
    }
}
