/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Sentence-level splitter with token-aware packing.
 *
 * <p>Mirrors Python's {@code SentenceSplitter} in
 * {@code openjiuwen/core/retrieval/indexing/processor/splitter/splitter.py}.</p>
 */
public class SentenceSplitter extends Splitter {

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;

    private final String defaultLan;
    private final Function<List<?>, String> sentenceTokenizerDec;
    private final SentenceSegmenterFactory segmenterFactory;
    private SentenceSegmenter segmenter;

    public SentenceSplitter(Object tokenizer, int chunkSize, int chunkOverlap) {
        this(tokenizer, chunkSize, chunkOverlap, "auto", null);
    }

    public SentenceSplitter(Object tokenizer, int chunkSize, int chunkOverlap, String lan) {
        this(tokenizer, chunkSize, chunkOverlap, lan, null);
    }

    public SentenceSplitter(Object tokenizer,
                            int chunkSize,
                            int chunkOverlap,
                            String lan,
                            Function<List<?>, String> tokenizerDec) {
        this(tokenizer, chunkSize, chunkOverlap, lan, tokenizerDec, DefaultSentenceSegmenter::new);
    }

    SentenceSplitter(Object tokenizer,
                     int chunkSize,
                     int chunkOverlap,
                     String lan,
                     Function<List<?>, String> tokenizerDec,
                     SentenceSegmenterFactory segmenterFactory) {
        super(tokenizer, chunkSize, chunkOverlap);
        this.defaultLan = "auto".equals(lan) || lan == null ? "" : lan;
        this.sentenceTokenizerDec = tokenizerDec;
        this.segmenterFactory = segmenterFactory;
        this.segmenter = null;
    }

    public String getDefaultLan() {
        return defaultLan;
    }

    public SentenceSegmenter getSegmenter() {
        return segmenter;
    }

