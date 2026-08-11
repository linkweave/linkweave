package org.linkweave.api.collection.events;

/**
 * What a {@link org.linkweave.api.collection.events.json.CollectionEventJson}
 * announces. Deliberately a discriminator and nothing more: the notification
 * carries no state, so the client reloads through the normal server side read
 * path rather than applying the payload (BR-202).
 *
 */
public enum ChangeKind {

    /**
     * A deferred screenshot capture completed and was committed, so the bookmark
     * now has a preview it did not have when the client last read it (UC-104 A1).
     */
    SCREENSHOT_READY,

    /** A member added a bookmark to the collection. */
    BOOKMARK_ADDED,

    /**
     * A member edited a bookmark, moved it, or changed its tags. One kind rather
     * than one per field: the client re-reads the collection either way (BR-202),
     * so a finer split would be detail nobody acts on.
     */
    BOOKMARK_CHANGED,

    /** A member deleted a bookmark from the collection. */
    BOOKMARK_REMOVED,

    /**
     * Nothing changed — traffic emitted purely to keep the connection alive
     * (BR-208). Clients ignore it.
     *
     * <p>An idle stream is this channel's normal state, and an intermediary that
     * closes idle connections would otherwise drop it silently. A kind rather
     * than an SSE comment line because the stream is typed
     * ({@code Multi<CollectionEventJson>}), so every frame on it is a serialized
     * event; smuggling a raw comment through would mean lying about the return
     * type. It is the one kind that carries no {@code bookmarkId}.
     */
    HEARTBEAT
}
