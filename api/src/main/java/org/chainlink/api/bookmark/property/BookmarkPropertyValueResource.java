package org.chainlink.api.bookmark.property;

import java.time.temporal.ChronoUnit;

import ch.dvbern.dvbstarter.types.id.ID;
import io.quarkus.security.Authenticated;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkMapper;
import org.chainlink.api.bookmark.json.BookmarkJson;
import org.chainlink.api.bookmark.property.json.BookmarkPropertyValueListJson;
import org.chainlink.api.shared.auth.AuthorizationService;
import org.chainlink.api.shared.config.ConfigService;
import org.chainlink.infrastructure.stereotypes.JaxResource;
import org.jspecify.annotations.NonNull;

@RateLimit(value = 120, window = 1, windowUnit = ChronoUnit.MINUTES)
@JaxResource
@RequiredArgsConstructor
@Authenticated
@Path("/bookmarks")
public class BookmarkPropertyValueResource {

    private final BookmarkPropertyValueService propertyValueService;
    private final AuthorizationService authorizationService;
    private final ConfigService configService;

    @PUT
    @Path("/{bookmarkId}/properties")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @NonNull
    @Authenticated
    public BookmarkJson replaceProperties(
        @PathParam("bookmarkId") @NotNull @NonNull ID<Bookmark> bookmarkId,
        @NotNull @Valid @NonNull BookmarkPropertyValueListJson json
    ) {
        if (!configService.isBookmarkPropertiesEnabled()) {
            throw new NotFoundException();
        }
        Bookmark bookmark = propertyValueService.getBookmark(bookmarkId);
        authorizationService.requireAccessTo(bookmark);
        Bookmark updated = propertyValueService.replacePropertyValues(bookmark, json.getPropertyValues());
        return BookmarkMapper.toJsonWithProperties(updated);
    }
}
