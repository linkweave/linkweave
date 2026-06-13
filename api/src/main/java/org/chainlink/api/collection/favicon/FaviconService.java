package org.chainlink.api.collection.favicon;

import java.net.URL;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.chainlink.api.bookmark.Bookmark;
import org.chainlink.api.bookmark.BookmarkRepo;
import org.chainlink.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaviconService {

    private final BookmarkRepo bookmarkRepo;
    private final FaviconCacheService cache;
    private final FaviconFetcherService fetcher;

    public @NonNull Optional<FaviconCacheService.CachedFavicon> getFavicon(@NonNull ID<Bookmark> bookmarkId) {
        // Scalar URL projection (not the entity): one favicon request per
        // visible bookmark, and concurrent entity loads race in Hibernate's
        // shared UTC calendar (HHH-20355, see BookmarkRepo#findUrlById).
        URL url = bookmarkRepo.findUrlById(bookmarkId)
            .orElseGet(() -> bookmarkRepo.getById(bookmarkId).getUrl()); // throws the canonical not-found
        String origin = FaviconFetcherService.canonicalOrigin(url);

        Optional<FaviconCacheService.CachedFavicon> cached = cache.get(origin);
        if (cached.isPresent()) {
            return cached.get().negative() ? Optional.empty() : cached;
        }

        Optional<FaviconFetcherService.FetchedFavicon> fetched = fetcher.fetchFor(url);
        if (fetched.isEmpty()) {
            cache.putNegative(origin);
            return Optional.empty();
        }
        cache.putSuccess(origin, fetched.get().bytes(), fetched.get().contentType());
        return Optional.of(new FaviconCacheService.CachedFavicon(
            fetched.get().bytes(),
            fetched.get().contentType(),
            false
        ));
    }
}
