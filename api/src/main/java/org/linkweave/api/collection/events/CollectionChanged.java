package org.linkweave.api.collection.events;

import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Internal CDI event fired <em>inside</em> the transaction that performs a
 * change, and observed after that transaction commits.
 *
 * <p>This indirection is the whole mechanism behind BR-203 ("notify only after
 * the change is durable"). Because
 * {@link org.linkweave.api.collection.events.CollectionEventPublisher} observes
 * it {@code during = AFTER_SUCCESS}, the container will not deliver it at all if
 * the transaction rolls back — a client can never be told about a write that
 * never landed. Publishing straight to the broadcaster from a write path would
 * be wrong because it would send events on transactions that are rolled back
 *
 * <p>Kept out of the {@code json} package on purpose: this never crosses the
 * wire. It carries entity IDs; the resource layer maps it to
 * {@link org.linkweave.api.collection.events.json.CollectionEventJson}.
 */
public record CollectionChanged(
    @NonNull ID<Collection> collectionId,
    // Null when the change covers several bookmarks at once (a batch move,
    // delete or tag edit): the client re-reads the whole collection anyway
    // (BR-202), so naming one of them would be arbitrary — and one event per
    // operation keeps a 500-bookmark batch from becoming 500 frames.
    @Nullable ID<Bookmark> bookmarkId,
    @NonNull ChangeKind kind,
    // Not an entity id — the originating browser tab's own UUID (BR-205). So the tab does not need to apply the event
    @IgnoreForIdClassTest @Nullable String originClientId,
    @Nullable String actorName
) {
}
