/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sentence splitter wrapper with tokenizer-aware windows.
 * <p>
 * Mirrors Python's {@code IndexSentenceSplitter} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_splitter.py}.
 * </p>
 */
public class IndexSentenceSplitter extends TextSplitter {

    public static final int DEFAULT_CHUNK_SIZE = 200;
    public static final int DEFAULT_SAFE_ENCODE_MAX_LENGTH = 65536;

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[^.!?。！？]+[.!?。！？]*", Pattern.MULTILINE);
    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;

    private final TokenCodec tokenizer;
    private final int chunkSize;
    private final int chunkOverlap;
    private final String language;

    public IndexSentenceSplitter() {
        this(null, null, null, null, "auto");
    }

    public IndexSentenceSplitter(TokenCodec tokenizer,
                                 Integer chunkSize,
                                 Integer chunkOverlap,
                                 Map<String, Object> splitterConfig,
                                 String language) {
        this.tokenizer = tokenizer;
        Integer maxTokenLength = maxLength(tokenizer);
        this.chunkSize = resolveChunkSize(chunkSize, maxTokenLength);
        this.chunkOverlap = chunkOverlap == null || chunkOverlap == 0 ? this.chunkSize / 5 : chunkOverlap;
        this.language = language == null ? "auto" : language;
    }

    @Override
    public List<TextChunk> split(Document doc) {
        return getNodesFromDocuments(List.of(doc));
    }

    @Override
    public List<TextChunk> split(TextChunk chunk) {
        Document node = new Document(chunk.getDocId(), chunk.getText(), chunk.getMetadata());
        return getNodesFromDocuments(List.of(node));
    }

    public List<TextChunk> getNodesFromDocuments(List<Document> docs) {
        List<TextChunk> chunks = new ArrayList<>();
        if (docs == null) {
            return chunks;
        }
        for (Document doc : docs) {
            if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                LOGGER.warning("Skipping empty document: {}", doc);
                continue;
            }
            for (String text : splitText(doc.getText())) {
                chunks.add(TextChunk.fromDocument(doc, text));
            }
        }
        return chunks;
    }

    public List<String> splitText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalizedText = text.trim();
        String lang = "auto".equalsIgnoreCase(language) ? detectLanguage(normalizedText) : language;
        String separator = "zh".equalsIgnoreCase(lang) ? "" : " ";

        List<String> segments = new ArrayList<>();
        for (String sentence : splitSentences(normalizedText, lang)) {
            if (tokenCount(sentence) > chunkSize) {
                segments.addAll(splitOversizedSentence(sentence, lang));
            } else {
                segments.add(sentence);
            }
        }

        List<String> result = new ArrayList<>();
        List<String> window = new ArrayList<>();
        int tokenCount = 0;
        for (String segment : segments) {
            int segmentTokens = tokenCount(segment);
            if (!window.isEmpty() && tokenCount + segmentTokens > chunkSize) {
                result.add(String.join(separator, window).trim());
                List<String> overlapWindow = new ArrayList<>();
                int overlapTokens = 0;
                for (int index = window.size() - 1; index >= 0; index--) {
                    String item = window.get(index);
                    int itemTokens = tokenCount(item);
                    if (overlapTokens + itemTokens > chunkOverlap) {
                        break;
                    }
                    overlapWindow.add(0, item);
                    overlapTokens += itemTokens;
                }
                window = overlapWindow;
                tokenCount = overlapTokens;
            }
            window.add(segment.trim());
            tokenCount += segmentTokens;
        }
        if (!window.isEmpty()) {
            result.add(String.join(separator, window).trim());
        }
        return result;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    private List<String> splitOversizedSentence(String sentence, String lang) {
        List<String> tokens = tokenize(sentence, lang);
        if (tokens.isEmpty()) {
            return List.of(sentence.trim());
        }
        int step = Math.max(1, chunkSize - chunkOverlap);
        List<String> result = new ArrayList<>();
        for (int start = 0; start < tokens.size(); start += step) {
            int end = Math.min(tokens.size(), start + chunkSize);
            List<String> window = tokens.subList(start, end);
            if (tokenizer != null && tokenizer.canDecode()) {
                result.add(tokenizer.decode(window));
            } else {
                String separator = "zh".equalsIgnoreCase(lang) ? "" : " ";
                result.add(String.join(separator, window).trim());
            }
            if (end >= tokens.size()) {
                break;
            }
        }
        return result;
    }

    private int tokenCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return tokenize(text, language).size();
    }

    private List<String> tokenize(String text, String lang) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (tokenizer != null) {
            List<String> tokens = tokenizer.encode(trimmed, DEFAULT_SAFE_ENCODE_MAX_LENGTH);
            return tokens == null ? List.of() : tokens;
        }
        String effectiveLanguage = "auto".equalsIgnoreCase(lang) ? detectLanguage(trimmed) : lang;
        if ("zh".equalsIgnoreCase(effectiveLanguage)) {
            List<String> chars = new ArrayList<>(trimmed.length());
            for (int index = 0; index < trimmed.length(); index++) {
                chars.add(String.valueOf(trimmed.charAt(index)));
            }
            return chars;
        }
        return List.of(trimmed.split("\\s+"));
    }

    private static Integer maxLength(TokenCodec tokenizer) {
        if (tokenizer == null) {
            return null;
        }
        try {
            Integer value = tokenizer.maxTokenLength();
            return value != null && value > 0 ? value : null;
        } catch (RuntimeException exception) {
            LOGGER.warning("Failed to get max length", exception);
            return null;
        }
    }

    private static int resolveChunkSize(Integer chunkSize, Integer maxTokenLength) {
        if (chunkSize == null && maxTokenLength != null) {
            return maxTokenLength;
        }
        if (chunkSize != null && maxTokenLength != null) {
            return Math.min(chunkSize, maxTokenLength);
        }
        return chunkSize == null || chunkSize == 0 ? DEFAULT_CHUNK_SIZE : chunkSize;
    }

    private static String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        int chinese = 0;
        for (int index = 0; index < text.length(); index++) {
            if (Character.UnicodeScript.of(text.charAt(index)) == Character.UnicodeScript.HAN) {
                chinese++;
            }
        }
        return chinese >= Math.max(1, (int) Math.ceil(text.length() * 0.1d)) ? "zh" : "en";
    }

    private static List<String> splitSentences(String text, String lang) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        if (sentences.isEmpty()) {
            return Collections.singletonList(text);
        }
        if ("zh".equalsIgnoreCase(lang)) {
            return sentences;
        }
        List<String> normalized = new ArrayList<>(sentences.size());
        for (String sentence : sentences) {
            normalized.add(sentence.replaceAll("\\s+", " ").trim());
        }
        return normalized;
    }

    public interface TokenCodec {

        List<String> encode(String text, int maxLength);

        default String decode(List<String> tokens) {
            return String.join(" ", tokens);
        }

        default boolean canDecode() {
            return true;
        }

        default Integer maxTokenLength() {
            return null;
        }
    }
}
