/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for keyword extraction behavior.
 *
 * <p>Mirrors Python's {@code KeywordExtractor} in
 * {@code openjiuwen/agent_evolving/sharing/keyword_extractor.py}.</p>
 */
class KeywordExtractorTest {

    @Test
    void parseFromOptimizerOutputReadsMapKeywordsAndSummary() {
        KeywordExtractor.KeywordSummary summary = KeywordExtractor.parseFromOptimizerOutput(Map.of(
                "keywords", List.of(" alpha ", "", "beta", 42),
                "summary", " useful failure pattern "
        ));

        assertEquals(List.of("alpha", "beta", "42"), summary.keywords());
        assertEquals("useful failure pattern", summary.summary());
    }

    @Test
    void parseFromOptimizerOutputReadsEvolutionPatch() {
        EvolutionPatch patch = new EvolutionPatch(
                "Troubleshooting",
                "append",
                "body",
                EvolutionTarget.BODY,
                null,
                null,
                null,
                null,
                null,
                List.of(" retry ", ""),
                " timeout fix "
        );

        KeywordExtractor.KeywordSummary summary = KeywordExtractor.parseFromOptimizerOutput(patch);

        assertEquals(List.of("retry"), summary.keywords());
        assertEquals("timeout fix", summary.summary());
    }

    @Test
    void extractQueryKeywordsReturnsEmptyForBlankExcerpt() {
        QueryKeywords query = new KeywordExtractor()
                .extractQueryKeywords("   ")
                .toCompletableFuture()
                .join();

        assertEquals(List.of(), query.getKeywords());
        assertEquals("", query.getIntent());
        assertEquals("", query.getRawExcerpt());
    }

    @Test
    void extractQueryKeywordsFallsBackWhenNoLlmBound() {
        String excerpt = "0123456789012345678901234567890123456789TAIL";
        QueryKeywords query = new KeywordExtractor()
                .extractQueryKeywords(excerpt)
                .toCompletableFuture()
                .join();

        assertEquals(List.of(), query.getKeywords());
        assertEquals("0123456789012345678901234567890123456789", query.getIntent());
        assertEquals(excerpt, query.getRawExcerpt());
    }

    @Test
    void extractQueryKeywordsParsesJsonEmbeddedInModelOutput() {
        AtomicReference<List<BaseMessage>> capturedMessages = new AtomicReference<>();
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> {
            capturedMessages.set(messages);
            return CompletableFuture.completedFuture(new AssistantMessage(
                    "prefix {\"keywords\":[\" FileNotFoundError \",\"\", \"worktree\", 7],"
                            + "\"intent\":\"diagnose missing worktree file with a long suffix that is truncated\"} suffix"));
        });

        QueryKeywords query = new KeywordExtractor(model, "gpt-test", "en", KeywordExtractor.QUERY_KEYWORDS_LLM_POLICY)
                .extractQueryKeywords("tool failed", "skill hint")
                .toCompletableFuture()
                .join();

        assertEquals(List.of("FileNotFoundError", "worktree", "7"), query.getKeywords());
        assertEquals("diagnose missing worktree file with a long suffix that is truncated", query.getIntent());
        assertEquals("tool failed", query.getRawExcerpt());
        assertTrue(String.valueOf(capturedMessages.get().get(0).getContent()).contains("skill hint"));
    }

    @Test
    void extractQueryKeywordsFallsBackWhenModelCallFails() {
        Model model = new Model((messages, modelConfig, modelClientConfig, options) -> failed(new RuntimeException("boom")));

        QueryKeywords query = new KeywordExtractor(model, "gpt-test")
                .extractQueryKeywords("failure excerpt")
                .toCompletableFuture()
                .join();

        assertEquals(List.of(), query.getKeywords());
        assertEquals("failure excerpt", query.getIntent());
        assertEquals("failure excerpt", query.getRawExcerpt());
    }

    private static CompletionStage<AssistantMessage> failed(RuntimeException exception) {
        CompletableFuture<AssistantMessage> future = new CompletableFuture<>();
        future.completeExceptionally(exception);
        return future;
    }
}
