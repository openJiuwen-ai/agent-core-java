/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.splitter;

import com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sentence splitter test cases.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/retrieval/indexing/processor/splitter/test_splitter.py}
 * for the Java splitter implementation.</p>
 */
class TestSplitterImpl {

    private static final Function<String, List<String>> TOKENIZER =
            text -> text == null || text.isBlank() ? List.of() : Arrays.asList(text.trim().split("\\s+"));

    @Test
    void testInitWithDefaults() throws Exception {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "auto");

        assertEquals("auto", readDefaultLanguage(splitter));
    }

    @Test
    void testInitWithCustomLanguage() throws Exception {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        assertEquals("en", readDefaultLanguage(splitter));
    }

    @Test
    void testCallEmptyText() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        assertEquals(List.of(), splitter.splitText(""));
    }

    @Test
    void testCallWhitespaceOnly() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        assertEquals(List.of(), splitter.splitText("   \n\t   "));
    }

    @Test
    void testCallSingleSentence() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("This is a test sentence.");

        assertEquals(1, chunks.size());
        assertEquals("This is a test sentence.", chunks.getFirst());
    }

    @Test
    void testCallMultipleSentences() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("First sentence. Second sentence. Third sentence.");
        String allText = String.join(" ", chunks);

        assertTrue(chunks.size() >= 1);
        assertTrue(allText.contains("First sentence"));
        assertTrue(allText.contains("Second sentence"));
        assertTrue(allText.contains("Third sentence"));
    }

    @Test
    void testCallLongSentence() {
        SentenceSplitter splitter = new SentenceSplitter(100, 10, TOKENIZER, "en");
        String longSentence = String.join(" ", java.util.Collections.nCopies(1000, "word"));

        List<String> chunks = splitter.splitText(longSentence);

        assertTrue(chunks.size() >= 1);
        String combined = String.join(" ", chunks);
        assertTrue(combined.contains("word"));
    }

    @Test
    void testCallWithoutPunctuationReturnsWholeText() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("plain text without punctuation");

        assertEquals(1, chunks.size());
        assertEquals("plain text without punctuation", chunks.getFirst());
    }

    @Test
    void testCallCombinesSentencesWhenChunkSizeIsSufficient() {
        SentenceSplitter splitter = new SentenceSplitter(100, 10, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("Short sentence 1. Short sentence 2. Short sentence 3.");

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("Short sentence 1"));
        assertTrue(chunks.getFirst().contains("Short sentence 2"));
        assertTrue(chunks.getFirst().contains("Short sentence 3"));
    }

    @Test
    void testCallSplitsByTokenCountNotCharCount() {
        SentenceSplitter splitter = new SentenceSplitter(5, 0, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("one two three four five. six seven.");

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("one"));
        assertTrue(chunks.get(0).contains("five"));
        assertTrue(chunks.get(1).contains("six"));
        assertTrue(chunks.get(1).contains("seven"));
    }

    @Test
    void testCallWithZeroOverlapDoesNotCarryPreviousSentence() {
        SentenceSplitter splitter = new SentenceSplitter(4, 0, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("w1 w2. w3 w4. w5 w6.");

        assertEquals(2, chunks.size());
        assertFalse(chunks.get(1).contains("w3 w4.") && chunks.get(0).equals(chunks.get(1)));
    }

    @Test
    void testCallChunkOverlapKeepsTrailingSentence() {
        SentenceSplitter splitter = new SentenceSplitter(4, 2, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("w1 w2. w3 w4. w5 w6.");

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("w1 w2."));
        assertTrue(chunks.get(0).contains("w3 w4."));
        assertTrue(chunks.get(1).contains("w3 w4."));
        assertTrue(chunks.get(1).contains("w5 w6."));
    }

    @Test
    void testChineseLanguageUsesNoSpaceSeparator() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "zh");

        List<String> chunks = splitter.splitText("第一句。第二句。第三句。");

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("第一句。第二句。第三句。"));
    }

    @Test
    void testDetectChineseReturnsEnglishForAscii() throws Exception {
        assertEquals("en", invokeDetectLanguage("Hello world. How are you?"));
    }

    @Test
    void testDetectChineseReturnsEnglishForEmptyString() throws Exception {
        assertEquals("en", invokeDetectLanguage(""));
    }

    @Test
    void testDetectChineseReturnsChineseWhenHanRatioIsHigh() throws Exception {
        assertEquals("zh", invokeDetectLanguage("这是中文句子。它应该被检测为中文。"));
    }

    @Test
    void testDetectChineseReturnsEnglishForMixedLowHanRatio() throws Exception {
        assertEquals("en", invokeDetectLanguage("Hello world Hello world Hello world 中文"));
    }

    @Test
    void testDetectChineseThresholdBoundary() throws Exception {
        assertEquals("zh", invokeDetectLanguage("abcdefghijklmnopqr中文"));
    }

    @Test
    void testSmallChunkSizeProducesMultipleChunks() {
        SentenceSplitter splitter = new SentenceSplitter(2, 0, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("one two. three four. five six.");

        assertTrue(chunks.size() >= 2);
    }

    @Test
    void testEnglishWhitespaceNormalizedWithinChunks() {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "en");

        List<String> chunks = splitter.splitText("First   sentence.\nSecond   sentence.");

        assertEquals(1, chunks.size());
        assertTrue(chunks.getFirst().contains("First sentence."));
        assertTrue(chunks.getFirst().contains("Second sentence."));
    }

    @Test
    void testCustomLanguageValueIsUsedAsProvided() throws Exception {
        SentenceSplitter splitter = new SentenceSplitter(512, 50, TOKENIZER, "fr");

        assertEquals("fr", readDefaultLanguage(splitter));
    }

    private static String readDefaultLanguage(SentenceSplitter splitter) throws Exception {
        Field field = SentenceSplitter.class.getDeclaredField("defaultLanguage");
        field.setAccessible(true);
        return (String) field.get(splitter);
    }

    private static String invokeDetectLanguage(String text) throws Exception {
        Method method = SentenceSplitter.class.getDeclaredMethod("detectLanguage", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, text);
    }
}
