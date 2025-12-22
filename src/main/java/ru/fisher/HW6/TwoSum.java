package ru.fisher.HW6;

public class TwoSum {

    public static int[] twoSum(int[] nums, int target) {
        int[] result = new int[2];
        for (int i = 0; i < nums.length; i++) {
            int x = nums[i];
            for (int j = 0; j < nums.length; j++) {
                if (x + nums[j] == target) {
                    result[0] = x;
                    result[1] = nums[j];
                    return result;
                }
            }
        }
        return result;
    }
}
