package org.linkweave.api.shared.auth;

import ch.dvbern.dvbstarter.types.id.ID;
import org.linkweave.api.collection.Collection;
import org.jspecify.annotations.NonNull;

/**
 * Marker for entities that belong to exactly one {@link Collection}, exposing
 * the owning collection's ID without the resource layer needing to traverse
 * the entity graph (which would breach the layering rule "no entity getters
 * called from the resource layer"). Lets the auth boundary be expressed as
 * {@code authorizationService.requireAccessTo(entity)} from a resource.
 */
public interface BelongsToCollection {
    @NonNull
    ID<Collection> getCollectionId();
}
