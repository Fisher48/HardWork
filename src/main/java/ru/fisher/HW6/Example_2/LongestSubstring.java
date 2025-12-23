package ru.fisher.HW6.Example_2;

import java.util.HashSet;
import java.util.Set;

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
        Set<Character> noRepeatSet = new HashSet<>();
        for (int i = 0; i < s.length(); i++) {
            if (noRepeatSet.contains(s.charAt(i))) {
                return false;
            }
           noRepeatSet.add(s.charAt(i));
        }
        return true;
    }

}
