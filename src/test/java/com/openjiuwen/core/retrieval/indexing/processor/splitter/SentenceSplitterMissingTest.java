/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Supplemental tests for {@link SentenceSplitter}.
 *
 * <p>Mirrors Python's {@code TestSentenceSplitter} and {@code TestDetectChinese} in
 * {@code tests/unit_tests/core/retrieval/indexing/processor/splitter/test_splitter.py}.</p>
 */
class SentenceSplitterMissingTest {

    private static final Function<List<?>, String> JOIN_DECODER = tokens -> String.join(
            " ",
            tokens.stream().map(String::valueOf).toList()
    );

    @Test
    void initWithDefaultsKeepsAutoLanguageLazySegmenterAndTokenizer() {
        WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();

        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 512, 50, "auto");

        assertEquals(512, splitter.getChunkSize());
        assertEquals(50, splitter.getChunkOverlap());
        assertSame(tokenizer, splitter.getTokenizer());
        assertEquals("", splitter.getDefaultLan());
        assertNull(splitter.getSegmenter());
    }

    @Test
    void initWithCustomLanguageKeepsExplicitLanguageAndLazySegmenter() {
        SentenceSplitter splitter = new SentenceSplitter(new WhitespaceTokenizer(), 512, 50, "en");

        assertEquals("en", splitter.getDefaultLan());
        assertNull(splitter.getSegmenter());
    }

    @Test
    void splitEmptyTextReturnsNoChunks() {
        SentenceSplitter splitter = fixedSplitter(List.of(), 512, 50);

        assertEquals(List.of(), splitter.split(""));
    }

    @Test
    void splitWhitespaceOnlyReturnsNoChunks() {
        SentenceSplitter splitter = fixedSplitter(List.of(), 512, 50);

        assertEquals(List.of(), splitter.split("   \n\t   "));
    }

    @Test
    void splitSingleSentenceReturnsTextAndSpan() {
        String sentence = "This is a test sentence.";
        SentenceSplitter splitter = fixedSplitter(List.of(sentence), 512, 50);

        List<Splitter.SplitChunk> chunks = splitter.split(sentence);

        assertEquals(1, chunks.size());
        assertEquals(sentence, chunks.getFirst().text());
        assertEquals(0, chunks.getFirst().startIdx());
        assertEquals(sentence.length(), chunks.getFirst().endIdx());
    }

    @Test
    void splitMultipleSentencesProcessesEverySentence() {
        SentenceSplitter splitter = fixedSplitter(List.of(
                "First sentence.",
                "Second sentence.",
                "Third sentence."
        ), 512, 50);

        List<Splitter.SplitChunk> chunks = splitter.split("First sentence. Second sentence. Third sentence.");
        String allText = String.join(" ", chunks.stream().map(Splitter.SplitChunk::text).toList());

        assertTrue(chunks.size() >= 1);
        assertTrue(allText.contains("First sentence"));
        assertTrue(allText.contains("Second sentence"));
        assertTrue(allText.contains("Third sentence"));
    }

    @Test
    void splitLongSentenceKeepsAtLeastOneChunk() {
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 1000)
                .mapToObj(index -> "word")
                .toList());
        SentenceSplitter splitter = fixedSplitter(List.of(longSentence), 100, 10);

        List<Splitter.SplitChunk> chunks = splitter.split(longSentence);

        assertTrue(chunks.size() >= 1);
    }

    @Test
    void splitCombinesSentencesWhenChunkSizeAllows() {
        SentenceSplitter splitter = fixedSplitter(List.of(
                "Short sentence 1.",
                "Short sentence 2.",
                "Short sentence 3."
        ), 100, 10);

        List<Splitter.SplitChunk> chunks = splitter.split("Short sentence 1. Short sentence 2. Short sentence 3.");

        assertTrue(chunks.size() >= 1);
        if (chunks.size() == 1) {
            assertTrue(chunks.getFirst().text().contains("Short sentence 1"));
            assertTrue(chunks.getFirst().text().contains("Short sentence 2"));
            assertTrue(chunks.getFirst().text().contains("Short sentence 3"));
        }
    }

    @Test
    void splitUsesTokenizerTokenCountInsteadOfCharacterCount() {
        SentenceSplitter splitter = fixedSplitter(List.of(
                "one two three four five",
                "six seven"
        ), 5, 0);

        List<Splitter.SplitChunk> chunks = splitter.split("one two three four five six seven");

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().contains("one") && chunks.get(0).text().contains("five"));
        assertTrue(chunks.get(1).text().contains("six") && chunks.get(1).text().contains("seven"));
        assertEquals(0, chunks.get(0).startIdx());
        assertTrue(chunks.get(1).startIdx() > chunks.get(0).startIdx());
    }

    @Test
    void splitChunkOverlapRespectsTokenLengths() {
        String s1 = "w1 w2";
        String s2 = "w3 w4";
        String s3 = "w5 w6";
        SentenceSplitter splitter = fixedSplitter(List.of(s1, s2, s3), 4, 3);

        List<Splitter.SplitChunk> chunks = splitter.split("w1 w2 w3 w4 w5 w6");

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().contains("w1") && chunks.get(0).text().contains("w4"));
        assertTrue(chunks.get(1).text().contains("w3") && chunks.get(1).text().contains("w4"));
        assertTrue(chunks.get(1).text().contains("w5") && chunks.get(1).text().contains("w6"));
    }

    @Test
    void splitLongSentenceWithDecoderProducesMultipleWindows() {
        String longSentence = tokenText("w", 25);
        SentenceSplitter splitter = fixedSplitter(List.of(longSentence), 10, 0, "en", JOIN_DECODER);

        List<Splitter.SplitChunk> chunks = splitter.split(longSentence);
        Set<String> covered = new HashSet<>();
        for (Splitter.SplitChunk chunk : chunks) {
            assertEquals(0, chunk.startIdx());
            assertEquals(longSentence.length(), chunk.endIdx());
            covered.addAll(Arrays.asList(chunk.text().split("\\s+")));
        }

        assertEquals(3, chunks.size());
        assertEquals(new HashSet<>(Arrays.asList(longSentence.split("\\s+"))), covered);
    }

    @Test
    void splitLongSentenceWithoutDecoderStaysSingleChunk() {
        String longSentence = tokenText("w", 25);
        SentenceSplitter splitter = fixedSplitter(List.of(longSentence), 10, 0, "en", null);

        List<Splitter.SplitChunk> chunks = splitter.split(longSentence);

        assertEquals(1, chunks.size());
        assertEquals(longSentence, chunks.getFirst().text());
    }

    @Test
    void splitLongSegmentRespectsOverlapStep() {
        String longSentence = tokenText("t", 20);
        SentenceSplitter splitter = fixedSplitter(List.of(longSentence), 6, 2, "en", JOIN_DECODER);

        List<Splitter.SplitChunk> chunks = splitter.split(longSentence);

        assertTrue(chunks.size() >= 2);
        for (Splitter.SplitChunk chunk : chunks) {
            assertTrue(chunk.text().split("\\s+").length <= 6);
        }
    }

    @Test
    void detectChineseEmptyTextIsEnglish() {
        assertEquals("en", SentenceSplitter.detectChinese(""));
    }

    @Test
    void detectChineseAsciiTextIsEnglish() {
        assertEquals("en", SentenceSplitter.detectChinese("Hello world. How are you?"));
    }

    @Test
    void detectChineseMostlyChineseTextIsChinese() {
        String text = "\u8FD9\u662F\u4E2D\u6587\u53E5\u5B50\u3002"
                + "\u5B83\u5E94\u8BE5\u88AB\u68C0\u6D4B\u4E3A\u4E2D\u6587\u3002";

        assertEquals("zh", SentenceSplitter.detectChinese(text));
    }

    @Test
    void detectChineseMixedBelowThresholdIsEnglish() {
        String text = "Hello world ".repeat(5) + "\u4E2D\u6587";

        assertEquals("en", SentenceSplitter.detectChinese(text));
    }

    @Test
    void detectChineseIntegerThresholdUsesFloorRatio() {
        String text = "abcdefghijklmnopqrs\u4E2D";
        String textTwo = "abcdefghijklmnopqr\u4E2D\u6587";

        assertEquals(20, text.length());
        assertEquals("en", SentenceSplitter.detectChinese(text));
        assertEquals(20, textTwo.length());
        assertEquals("zh", SentenceSplitter.detectChinese(textTwo));
    }

    @Test
    void detectChinesePunctuationHeuristicCanReturnChinese() {
        String text = "Latin only here\uFF1F\uFF1F\uFF01\uFF01";

        assertEquals("zh", SentenceSplitter.detectChinese(text));
    }

    @Test
    void detectChinesePunctuationHeuristicRequiresBothConditions() {
        String text = "Hello world\uFF1F\uFF1F\uFF1F";

        assertEquals("en", SentenceSplitter.detectChinese(text));
    }

    @Test
    void detectChineseHonorsCustomThreshold() {
        assertEquals("zh", SentenceSplitter.detectChinese("ab\u4E2D", 0.5d));
        assertEquals("en", SentenceSplitter.detectChinese("ab\u4E2D", 0.99d));
    }

    private static SentenceSplitter fixedSplitter(List<String> sentences, int chunkSize, int chunkOverlap) {
        return fixedSplitter(sentences, chunkSize, chunkOverlap, "en", null);
    }

    private static SentenceSplitter fixedSplitter(List<String> sentences,
                                                  int chunkSize,
                                                  int chunkOverlap,
                                                  String language,
                                                  Function<List<?>, String> decoder) {
        return new SentenceSplitter(
                new WhitespaceTokenizer(),
                chunkSize,
                chunkOverlap,
                language,
                decoder,
                ignoredLanguage -> ignoredText -> sentences
        );
    }

    private static String tokenText(String prefix, int count) {
        return String.join(" ", java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> prefix + index)
                .toList());
    }

    private static final class WhitespaceTokenizer implements Splitter.TokenizerAdapter {

        @Override
        public Object encode(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return Arrays.asList(text.trim().split("\\s+"));
        }
    }
}
