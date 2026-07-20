package com.aditya.movieticketing.service;

import com.aditya.movieticketing.exception.InvalidSeatSelectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Table-driven tests for the pure seat-selection rules.
 */
class SeatSelectionRulesTest {

    @Test
    @DisplayName("max seats: at the cap passes, over the cap is rejected")
    void maxSeats() {
        assertThatCode(() -> SeatSelectionRules.validateMaxSeats(10, 10)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SeatSelectionRules.validateMaxSeats(11, 10))
                .isInstanceOf(InvalidSeatSelectionException.class);
    }

    /** true = occupied, false = available; key = seat number in the row. */
    private static Map<Integer, Boolean> row(Object... pairs) {
        Map<Integer, Boolean> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((Integer) pairs[i], (Boolean) pairs[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("an available seat flanked by occupied seats on both sides is an orphan")
    void orphanBetweenTwoOccupied() {
        // [1 taken][2 free][3 taken]
        assertThat(SeatSelectionRules.hasOrphanSeat(row(1, true, 2, false, 3, true))).isTrue();
    }

    @Test
    @DisplayName("a gap of two seats is not an orphan")
    void gapOfTwoIsFine() {
        // [1 taken][2 free][3 free][4 taken]
        assertThat(SeatSelectionRules.hasOrphanSeat(row(1, true, 2, false, 3, false, 4, true))).isFalse();
    }

    @Test
    @DisplayName("a single seat against the row boundary is not an orphan")
    void edgeSingleIsFine() {
        // [1 free][2 taken] — seat 1 has a wall on the left, so it is not orphaned
        assertThat(SeatSelectionRules.hasOrphanSeat(row(1, false, 2, true))).isFalse();
        // [1 taken][2 free] — seat 2 has a wall on the right
        assertThat(SeatSelectionRules.hasOrphanSeat(row(1, true, 2, false))).isFalse();
    }

    @Test
    @DisplayName("a fully open row has no orphan")
    void openRowIsFine() {
        assertThat(SeatSelectionRules.hasOrphanSeat(row(1, false, 2, false, 3, false))).isFalse();
    }

    @Test
    @DisplayName("detects an orphan created deeper in a longer row")
    void orphanInLongerRow() {
        // [1 free][2 free][3 taken][4 free][5 taken] -> seat 4 is orphaned
        assertThat(SeatSelectionRules.hasOrphanSeat(
                row(1, false, 2, false, 3, true, 4, false, 5, true))).isTrue();
    }
}
