package org.linkweave.api.shared.metrics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.linkweave.api.bookmark.BookmarkRepo;
import org.linkweave.api.collection.CollectionAccessRepo;
import org.linkweave.api.collection.CollectionRepo;
import org.linkweave.api.shared.config.ConfigService;
import org.linkweave.infrastructure.stereotypes.Service;

@Service
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class ApplicationMetricsService {

    private final MeterRegistry meterRegistry;
    private final BookmarkRepo bookmarkRepo;
    private final CollectionRepo collectionRepo;
    private final CollectionAccessRepo collectionAccessRepo;

    private final AtomicLong collectionsTotal = new AtomicLong(0);
    private final AtomicLong collectionsShared = new AtomicLong(0);
    private MultiGauge bookmarksPerCollection;

    @PostConstruct
    void registerMetrics() {
        Gauge.builder("chainlink.collections.total", collectionsTotal, AtomicLong::get)
            .description("Total number of collections")
            .register(meterRegistry);

        Gauge.builder("chainlink.collections.shared", collectionsShared, AtomicLong::get)
            .description("Number of collections shared with at least one other user")
            .register(meterRegistry);

        bookmarksPerCollection = MultiGauge.builder("chainlink.bookmarks.total")
            .description("Total bookmarks per collection")
            .register(meterRegistry);
    }

    @Scheduled(
        every = "{chainlink.metrics.refresh.every:30m}",
        skipExecutionIf = DisabledPredicate.class,
        identity = "metrics-refresh"
    )
    public void refreshMetrics() {
        collectionsTotal.set(collectionRepo.countAll());
        collectionsShared.set(collectionAccessRepo.countSharedCollections());

        Map<UUID, Long> counts = bookmarkRepo.countByCollectionGrouped();
        var rows = counts.entrySet().stream()
            .map(e -> MultiGauge.Row.of(Tags.of("collection_id", e.getKey().toString()), e.getValue()))
            .toList();
        bookmarksPerCollection.register(rows, true);

        LOG.debug("Metrics refreshed: {} collections, {} shared, {} bookmark-collection rows",
            collectionsTotal.get(), collectionsShared.get(), rows.size());
    }

    @ApplicationScoped
    @RequiredArgsConstructor
    public static class DisabledPredicate implements Scheduled.SkipPredicate {

        private final ConfigService configService;

        @Override
        public boolean test(io.quarkus.scheduler.ScheduledExecution execution) {
            return !configService.isMetricsRefreshEnabled();
        }
    }
}
