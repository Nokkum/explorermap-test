package com.explorermap.table;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableCostsTest {

    @Test
    void mapWithoutExpansionsCostsVanillasOneItem() {
        assertEquals(1, TableCosts.requiredCount(0, 2, 64));
    }

    @Test
    void oneExtraItemForEveryGroupOfTilesRoundingUp() {
        assertEquals(2, TableCosts.requiredCount(1, 2, 64));
        assertEquals(2, TableCosts.requiredCount(2, 2, 64));
        assertEquals(3, TableCosts.requiredCount(3, 2, 64));
        assertEquals(6, TableCosts.requiredCount(10, 2, 64));
        assertEquals(4, TableCosts.requiredCount(10, 4, 64));
    }

    @Test
    void zeroTurnsScalingOff() {
        assertEquals(1, TableCosts.requiredCount(40, 0, 64));
    }

    @Test
    void neverMoreThanOneStack() {
        assertEquals(64, TableCosts.requiredCount(256, 2, 64));
        assertEquals(16, TableCosts.requiredCount(256, 2, 16));
    }
}