    public Object getTokenizer() {
        return tokenizer;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    @Override
    public List<SplitChunk> split(String doc) {
        if (doc == null || doc.isBlank()) {
            return List.of();
        }

        String detectedLan = defaultLan.isEmpty() ? detectChinese(doc) : defaultLan;
        segmenter = segmenterFactory.create(detectedLan);
        List<SentenceSpan> sentencesWithSpans = sentencesWithSpans(doc);
        List<SplitChunk> chunks = new ArrayList<>();
        List<SentenceSpan> currentSentences = new ArrayList<>();

        for (SentenceSpan sentence : sentencesWithSpans) {
            if (sentence.text().isBlank()) {
                continue;
            }
            if (sentence.tokenLength() > chunkSize) {
                FlushResult flushed = flush(chunks, currentSentences);
                chunks = flushed.chunks();
                currentSentences = flushed.nextSentences();
                if (sentenceTokenizerDec != null) {
                    chunks.addAll(splitLongSegment(sentence));
                } else {
                    chunks.add(new SplitChunk(sentence.text(), sentence.start(), sentence.end()));
                }
                continue;
            }

            int currentTokenCount = currentSentences.stream().mapToInt(SentenceSpan::tokenLength).sum();
            if (currentTokenCount + sentence.tokenLength() <= chunkSize) {
                currentSentences.add(sentence);
            } else {
                FlushResult flushed = flush(chunks, currentSentences);
                chunks = flushed.chunks();
                currentSentences = flushed.nextSentences();
                currentSentences.add(sentence);
            }
        }

        FlushResult flushed = flush(chunks, currentSentences);
        LOGGER.info("Computed the following sentence-level chunks: {} chunks", flushed.chunks().size());
        return flushed.chunks();
    }

    public static String detectChinese(String text) {
        return detectChinese(text, 0.1d);
    }

    public static String detectChinese(String text, double threshold) {
        if (text == null || text.isEmpty()) {
            return "en";
        }
        int totalChars = text.codePointCount(0, text.length());
        int thresholdValue = (int) (threshold * totalChars);
        int chineseCount = 0;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) {
                chineseCount++;
            }
            offset += Character.charCount(codePoint);
        }
        boolean isChinese = chineseCount >= thresholdValue;
        if (!isChinese) {
            isChinese = count(text, '\uFF1F') > count(text, '?')
                    && count(text, '\uFF01') > count(text, '!');
        }
        return isChinese ? "zh" : "en";
    }

    private List<SentenceSpan> sentencesWithSpans(String text) {
        List<String> sentences = segmenter.segment(text);
        Set<String> usedSpans = new HashSet<>();
        List<SentenceSpan> spans = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) {
                continue;
            }
            int sentenceTokens = getTokenCount(sentence);
            int index = 0;
            while (true) {
                index = text.indexOf(sentence, index);
                if (index == -1) {
                    LOGGER.warning("Span recovery failed for: {}...", sentence.substring(0, Math.min(30, sentence.length())));
                    break;
                }
                int end = index + sentence.length();
                String key = index + ":" + end;
                if (!usedSpans.contains(key)) {
                    usedSpans.add(key);
                    spans.add(new SentenceSpan(sentence, index, end, sentenceTokens));
                    break;
                }
                index++;
            }
        }
        return spans;
    }

    private List<SplitChunk> splitLongSegment(SentenceSpan sentence) {
        List<?> ids = encodeTokens(sentence.text());
        if (ids.isEmpty()) {
            return List.of(new SplitChunk(sentence.text(), sentence.start(), sentence.end()));
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        List<SplitChunk> result = new ArrayList<>();
        for (int windowStart = 0; windowStart < ids.size(); windowStart += step) {
            int windowEnd = Math.min(ids.size(), windowStart + chunkSize);
            List<?> window = ids.subList(windowStart, windowEnd);
            if (window.isEmpty()) {
                break;
            }
            String text = sentenceTokenizerDec.apply(window);
            if (text != null && !text.isBlank()) {
                result.add(new SplitChunk(text, sentence.start(), sentence.end()));
            }
        }
        if (result.isEmpty()) {
            return List.of(new SplitChunk(sentence.text(), sentence.start(), sentence.end()));
        }
        return result;
    }

    private FlushResult flush(List<SplitChunk> chunks, List<SentenceSpan> currentSentences) {
        if (currentSentences.isEmpty()) {
            return new FlushResult(chunks, new ArrayList<>());
        }

        StringBuilder chunkText = new StringBuilder();
        for (SentenceSpan sentence : currentSentences) {
            chunkText.append(sentence.text());
        }
        chunks.add(new SplitChunk(
                chunkText.toString(),
                currentSentences.get(0).start(),
                currentSentences.get(currentSentences.size() - 1).end()
        ));

        List<SentenceSpan> nextSentences = new ArrayList<>();
        if (chunkOverlap > 0 && currentSentences.size() > 1) {
            int overlapTokens = 0;
            for (int index = currentSentences.size() - 1; index >= 0; index--) {
                SentenceSpan sentence = currentSentences.get(index);
                if (overlapTokens + sentence.tokenLength() <= chunkOverlap) {
                    nextSentences.add(0, sentence);
                    overlapTokens += sentence.tokenLength();
                } else {
                    break;
                }
            }
        }

        return new FlushResult(chunks, nextSentences);
    }

    private List<?> encodeTokens(String text) {
        if (tokenizerEnc == null) {
            List<String> chars = new ArrayList<>();
            for (int index = 0; index < text.length(); index++) {
                chars.add(String.valueOf(text.charAt(index)));
            }
            return chars;
        }
        Object tokens = tokenizerEnc.apply(text);
        if (tokens instanceof List<?> list) {
            return list;
        }
        if (tokens instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            for (Object token : iterable) {
                values.add(token);
            }
            return values;
        }
        if (tokens != null && tokens.getClass().isArray()) {
            int length = Array.getLength(tokens);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(Array.get(tokens, index));
            }
            return values;
        }
        return List.of(String.valueOf(tokens));
    }

    private static int count(String text, char target) {
        int value = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == target) {
                value++;
            }
        }
        return value;
    }

    public interface SentenceSegmenter {
        List<String> segment(String text);
    }

    interface SentenceSegmenterFactory {
        SentenceSegmenter create(String language);
    }

    private record SentenceSpan(String text, int start, int end, int tokenLength) {
    }

    private record FlushResult(List<SplitChunk> chunks, List<SentenceSpan> nextSentences) {
    }

    private static final class DefaultSentenceSegmenter implements SentenceSegmenter {

        private DefaultSentenceSegmenter(String language) {
        }

        @Override
        public List<String> segment(String text) {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            List<String> sentences = new ArrayList<>();
            int start = 0;
            for (int index = 0; index < text.length(); index++) {
                char value = text.charAt(index);
                if (isSentenceEnd(value)) {
                    String sentence = text.substring(start, index + 1).trim();
                    if (!sentence.isEmpty()) {
                        sentences.add(sentence);
                    }
                    start = index + 1;
                    while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                        start++;
                    }
                    index = start - 1;
                }
            }
            if (start < text.length()) {
                String sentence = text.substring(start).trim();
                if (!sentence.isEmpty()) {
                    sentences.add(sentence);
                }
            }
            return sentences.isEmpty() ? List.of(text.trim()) : sentences;
        }

        private static boolean isSentenceEnd(char value) {
            return value == '.' || value == '!' || value == '?'
                    || value == '\u3002' || value == '\uFF01' || value == '\uFF1F';
        }
    }
}
