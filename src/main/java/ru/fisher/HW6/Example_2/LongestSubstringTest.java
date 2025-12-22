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

}
