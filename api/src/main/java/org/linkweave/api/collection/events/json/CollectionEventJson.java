package org.linkweave.api.collection.events.json;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.collection.Collection;
import org.linkweave.api.collection.events.ChangeKind;
import org.linkweave.api.types.id.ID;
import org.linkweave.infrastructure.stereotypes.JaxDTO;
import org.linkweave.infrastructure.types.IgnoreForIdClassTest;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * One notification on a collection's live-update channel (UC-104).
 *
 * <p><strong>This is not the changed data.</strong> It identifies <em>what</em>
 * changed so the client can reload through the normal read path (BR-202) — which
 * keeps a single authoritative representation of a collection and stops a
 * client's view diverging when a notification is missed or arrives out of date.
 *
 * <p>{@code originClientId} and {@code actorName} look similar and are not
 * interchangeable:
 * <ul>
 *   <li>{@code originClientId} identifies a <strong>browser tab</strong>, and exists
 *       solely so the tab that caused the change ignores its own event (BR-205).
 *       It must never be a user id: a user with the same collection open twice
 *       would then have their second tab discard updates it genuinely needs.</li>
 *   <li>{@code actorName} identifies a <strong>person</strong>, and exists solely
 *       for the attribution shown in the indicator (BR-209).</li>
 * </ul>
 *
 * <p>Both are {@code null} when a background job caused the change — there is no
 * originating tab to filter and no person to attribute it to (A1).
 */
@Value
@AllArgsConstructor
@JaxDTO
public class CollectionEventJson {

    @Schema(required = true)
    ID<Collection> collectionId;

    /**
     * Which bookmark the notification is about — {@code null} only for
     * {@link ChangeKind#HEARTBEAT}, which announces no change at all.
     */
    @Nullable
    @Schema(required = false)
    ID<Bookmark> bookmarkId;

    @Schema(required = true)
    ChangeKind kind;

    /**
     * Not an entity id — a browser tab's own {@code crypto.randomUUID()}, opaque
     * to the server and never looked up. Hence {@link IgnoreForIdClassTest}.
     */
    @Nullable
    @IgnoreForIdClassTest
    @Schema(required = false)
    String originClientId;

    @Nullable
    @Schema(required = false)
    String actorName;

    /**
     * A keep-alive frame for this collection's stream — no bookmark, no origin
     * tab, no actor. See {@link ChangeKind#HEARTBEAT}.
     */
    public static CollectionEventJson heartbeat(ID<Collection> collectionId) {
        return new CollectionEventJson(collectionId, null, ChangeKind.HEARTBEAT, null, null);
    }
}
