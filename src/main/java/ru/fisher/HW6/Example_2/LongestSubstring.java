package ru.fisher.HW6.Example_2;

import java.util.HashMap;

public class LongestSubstring {

    public static int lengthOfLongestSubstring(String s) {
        int maxLen = 0;
        for (int i = 0; i < s.length(); i++) {
            for (int j = i; j < s.length() + 1; j++) {
                String checkingSubstring = s.substring(i, j);
                if (checkRepeatElements(checkingSubstring)) {
                    maxLen = Math.max(maxLen, checkingSubstring.length());
                }
            }
        }

        return maxLen;
    }

    public static boolean checkRepeatElements(String s) {
        HashMap<Character, Integer> noRepeatMap = new HashMap<>();
        for (int i = 0; i < s.length(); i++) {
            if (noRepeatMap.containsKey(s.charAt(i))) {
                return false;
            }
           noRepeatMap.put(s.charAt(i), i);
        }
        return true;
    }

}
