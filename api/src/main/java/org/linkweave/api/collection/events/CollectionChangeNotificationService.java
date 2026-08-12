package org.linkweave.api.collection.events;

import jakarta.enterprise.event.Event;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.shared.user.CurrentUserService;
import org.linkweave.api.shared.user.User;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Announces collection changes to whoever is listening (UC-104 main scenario).
 *
 * <p>Called from the write services rather than from the resources, for the same
 * reason {@code ScreenshotWriteService} fires its own event: the notification
 * belongs next to the change it describes, inside the same transaction, so it
 * cannot be forgotten by one caller and remembered by another. What the write
 * services must <em>not</em> have to care about is who is acting and from which
 * tab, so that is resolved here from the ambient request — exactly how
 * {@code AbstractEntityListener} already stamps the audit columns.
 *
 * <p>Delivery is still gated on commit by {@link CollectionEventPublisher}
 * observing {@code AFTER_SUCCESS} (BR-203).
 */
@Service
@RequiredArgsConstructor
public class CollectionChangeNotificationService {

    private final Event<CollectionChanged> collectionChanged;
    private final OriginClientIdRequestFilter originClientIdFilter;
    private final CurrentUserService currentUserService;

    private @Nullable String actorName;
    private boolean actorNameResolved;

    /**
     * {@code bookmarkId} is null when the change covered several bookmarks at
     * once — an import, for instance — see {@link CollectionChanged}.
     */
    public void bookmarkAdded(@NonNull ID<Collection> collectionId, @Nullable ID<Bookmark> bookmarkId) {
        fire(collectionId, bookmarkId, ChangeKind.BOOKMARK_ADDED);
    }

    /** @see #bookmarkAdded */
    public void bookmarkChanged(@NonNull ID<Collection> collectionId, @Nullable ID<Bookmark> bookmarkId) {
        fire(collectionId, bookmarkId, ChangeKind.BOOKMARK_CHANGED);
    }

    public void bookmarkRemoved(@NonNull ID<Collection> collectionId, @Nullable ID<Bookmark> bookmarkId) {
        fire(collectionId, bookmarkId, ChangeKind.BOOKMARK_REMOVED);
    }

    public void folderAdded(@NonNull ID<Collection> collectionId) {
        fire(collectionId, null, ChangeKind.FOLDER_ADDED);
    }

    public void folderChanged(@NonNull ID<Collection> collectionId) {
        fire(collectionId, null, ChangeKind.FOLDER_CHANGED);
    }

    /**
     * Covers the bookmarks that went down with the folder — one notification for
     * the operation, not one per casualty. See {@link ChangeKind#FOLDER_REMOVED}.
     */
    public void folderRemoved(@NonNull ID<Collection> collectionId) {
        fire(collectionId, null, ChangeKind.FOLDER_REMOVED);
    }

    /**
     * Tags, property definitions, auto-tag rules — see
     * {@link ChangeKind#COLLECTION_CHANGED}.
     */
    public void collectionChanged(@NonNull ID<Collection> collectionId) {
        fire(collectionId, null, ChangeKind.COLLECTION_CHANGED);
    }

    private void fire(
        @NonNull ID<Collection> collectionId,
        @Nullable ID<Bookmark> bookmarkId,
        @NonNull ChangeKind kind
    ) {
        collectionChanged.fire(new CollectionChanged(
            collectionId,
            bookmarkId,
            kind,
            originClientIdFilter.getOriginClientId(),
            currentActorName()));
    }

    /**
     * Display name for the attribution text (BR-209), or null when nobody is
     * logged in — a background job's change is shown without attribution (A1).
     *
     * <p>Resolved at most once per request. {@code findCurrentUser()} is a query
     * (with permissions fetch-joined), and one request can perform many writes —
     * an import, a batch edit — each of which announces itself. The identity
     * cannot change mid-request, so caching it is free correctness-wise; this
     * bean is {@code @RequestScoped} via {@code @Service}, so the cache dies with
     * the request rather than outliving the user it describes.
     */
    private @Nullable String currentActorName() {
        if (!actorNameResolved) {
            actorName = currentUserService.findCurrentUser()
                .map(User::getVornameName)
                .orElse(null);
            actorNameResolved = true;
        }
        return actorName;
    }
}
