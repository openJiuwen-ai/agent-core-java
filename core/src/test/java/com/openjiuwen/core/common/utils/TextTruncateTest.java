/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextTruncateTest {

    private static boolean hasLoneSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)
                    && (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1)))) {
                return true;
            }
            if (Character.isLowSurrogate(c)
                    && (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1)))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void safePrefixDoesNotSplitEmoji() {
        // 🔴 is U+1F534 → two UTF-16 units; place it across the maxUnits boundary.
        String text = "a".repeat(1999) + "🔴" + "b".repeat(10);
        String truncated = TextTruncate.safePrefix(text, 2000);
        assertFalse(hasLoneSurrogate(truncated));
        assertEquals(1999, truncated.length());
    }

    @Test
    void safeSuffixDoesNotSplitEmoji() {
        String text = "a".repeat(10) + "🔴" + "b".repeat(1999);
        String truncated = TextTruncate.safeSuffix(text, 2000);
        assertFalse(hasLoneSurrogate(truncated));
        assertEquals(1999, truncated.length());
    }

    @Test
    void safeSubstringKeepsWholePair() {
        String text = "x🔴y";
        // high surrogate of 🔴 is at index 1; cutting [0,2) must drop the pair entirely
        String sliced = TextTruncate.safeSubstring(text, 0, 2);
        assertFalse(hasLoneSurrogate(sliced));
        assertEquals("x", sliced);
    }

    @Test
    void safePrefixAndSuffixComposeWithoutLoneSurrogates() {
        // Head/tail truncation pattern used by compressors/offloaders.
        String text = "a".repeat(50) + "🔴" + "b".repeat(50) + "🟢" + "c".repeat(50);
        String head = TextTruncate.safePrefix(text, 52);
        String tail = TextTruncate.safeSuffix(text, 52);
        assertFalse(hasLoneSurrogate(head));
        assertFalse(hasLoneSurrogate(tail));
        assertFalse(hasLoneSurrogate(head + "\n...\n" + tail));
    }
}
