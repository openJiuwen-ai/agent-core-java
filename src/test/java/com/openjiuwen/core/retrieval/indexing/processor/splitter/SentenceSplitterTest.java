/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SentenceSplitterTest {

    @Test
    void splitTextHandlesChinesePunctuationWithoutSpaces() {
        SentenceSplitter splitter = new SentenceSplitter(3, 0, null, "auto");

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
        SentenceSplitter splitter = new SentenceSplitter(4, 0, tokenizer, "en");

        List<String> chunks = splitter.splitText("Alpha beta. Gamma delta.");

        assertEquals(List.of("Alpha beta. Gamma delta."), chunks);
    }
}
