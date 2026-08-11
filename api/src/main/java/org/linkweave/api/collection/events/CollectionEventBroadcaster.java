package org.linkweave.api.collection.events;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * In-process fan-out of live collection updates (UC-104), one hot stream per
 * collection.
 *
 * <p>A {@link BroadcastProcessor} is exactly the semantics BR-204 asks for and
 * nothing more: it drops items when nobody is subscribed and never replays past
 * items to a late subscriber. There is deliberately no queue, no retained
 * history and no {@code Last-Event-ID} replay — a client that missed something
 * recovers by reloading the collection on reconnect, and that choice is what
 * keeps this stateless enough that a reconnecting client does not care which
 * instance it lands on.
 *
 * <p><strong>Single-instance only.</strong> Fan-out is in-process, so this is
 * correct only while the application runs as one instance — which C-003 (single
 * SQLite database), C-011/C-012 (local-disk caches) and the in-process
 * {@code @Scheduled} jobs already require independently. Scaling out means
 * replacing the {@link #publish} body with a shared bu in the backplane;
 * nothing else needs to change shape
 */
@ApplicationScoped
public class CollectionEventBroadcaster {

    private final Map<ID<Collection>, CollectionChangeEventSink> collectionChangeEventSinks = new ConcurrentHashMap<>();

    /**
     * Opens a stream of updates for one specific collection (BR-210 — a subscriber can
     * only ever reach the collection it asked for), optionally excluding the
     * events that one client caused itself.
     *
     * <p>{@code excludeClientId} identifies a <em>browser tab</em>, so the tab
     * that made a change does not act on its own notification (BR-205). It must
     * never be a user id — see {@link CollectionEventJson}. {@code null} on
     * either side passes the event through: an unidentified subscriber wants
     * everything, and a background job's event belongs to no tab at all.
     *
     * <p>The slot is taken on <em>subscription</em> rather than when this method
     * is called, and released when that subscription ends for any reason —
     * completion, failure or cancellation, the last of which is the ordinary
     * "user closed the tab" case. Acquiring it here instead would be asymmetric:
     * a {@link Multi} that is never subscribed would pin its sink forever, and
     * one that is subscribed twice would release it twice, dropping a sink whose
     * remaining subscriber is then attached to an orphaned processor and silently
     * never hears anything again.
     */
    @NonNull
    public Multi<CollectionEventJson> subscribe(
        @NonNull ID<Collection> collectionId,
        // Not an entity id — a browser tab's own UUID, opaque to the server.
        @IgnoreForIdClassTest @Nullable String excludeClientId
    ) {
        return Multi.createFrom().<CollectionEventJson>deferred(() -> {
            CollectionChangeEventSink sink = acquire(collectionId);
            return sink.processor.onTermination()
                .invoke(() -> this.release(collectionId, sink));
        }).filter(event -> excludeClientId == null || !excludeClientId.equals(event.getOriginClientId()));
    }

    /**
     * Emits to whoever is listening to the event's collection right now. A no-op
     * when nobody is — notifications are not queued for absent clients (BR-204).
     */
    public void publish(@NonNull CollectionEventJson event) {
        CollectionChangeEventSink sink = collectionChangeEventSinks.get(event.getCollectionId());
        if (sink == null) {
            return;
        }
        sink.processor.onNext(event);
    }

    @NonNull
    private CollectionChangeEventSink acquire(@NonNull ID<Collection> collectionId) {
        // compute() rather than computeIfAbsent() + increment: the count has to
        // move under the map's lock, or a release() racing between the two would
        // drop a sink that just gained a subscriber.
        return collectionChangeEventSinks.compute(collectionId, (_, existing) -> {
            CollectionChangeEventSink target = existing != null ? existing : new CollectionChangeEventSink();
            target.subscribers.incrementAndGet();
            return target;
        });
    }

    private void release(@NonNull ID<Collection> collectionId, @NonNull CollectionChangeEventSink sink) {
        collectionChangeEventSinks.compute(collectionId, (id, existing) -> {
            if (existing != sink) {
                return existing; // already replaced by a newer sink — leave it alone
            }
            return existing.subscribers.decrementAndGet() <= 0 ? null : existing;
        });
    }

    /** Number of collections with at least one live subscriber. user for testing. */
    int openCollectionCount() {
        return collectionChangeEventSinks.size();
    }

    private static final class CollectionChangeEventSink {
        private final BroadcastProcessor<CollectionEventJson> processor = BroadcastProcessor.create();
        private final AtomicInteger subscribers = new AtomicInteger();
    }
}
