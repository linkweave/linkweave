package org.linkweave.api.shared.sortorder;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;

import org.jspecify.annotations.NonNull;

/**
 * Shared placement step for a drop at an explicit position (UC-102 folders,
 * UC-103 bookmarks). The services keep entity-specific loading and anchor
 * validation; the numbering decision lives here so both orderings can never
 * drift apart.
 */
public final class SortOrderPlacement {

    private SortOrderPlacement() {
    }

    /**
     * Assigns {@code moved} the sort order for slot {@code insertIndex} among
     * {@code siblings}: usually a midpoint between the two neighbors; when their
     * gap is exhausted, the whole sibling group is renumbered in steps of
     * {@link SparseSortOrder#STEP}. Every modified entity is handed to
     * {@code persist}.
     *
     * @param siblings the sibling group in manual order, without {@code moved}; mutable
     */
    public static <T extends HasSortOrder> void placeAt(
        @NonNull List<T> siblings,
        @NonNull T moved,
        int insertIndex,
        @NonNull Consumer<T> persist
    ) {
        Long prevSortOrderVal = insertIndex > 0 ? siblings.get(insertIndex - 1).getSortOrder() : null;
        Long nextSortOrderVal = insertIndex < siblings.size() ? siblings.get(insertIndex).getSortOrder() : null;

        OptionalLong slot = SparseSortOrder.between(prevSortOrderVal, nextSortOrderVal);
        if (slot.isPresent()) {
            moved.setSortOrder(slot.getAsLong());
            persist.accept(moved);
            return;
        }

        siblings.add(insertIndex, moved);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setSortOrder(SparseSortOrder.renumbered(i));
            persist.accept(siblings.get(i));
        }
    }
}
