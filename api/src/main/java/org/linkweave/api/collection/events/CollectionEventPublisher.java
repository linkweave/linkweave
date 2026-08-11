package org.linkweave.api.collection.events;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import lombok.RequiredArgsConstructor;
import org.linkweave.api.collection.events.json.CollectionEventJson;
import org.jspecify.annotations.NonNull;

/**
 * Turns a committed {@link CollectionChanged} into a wire notification.
 *
 * <p>The {@code AFTER_SUCCESS} phase is the point of this class: CDI delivers
 * the observer only once the firing transaction has committed, so BR-203
 * ("notify only after the change is durable") holds by construction rather than
 * by everyone remembering to publish in the right place. A rolled-back write
 * silently notifies nobody, which is the correct outcome and the one that is
 * easy to get wrong by hand.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class CollectionEventPublisher {

    private final CollectionEventBroadcaster broadcaster;

    void onCommitted(@Observes(during = TransactionPhase.AFTER_SUCCESS) @NonNull CollectionChanged changed) {
        broadcaster.publish(toJson(changed));
    }

    @NonNull
    private static CollectionEventJson toJson(@NonNull CollectionChanged changed) {
        return new CollectionEventJson(
            changed.collectionId(),
            changed.bookmarkId(),
            changed.kind(),
            changed.originClientId(),
            changed.actorName()
        );
    }
}
