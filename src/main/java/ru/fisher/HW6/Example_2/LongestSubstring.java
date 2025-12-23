package ru.fisher.HW6.Example_2;

import java.util.HashSet;
import java.util.Set;

public class LongestSubstring {

    public static int lengthOfLongestSubstring(String s) {
        int maxLen = 0;
        for (int i = 0; i < s.length(); i++) {
            Set<Character> noRepeatSet = new HashSet<>();
            for (int j = i; j < s.length(); j++) {
                if (noRepeatSet.contains(s.charAt(j))) {
                    break;
                }
                noRepeatSet.add(s.charAt(j));
                maxLen = Math.max(maxLen, j - i + 1);
            }
        }

        return maxLen;
    }

}
