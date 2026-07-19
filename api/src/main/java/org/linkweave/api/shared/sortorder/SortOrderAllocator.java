package org.linkweave.api.shared.sortorder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Hands out appended sort orders for a batch that creates many entities at once
 * (bookmark imports). The highest existing sort order is queried once per sibling
 * group and then carried forward in memory, so importing a browser export with
 * hundreds of folders no longer costs one aggregate query per folder.
 *
 * <p>Only valid within a single unit of work: it assumes nothing else appends to
 * the same groups concurrently.
 *
 * @param <K> identifies a sibling group, e.g. the parent folder ID ({@code null} = root level)
 */
public final class SortOrderAllocator<K> {

    private final Function<@Nullable K, @Nullable Long> currentMaxLookup;
    private final Map<K, Long> lastAssigned = new HashMap<>();

    public SortOrderAllocator(@NonNull Function<@Nullable K, @Nullable Long> currentMaxLookup) {
        this.currentMaxLookup = currentMaxLookup;
    }

    /**
     * @return the sort order that appends a new entry after everything already in {@code group}
     */
    public long next(@Nullable K group) {
        Long currentMax = lastAssigned.containsKey(group)
            ? lastAssigned.get(group)
            : currentMaxLookup.apply(group);
        long assigned = SparseSortOrder.afterMax(currentMax);
        lastAssigned.put(group, assigned);
        return assigned;
    }
}
