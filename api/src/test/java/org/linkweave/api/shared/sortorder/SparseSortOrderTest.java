package org.linkweave.api.shared.sortorder;

import java.util.OptionalLong;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SparseSortOrderTest {

    @Test
    void shouldStartAtStep_whenGroupIsEmpty() {
        Assertions.assertThat(SparseSortOrder.afterMax(null)).isEqualTo(SparseSortOrder.STEP);
    }

    @Test
    void shouldAppendOneStepAfterMax() {
        Assertions.assertThat(SparseSortOrder.afterMax(3000L)).isEqualTo(3000L + SparseSortOrder.STEP);
    }

    @Test
    void shouldUseStep_whenInsertingIntoEmptyGroup() {
        Assertions.assertThat(SparseSortOrder.between(null, null)).isEqualTo(OptionalLong.of(SparseSortOrder.STEP));
    }

    @Test
    void shouldInsertOneStepBeforeFirst_whenNoPreviousNeighbor() {
        Assertions.assertThat(SparseSortOrder.between(null, 1000L)).isEqualTo(OptionalLong.of(0L));
    }

    /**
     * The head slot walks below STEP on repeated drops to the top; only the relative
     * order matters, so zero and negative values are correct results rather than a
     * broken invariant. What must hold is strict ordering against the anchor — a tie
     * would let the creation-timestamp tiebreak invert the drop.
     */
    @Test
    void shouldStayStrictlyBelowNext_whenFirstSlotFallsBelowStep() {
        Assertions.assertThat(SparseSortOrder.between(null, 10L)).isEqualTo(OptionalLong.of(-990L));
        Assertions.assertThat(SparseSortOrder.between(null, 0L)).isEqualTo(OptionalLong.of(-1000L));
        Assertions.assertThat(SparseSortOrder.between(null, -1000L)).isEqualTo(OptionalLong.of(-2000L));
    }

    @Test
    void shouldInsertOneStepAfterLast_whenNoNextNeighbor() {
        Assertions.assertThat(SparseSortOrder.between(2000L, null)).isEqualTo(OptionalLong.of(3000L));
    }

    @Test
    void shouldUseMidpoint_whenGapIsLargeEnough() {
        Assertions.assertThat(SparseSortOrder.between(1000L, 2000L)).isEqualTo(OptionalLong.of(1500L));
    }

    @Test
    void shouldUseMidpoint_whenGapIsExactlyTwo() {
        Assertions.assertThat(SparseSortOrder.between(10L, 12L)).isEqualTo(OptionalLong.of(11L));
    }

    @Test
    void shouldSignalRenumbering_whenGapIsExhausted() {
        Assertions.assertThat(SparseSortOrder.between(10L, 11L)).isEmpty();
        Assertions.assertThat(SparseSortOrder.between(10L, 10L)).isEmpty();
    }

    @Test
    void shouldRenumberInSteps() {
        Assertions.assertThat(SparseSortOrder.renumbered(0)).isEqualTo(SparseSortOrder.STEP);
        Assertions.assertThat(SparseSortOrder.renumbered(2)).isEqualTo(3 * SparseSortOrder.STEP);
    }
}
