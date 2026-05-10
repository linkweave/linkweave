package org.chainlink.api.bookmark;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.json.TagJson;
import org.chainlink.api.bookmark.json.TagListJson;
import org.chainlink.api.bookmark.json.TagSaveJson;
import org.chainlink.api.collection.Collection;
import org.chainlink.api.shared.auth.AuthorizationService;
import io.smallrye.faulttolerance.api.RateLimit;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/tags")
public class TagResource {

    private final TagService tagService;
    private final AuthorizationService authorizationService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public TagListJson list(@QueryParam("collectionId") @NotNull @NonNull ID<Collection> collectionId) {
        authorizationService.requireCollectionAccess(collectionId);
        return new TagListJson(
            tagService.findByCollection(collectionId).stream()
                .map(TagMapper::toJson)
                .toList()
        );
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public TagJson create(@NotNull @Valid @NonNull TagSaveJson json) {
        authorizationService.requireCollectionAccess(json.getCollectionId());
        Tag tag = tagService.createTag(json);
        return TagMapper.toJson(tag);
    }

    @PUT
    @Path("/{tagId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public TagJson update(
        @PathParam("tagId") @NotNull @NonNull ID<Tag> tagId,
        @NotNull @Valid @NonNull TagSaveJson json
    ) {

        authorizationService.requireCollectionAccess(json.getCollectionId());
        Tag tag = tagService.getTag(tagId);
        authorizationService.requireCollectionAccess(tag.getCollection().getId());
        Tag updated = tagService.updateTag(tag, json);
        return TagMapper.toJson(updated);
    }

    @DELETE
    @Path("/{tagId}")
    @Authenticated
    public void delete(
        @PathParam("tagId") @NotNull @NonNull ID<Tag> tagId
    ) {
        Tag tag = tagService.getTag(tagId);
        authorizationService.requireCollectionAccess(tag.getCollection().getId());
        tagService.removeTag(tagId);
    }
}
