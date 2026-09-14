/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.tool;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code search_wikipedia} and module-level tool exports in
 * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
 */
class WikipediaToolTest {
    @Test
    void searchWikipediaReturnsTitleAndSummaryFromTopResult() {
        RecordingTransport transport = new RecordingTransport(
                Map.of("query", Map.of("search", List.of(Map.of("pageid", 42, "title", "Java")))),
                Map.of("query", Map.of("pages", Map.of("42", Map.of("extract", "A programming language."))))
        );

        String result = WikipediaTool.searchWikipedia("java", transport);

        assertEquals("Title: Java\nSummary: A programming language.", result);
        assertEquals("OpenJiuwenAgent/1.0 (Educational Research)", transport.requests().get(0).headers().get("User-Agent"));
        assertEquals("search", transport.requests().get(0).params().get("list"));
        assertEquals("42", transport.requests().get(1).params().get("pageids"));
    }

    @Test
    void searchWikipediaReturnsNoResultMessage() {
        RecordingTransport transport = new RecordingTransport(Map.of("query", Map.of("search", List.of())));

        String result = WikipediaTool.searchWikipedia("missing", transport);

        assertEquals("No Wikipedia results found for 'missing'.", result);
        assertEquals(1, transport.requests().size());
    }

    @Test
    void searchWikipediaReturnsNoSummaryMessage() {
        RecordingTransport transport = new RecordingTransport(
                Map.of("query", Map.of("search", List.of(Map.of("pageid", "7", "title", "Topic")))),
                Map.of("query", Map.of("pages", Map.of("7", Map.of("extract", ""))))
        );

        String result = WikipediaTool.searchWikipedia("topic", transport);

        assertEquals("Found page 'Topic' for 'topic', but no summary available.", result);
    }

    @Test
    void searchWikipediaTruncatesLongResultLikePython() {
        String extract = "x".repeat(2100);
        RecordingTransport transport = new RecordingTransport(
                Map.of("query", Map.of("search", List.of(Map.of("pageid", 1, "title", "Long")))),
                Map.of("query", Map.of("pages", Map.of("1", Map.of("extract", extract))))
        );

        String result = WikipediaTool.searchWikipedia("long", transport);

        assertEquals(2003, result.length());
        assertTrue(result.endsWith("..."));
        assertTrue(result.startsWith("Title: Long\nSummary: "));
    }

    @Test
    void searchWikipediaReturnsErrorMessageOnTransportFailure() {
        WikipediaTool.WikipediaHttpTransport transport = (url, params, headers) -> {
            throw new IllegalStateException("boom");
        };

        assertEquals("Error searching Wikipedia: boom", WikipediaTool.searchWikipedia("java", transport));
    }

    @Test
    void moduleExportsToolCardAndLocalFunction() throws Exception {
        assertSame(WikipediaTool.WIKIPEDIA_TOOL_CARD, WikipediaTool.WIKIPEDIA_TOOL.getCard());
        assertEquals("wikipedia_search", WikipediaTool.WIKIPEDIA_TOOL_CARD.getName());
        assertEquals("Search Wikipedia for information about a topic.",
                WikipediaTool.WIKIPEDIA_TOOL_CARD.getDescription());
        assertEquals(
                Map.of(
                        "description", "The search query for Wikipedia",
                        "title", "Query",
                        "type", "string"),
                ((Map<?, ?>) WikipediaTool.WIKIPEDIA_TOOL_CARD.getInputParams().get("properties")).get("query"));
    }

    /**
     * Mirrors Python's Wikipedia API response collaborator in
     * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
     */
    private static final class RecordingTransport implements WikipediaTool.WikipediaHttpTransport {
        private final List<Map<String, Object>> responses;
        private final List<Request> requests = new ArrayList<>();
        private int index;

        @SafeVarargs
        private RecordingTransport(Map<String, Object>... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public Map<String, Object> get(String url, Map<String, String> params, Map<String, String> headers) {
            requests.add(new Request(url, new LinkedHashMap<>(params), new LinkedHashMap<>(headers)));
            return responses.get(index++);
        }

        private List<Request> requests() {
            return requests;
        }
    }

    /**
     * Mirrors Python's captured {@code requests.get} call arguments in
     * {@code openjiuwen/extensions/context_evolver/tool/wikipedia_tool.py}.
     */
    private record Request(String url, Map<String, String> params, Map<String, String> headers) {
    }
}
