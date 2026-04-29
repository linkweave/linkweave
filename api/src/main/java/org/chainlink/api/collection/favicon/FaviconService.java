package org.chainlink.api.collection.favicon;

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
        Bookmark bookmark = bookmarkRepo.getById(bookmarkId);
        String origin = FaviconFetcherService.canonicalOrigin(bookmark.getUrl());

        Optional<FaviconCacheService.CachedFavicon> cached = cache.get(origin);
        if (cached.isPresent()) {
            return cached.get().negative() ? Optional.empty() : cached;
        }

        Optional<FaviconFetcherService.FetchedFavicon> fetched = fetcher.fetchFor(bookmark.getUrl());
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
