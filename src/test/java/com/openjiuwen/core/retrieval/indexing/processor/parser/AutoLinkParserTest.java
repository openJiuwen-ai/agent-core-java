/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.retrieval.common.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLinkParserTest {

    @Test
    void supportsWechatAndGenericHttpUrls() {
        AutoLinkParser parser = new AutoLinkParser();

        assertTrue(parser.supports("https://mp.weixin.qq.com/s/abc123"));
        assertTrue(parser.supports("https://example.com/page"));
        assertFalse(parser.supports("ftp://example.com"));
        assertFalse(parser.supports("not-a-url"));
    }

    @Test
    void parseDelegatesToFirstMatchingRoute() {
        RecordingParser parser = new RecordingParser(List.of(new Document("doc-1", "parsed", Map.of())));
        AutoLinkParser router = new AutoLinkParser(List.of(
                new AutoLinkParser.Route(url -> url != null && url.contains("wechat"), parser),
                new AutoLinkParser.Route(Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE).asMatchPredicate(), new RecordingParser(List.of()))));

        List<Document> docs = router.parse("https://wechat.com/article", "id-1", null, Map.of());

        assertEquals(1, docs.size());
        assertEquals("https://wechat.com/article", parser.lastDoc);
        assertEquals("id-1", parser.lastDocId);
    }

    private static final class RecordingParser extends Parser {

        private final List<Document> result;
        private String lastDoc;
        private String lastDocId;

        private RecordingParser(List<Document> result) {
            this.result = result;
        }

        @Override
        public List<Document> parse(String doc, String docId, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            this.lastDoc = doc;
            this.lastDocId = docId;
            return result;
        }

        @Override
        protected String parseContent(String doc, com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient llmClient, Map<String, Object> options) {
            return null;
        }
    }
}
