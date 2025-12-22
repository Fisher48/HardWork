package ru.fisher.HW6;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TwoSumTest {

    @Test
    void simpleTest() {
        int[] array = new int[]{5, 8};
        int target = 13;
        assertArrayEquals(new int[]{5, 8}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumWithThreeElementsTest() {
        int[] array = new int[]{1, 12, 5};
        int target = 6;
        assertArrayEquals(new int[]{1, 5}, TwoSum.twoSum(array, target));
    }
}
