/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class SentenceSplitterTest {
    @Test
    void splitTextHandlesChinesePunctuationWithoutSpaces() {
        SentenceSplitter splitter = new SentenceSplitter(3, 0, null, "auto");

        List<String> chunks = splitter.splitText("第一句。第二句！第三句？");

        assertEquals(List.of("第一句。", "第二句！", "第三句？"), chunks);
    }

    @Test
    void splitTextUsesTokenizerForEnglishWindowing() {
        Function<String, List<String>> tokenizer =
            text -> Arrays.stream(text.replaceAll("[^A-Za-z0-9\\s]", " ").trim().split("\\s+"))
                    .filter(part -> !part.isBlank()).toList();
        SentenceSplitter splitter = new SentenceSplitter(4, 0, tokenizer, "en");

        List<String> chunks = splitter.splitText("Alpha beta. Gamma delta.");

        assertEquals(List.of("Alpha beta. Gamma delta."), chunks);
    }

    @Test
    void splitSpansShouldReturnStartAndEndOffsets() {
        SentenceSplitter splitter =
            new SentenceSplitter(5, 0, text -> Arrays.stream(text.split("\\s+")).toList(), "en");

        List<SplitSpan> spans = splitter.splitSpans("one two three four five. six seven.");

        assertThat(spans).hasSize(2);
        assertThat(spans.get(0).text()).contains("one").contains("five");
        assertThat(spans.get(0).start()).isEqualTo(0);
        assertThat(spans.get(1).text()).contains("six").contains("seven");
        assertThat(spans.get(1).start()).isGreaterThan(spans.get(0).start());
    }

    @Test
    void splitTextShouldCarryOverlapSentencesByTokenCount() {
        SentenceSplitter splitter =
            new SentenceSplitter(4, 3, text -> Arrays.stream(text.split("\\s+")).toList(), "en");

        List<String> chunks = splitter.splitText("w1 w2. w3 w4. w5 w6.");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("w1").contains("w4");
        assertThat(chunks.get(1)).contains("w3").contains("w4").contains("w5").contains("w6");
    }

    @Test
    void longSentenceShouldSplitIntoTokenWindowsWhenDecoderProvided() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        Function<List<String>, String> decoder = tokens -> String.join(" ", tokens);
        SentenceSplitter splitter = new SentenceSplitter(10, 0, tokenizer, "en", decoder);
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 25).mapToObj(i -> "w" + i).toList());

        List<SplitSpan> spans = splitter.splitSpans(longSentence);

        assertThat(spans).hasSize(3);
        assertThat(spans).allSatisfy(span -> {
            assertThat(span.start()).isEqualTo(0);
            assertThat(span.end()).isEqualTo(longSentence.length());
            assertThat(span.text().split("\\s+").length).isLessThanOrEqualTo(10);
        });
    }

    @Test
    void longSentenceWithoutDecoderShouldStaySingleChunk() {
        Function<String, List<String>> tokenizer = text -> Arrays.stream(text.split("\\s+")).toList();
        SentenceSplitter splitter = new SentenceSplitter(10, 0, tokenizer, "en");
        String longSentence = String.join(" ", java.util.stream.IntStream.range(0, 25).mapToObj(i -> "w" + i).toList());

        assertThat(splitter.splitText(longSentence)).containsExactly(longSentence);
    }

    @Test
    void detectLanguageShouldUseChineseRatioAndPunctuationHeuristic() {
        assertThat(SentenceSplitter.detectLanguage("")).isEqualTo("en");
        assertThat(SentenceSplitter.detectLanguage("Hello world. How are you?")).isEqualTo("en");
        assertThat(SentenceSplitter.detectLanguage("这是中文句子。它应该被检测为中文。")).isEqualTo("zh");
        assertThat(SentenceSplitter.detectLanguage("Hello world " + "x".repeat(40) + "中文")).isEqualTo("en");
        assertThat(SentenceSplitter.detectLanguage("Latin only here？？！！")).isEqualTo("zh");
    }
}
