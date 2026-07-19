package org.linkweave.api.shared.sortorder;

import java.util.OptionalLong;

import org.jspecify.annotations.Nullable;

/**
 * Sparse sort-order numbering shared by manually orderable entities
 * (folders — UC-102, bookmarks — UC-103). Values are spaced {@link #STEP}
 * apart so a drop between two neighbors can usually be persisted by writing a
 * single midpoint value; only when a gap is exhausted does the whole sibling
 * group need renumbering.
 */
public final class SparseSortOrder {

    /** Gap between two adjacent entries after an append or a renumbering. */
    public static final long STEP = 1000L;

    private SparseSortOrder() {
    }

    /**
     * @param currentMax the highest sort order among the existing siblings, or {@code null} if there are none
     * @return the sort order that appends a new entry after all existing siblings (BR-190)
     */
    public static long afterMax(@Nullable Long currentMax) {
        return currentMax == null ? STEP : currentMax + STEP;
    }

    /**
     * <p>Only relative order carries meaning, so the head slot ({@code previous == null})
     * deliberately walks below {@link #STEP} and may reach zero or go negative — dropping a
     * folder above a group that starts at {@code STEP} yields 0 on the first drop, -1000 on
     * the next, and so on. Sorting is unaffected and the head never needs a renumbering.
     *
     * @param previous sort order of the neighbor before the insertion point, or {@code null} when inserting first
     * @param next sort order of the neighbor after the insertion point, or {@code null} when inserting last
     * @return the sort order for the insertion point — always strictly between the neighbors —
     *     or empty when the gap between them is exhausted and the sibling group must be renumbered
     */
    public static OptionalLong between(@Nullable Long previous, @Nullable Long next) {
        if (previous == null && next == null) {
            return OptionalLong.of(STEP);
        }
        if (previous == null) {
            return OptionalLong.of(next - STEP);
        }
        if (next == null) {
            return OptionalLong.of(previous + STEP);
        }
        long gap = next - previous;
        return gap >= 2 ? OptionalLong.of(previous + gap / 2) : OptionalLong.empty();
    }

    /**
     * @return the sort order for slot {@code index} (0-based) after a full renumbering of a sibling group
     */
    public static long renumbered(int index) {
        return (index + 1) * STEP;
    }
}
