package ru.fisher.HW6.Example_2;

import java.util.HashMap;
import java.util.Map;

public class LongestSubstring {

    public static int lengthOfLongestSubstring(String s) {
        int maxLen = 0;
        Map<Character, Integer> map = new HashMap<>();
        for (int i = 0; i < s.length(); i++) {
            if (!map.containsKey(s.charAt(i))) {
                map.put(s.charAt(i), map.getOrDefault(s.charAt(i), 0) + 1);
                maxLen++;
            }
        }
        return maxLen;
    }

}
