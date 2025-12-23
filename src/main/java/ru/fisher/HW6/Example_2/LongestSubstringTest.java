package ru.fisher.HW6.Example_2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LongestSubstringTest {

    @Test
    void simpleTest() {
        String s = "a";
        assertEquals(1,LongestSubstring.lengthOfLongestSubstring(s));
    }

    @Test
    void oneSubstringTest() {
        String s = "abc";
        assertEquals(3, LongestSubstring.lengthOfLongestSubstring(s));
    }

    @Test
    void oneSubstringFromThreeCharsTest() {
        String s = "abcabcbb";
        assertEquals(3, LongestSubstring.lengthOfLongestSubstring(s));
    }

    @Test
    void oneSubstringFromOneSameCharsTest() {
        String s = "bbbbb";
        assertEquals(1, LongestSubstring.lengthOfLongestSubstring(s));
    }

    @Test
    void oneSubstringFromThreeCharsWithPartOfSameCharsTest() {
        String s = "pwwkew";
        assertEquals(3, LongestSubstring.lengthOfLongestSubstring(s));
    }

}
