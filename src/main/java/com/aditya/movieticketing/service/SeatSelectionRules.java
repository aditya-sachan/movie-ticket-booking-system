package com.aditya.movieticketing.service;

import com.aditya.movieticketing.exception.InvalidSeatSelectionException;

import java.util.Map;

/**
 * Pure seat-selection rules — no Spring, no DB, fully unit-testable.
 *
 * <ul>
 *   <li><b>Max per booking</b>: a single booking may hold at most N seats.</li>
 *   <li><b>No orphan seat</b>: a selection must not leave an available seat with an occupied seat
 *       on both immediate sides within its row (a lone isolated gap). A seat against the row
 *       boundary is not an orphan — the wall is not a neighbour.</li>
 * </ul>
 */
public final class SeatSelectionRules {

    private SeatSelectionRules() {
    }

    public static void validateMaxSeats(int requested, int max) {
        if (requested > max) {
            throw new InvalidSeatSelectionException("A single booking may hold at most " + max + " seats");
        }
    }

    /**
     * @param occupiedByNumber for one row: seat number → whether it is occupied after the hold.
     *                         A missing number means no such seat exists (row boundary/gap).
     * @return true if any available seat is flanked by occupied seats on both sides.
     */
    public static boolean hasOrphanSeat(Map<Integer, Boolean> occupiedByNumber) {
        for (Map.Entry<Integer, Boolean> entry : occupiedByNumber.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                continue; // occupied seat cannot itself be an orphan
            }
            int number = entry.getKey();
            boolean leftOccupied = Boolean.TRUE.equals(occupiedByNumber.get(number - 1));
            boolean rightOccupied = Boolean.TRUE.equals(occupiedByNumber.get(number + 1));
            if (leftOccupied && rightOccupied) {
                return true;
            }
        }
        return false;
    }
}
