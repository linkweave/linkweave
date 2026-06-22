package org.linkweave.api.collection.favicon;

import java.net.URL;
import java.util.Optional;

import ch.dvbern.dvbstarter.types.id.ID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.bookmark.Bookmark;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.infrastructure.stereotypes.Service;
import org.jspecify.annotations.NonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaviconService {

    private final BookmarkRepo bookmarkRepo;
    private final FaviconCacheService cache;
    private final FaviconFetcherService fetcher;
    private final BackendFetchPolicy fetchPolicy;

    public @NonNull Optional<FaviconCacheService.CachedFavicon> getFavicon(@NonNull ID<Bookmark> bookmarkId) {
        // Scalar projection (not the entity): one favicon request per visible
        // bookmark, and concurrent entity loads race in Hibernate's shared UTC
        // calendar (HHH-20355, see BookmarkRepo#findUrlById). The projection also
        // carries the collection's allowlist so we can short-circuit below.
        BookmarkRepo.UrlFetchContext ctx = bookmarkRepo.findUrlFetchContextById(bookmarkId)
            .orElseGet(() -> new BookmarkRepo.UrlFetchContext(
                bookmarkRepo.getById(bookmarkId).getUrl(), null)); // throws the canonical not-found
        URL url = ctx.url();

        // Hosts the backend must not reach (operator denylist, or this
        // collection's browser allowlist — loaded directly by the user's
        // browser): never fetch or cache server-side, since it would only ever
        // produce negative entries.
        if (fetchPolicy.blocks(url.getHost(), ctx.collectionBrowserAllowlist())) {
            return Optional.empty();
        }

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
