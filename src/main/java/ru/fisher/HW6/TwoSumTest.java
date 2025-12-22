package ru.fisher.HW6;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class TwoSumTest {

    @Test
    void simpleTest() {
        int[] array = new int[]{5, 8};
        int target = 13;
        assertArrayEquals(new int[]{0, 1}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumWithThreeElementsTest() {
        int[] array = new int[]{1, 12, 5};
        int target = 6;
        assertArrayEquals(new int[]{0, 2}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumWithFourElementsTest() {
        int[] array = new int[]{2, 7, 11, 15};
        int target = 9;
        assertArrayEquals(new int[]{0, 1}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumWithThreeElementsFromExampleTest() {
        int[] array = new int[]{3, 2, 4};
        int target = 6;
        assertArrayEquals(new int[]{1, 2}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumWithTwoSameElementsTest() {
        int[] array = new int[]{3, 3};
        int target = 6;
        assertArrayEquals(new int[]{0, 1}, TwoSum.twoSum(array, target));
    }

    @Test
    void twoSumTest() {
        int[] array = new int[]{2, 5, 5, 11};
        int target = 10;
        assertArrayEquals(new int[]{1, 2}, TwoSum.twoSum(array, target));
    }



}
