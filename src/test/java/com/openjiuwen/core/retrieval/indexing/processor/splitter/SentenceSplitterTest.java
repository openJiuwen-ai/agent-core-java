/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

    @Test
    void splitTextHandlesChinesePunctuationWithoutSpaces() {
        SentenceSplitter splitter = new SentenceSplitter(null, 3, 0, "auto");

        List<String> chunks = splitter.splitText("第一句。第二句！第三句？");

        assertEquals(List.of("第一句。", "第二句！", "第三句？"), chunks);
    }

    @Test
    void splitTextUsesTokenizerForEnglishWindowing() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.replaceAll("[^A-Za-z0-9\\s]", " ")
                        .trim()
                        .split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 4, 0, "en");

        List<String> chunks = splitter.splitText("Alpha beta. Gamma delta.");

        assertEquals(List.of("Alpha beta. Gamma delta."), chunks);
    }

    @Test
    void splitTextPreservesOriginalWhitespaceBetweenPackedSentences() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.replaceAll("[^A-Za-z0-9\\s]", " ")
                        .trim()
                        .split("\\s+"))
                .filter(part -> !part.isBlank())
                .toList();
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 4, 0, "en");

        List<String> chunks = splitter.splitText("Alpha beta. \n  Gamma delta.");

        assertEquals(List.of("Alpha beta. \n  Gamma delta."), chunks);
    }

    @Test
    void splitShouldReturnStartAndEndOffsets() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 5, 0, "en");

        List<Splitter.SplitChunk> chunks = splitter.split("one two three four five. six seven.");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text()).contains("one").contains("five");
        assertThat(chunks.get(0).startIdx()).isEqualTo(0);
        assertThat(chunks.get(1).text()).contains("six").contains("seven");
        assertThat(chunks.get(1).startIdx()).isGreaterThan(chunks.get(0).startIdx());
    }

    @Test
    void splitTextShouldCarryOverlapSentencesByTokenCount() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 4, 3, "en");

        List<String> chunks = splitter.splitText("w1 w2. w3 w4. w5 w6.");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("w1").contains("w4");
        assertThat(chunks.get(1)).contains("w3").contains("w4").contains("w5").contains("w6");
    }

    @Test
    void longSentenceShouldSplitIntoTokenWindowsWhenDecoderProvided() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        Function<List<?>, String> decoder = tokens -> String.join(" ", tokens.stream().map(String::valueOf).toList());
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 10, 0, "en", decoder);
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> "w" + i)
                .toList());

        List<Splitter.SplitChunk> chunks = splitter.split(longSentence);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.startIdx()).isEqualTo(0);
            assertThat(chunk.endIdx()).isEqualTo(longSentence.length());
            assertThat(chunk.text().split("\\s+").length).isLessThanOrEqualTo(10);
        });
    }

    @Test
    void longSentenceWithoutDecoderShouldStaySingleChunk() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        SentenceSplitter splitter = new SentenceSplitter(tokenizer, 10, 0, "en");
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> "w" + i)
                .toList());

        assertThat(splitter.splitText(longSentence)).containsExactly(longSentence);
    }

    @Test
    void detectLanguageShouldUseChineseRatioAndPunctuationHeuristic() {
        assertThat(SentenceSplitter.detectChinese("")).isEqualTo("en");
        assertThat(SentenceSplitter.detectChinese("Hello world. How are you?")).isEqualTo("en");
        assertThat(SentenceSplitter.detectChinese("这是中文句子。它应该被检测为中文。")).isEqualTo("zh");
        assertThat(SentenceSplitter.detectChinese("Hello world " + "x".repeat(40) + "中文")).isEqualTo("en");
        assertThat(SentenceSplitter.detectChinese("Latin only here？？！！")).isEqualTo("zh");
    }
}
