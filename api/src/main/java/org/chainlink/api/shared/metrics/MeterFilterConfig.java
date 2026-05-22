package org.chainlink.api.shared.metrics;

import java.util.List;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

/**
 * Normalizes SmallRye Fault Tolerance metrics that register ft.ratelimit.calls with
 * two incompatible tag sets: {bulkheadResult, method} and {method, rateLimitResult}.
 * This is a known SmallRye FT 6.x bug. We rename bulkheadResult → rateLimitResult
 * so all registrations use a consistent tag schema.
 *
 * In tests, FT Micrometer metrics are disabled entirely via
 * smallrye.faulttolerance.micrometer.disabled=true (test profile) so this filter
 * only applies in dev/prod where the bug still surfaces.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class MeterFilterConfig {

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    void onStart(@Observes StartupEvent event) {
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.@NonNull Id meterSpec) {
                if (!"ft.ratelimit.calls".equals(meterSpec.getName())) {
                    return meterSpec;
                }
                List<Tag> normalized = meterSpec.getTags().stream()
                    .map(t -> "bulkheadResult".equals(t.getKey())
                        ? Tag.of("rateLimitResult", t.getValue())
                        : t)
                    .toList();
                return meterSpec.replaceTags(normalized);
            }
        });
    }
}
