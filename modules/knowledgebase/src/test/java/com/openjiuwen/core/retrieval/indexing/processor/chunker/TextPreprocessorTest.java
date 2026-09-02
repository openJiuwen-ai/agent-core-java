/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.retrieval.common.Document;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TextPreprocessorTest {
    @Test
    void whitespaceNormalizerShouldPreserveNullAndNormalizeWhitespace() {
        WhitespaceNormalizer normalizer = new WhitespaceNormalizer();

        assertThat(normalizer.process(null)).isNull();
        assertThat(normalizer.process("This  \n\t  is   a test")).isEqualTo("This is a test");
    }

    @Test
    void urlEmailRemoverShouldSupportFlagsAndReplacement() {
        assertThat(new URLEmailRemover().process("Visit https://example.com and test@example.com"))
                .doesNotContain("https://example.com").doesNotContain("test@example.com");
        assertThat(new URLEmailRemover(false, true, "[EMAIL]").process("Visit http://example.com and test@example.com"))
                .contains("http://example.com").contains("[EMAIL]").doesNotContain("test@example.com");
        assertThat(new URLEmailRemover(false, false, "[URL]").process("Visit www.example.com and test@example.com"))
                .contains("www.example.com").contains("test@example.com");
        assertThat(new URLEmailRemover().process(null)).isNull();
    }

    @Test
    void specialCharacterNormalizerShouldRemoveControlReplaceAndRemoveConfiguredChars() {
        SpecialCharacterNormalizer normalizer = new SpecialCharacterNormalizer("!#", Map.of("&", "and", "@", "at"));

        String result = normalizer.process("Tom & Jerry @ home!#\u0000");

        assertThat(result).contains("and").contains("at");
        assertThat(result).doesNotContain("&", "@", "!", "#", "\u0000");
        assertThat(normalizer.process(null)).isNull();
    }

    @Test
    void preprocessingPipelineShouldAddProcessInOrderAndExposeSize() {
        List<String> order = new ArrayList<>();
        TextPreprocessor first = text -> {
            order.add("first");
            return text + "1";
        };
        TextPreprocessor second = text -> {
            order.add("second");
            return text + "2";
        };
        PreprocessingPipeline pipeline = new PreprocessingPipeline(List.of(first));

        pipeline.addPreprocessor(second);

        assertThat(pipeline.process("x")).isEqualTo("x12");
        assertThat(order).containsExactly("first", "second");
        assertThat(pipeline.size()).isEqualTo(2);
        assertThat(pipeline.getPreprocessors()).containsExactly(first, second);
    }

    @Test
    void textChunkerShouldNotPreprocessByDefault() {
        TextChunker chunker = new TextChunker(200, 0, "char");
        Document document = new Document("id", "Visit https://example.com\nwith  spaces", Map.of());

        String chunk = chunker.chunkDocuments(List.of(document)).get(0).getText();

        assertThat(chunk).contains("https://example.com").contains("\n").contains("  spaces");
    }

    @Test
    void textChunkerShouldApplyExplicitPreprocessors() {
        TextChunker chunker = new TextChunker(200, 0, "char", null, "auto",
                List.of(new WhitespaceNormalizer(), new URLEmailRemover(), new WhitespaceNormalizer()));
        Document document = new Document("id", "Visit https://example.com\nwith  spaces", Map.of());

        String chunk = chunker.chunkDocuments(List.of(document)).get(0).getText();

        assertThat(chunk).doesNotContain("https://example.com");
        assertThat(chunk).contains("Visit with spaces");
    }
}
