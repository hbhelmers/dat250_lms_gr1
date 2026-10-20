package no.uib.dat250.LMS.content;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "content_items")
public class ContentItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContentType type = ContentType.PAGE;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean published;

    private Instant visibleFrom;
    private Instant visibleUntil;

    protected ContentItem() {}

    public ContentItem(CourseModule module, PageRequest request, int position) {
        this(module, ContentType.PAGE, request, position);
    }

    public ContentItem(CourseModule module, ContentType type, PageRequest request, int position) {
        this.module = module;
        this.type = type;
        this.position = position;
        update(request);
    }

    public void update(PageRequest request) {
        title = request.title().strip();
        body = request.body();
        published = request.published();
        visibleFrom = request.visibleFrom();
        visibleUntil = request.visibleUntil();
    }

    public boolean visibleAt(Instant now) {
        return published && (visibleFrom == null || !now.isBefore(visibleFrom))
                && (visibleUntil == null || now.isBefore(visibleUntil));
    }

    public Long getId() { return id; }
    public CourseModule getModule() { return module; }
    public ContentType getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public int getPosition() { return position; }
    public boolean isPublished() { return published; }
    public Instant getVisibleFrom() { return visibleFrom; }
    public Instant getVisibleUntil() { return visibleUntil; }
    public void setPosition(int position) { this.position = position; }
}
