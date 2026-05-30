/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShowcaseQueryRewriterExampleTest {

    @Test
    void constantsAndConfigMatchPythonQueryRewriterExample() {
        assertEquals(4, ShowcaseQueryRewriterExample.COMPRESS_RANGE);
        assertEquals("zh", ShowcaseQueryRewriterExample.PROMPT_LANG);
        assertEquals(8, ShowcaseQueryRewriterExample.EXAMPLE_TURNS.size());
        assertEquals("user", ShowcaseQueryRewriterExample.EXAMPLE_TURNS.get(0).role());
        assertEquals("What is our project's tech stack?",
                ShowcaseQueryRewriterExample.EXAMPLE_TURNS.get(0).content());
        assertEquals("Can you summarize that again?", ShowcaseQueryRewriterExample.FINAL_QUERY);

        ModelConfig config = ShowcaseQueryRewriterExample.sampleQrModelConfig();
        assertEquals("OpenAI", config.modelProvider());
        assertEquals("qr-model", config.modelInfo().getModelName());
        assertEquals("https://qr.example/v1", config.modelInfo().getApiBase());
        assertEquals("qr-key", config.modelInfo().getApiKey());
    }

    @Test
    void inMemoryContextSupportsPythonExampleMessageOperations() {
        ShowcaseQueryRewriterExample.InMemoryContext ctx = new ShowcaseQueryRewriterExample.InMemoryContext();

        ctx.addMessage(ShowcaseQueryRewriterExample.Message.user("u1"));
        ctx.addMessage(ShowcaseQueryRewriterExample.Message.assistant("a1"));
        ctx.addMessage(ShowcaseQueryRewriterExample.Message.user("u2"));

        assertEquals(3, ctx.size());
        assertEquals(List.of(
                ShowcaseQueryRewriterExample.Message.assistant("a1"),
                ShowcaseQueryRewriterExample.Message.user("u2")
        ), ctx.getMessages(2, true));
        assertEquals(List.of(ShowcaseQueryRewriterExample.Message.user("u2")), ctx.popMessages(1));
        assertEquals(2, ctx.statistic().totalMessages());
        ctx.setMessages(List.of(ShowcaseQueryRewriterExample.Message.system("summary")));
        assertEquals("system", ctx.messages().get(0).role());
        ctx.clearMessages();
        assertEquals(0, ctx.size());
        assertEquals("qr_example_session", ctx.sessionId());
        assertEquals("qr_example_context", ctx.contextId());
    }

    @Test
    void runExamplePreservesRewriteBeforeAppendAndCompressionFlow() {
        ShowcaseQueryRewriterExample.DemoReport report = ShowcaseQueryRewriterExample.runExample();

        assertFalse(report.missingConfig());
        assertEquals(5, report.steps().size());
        assertTrue(report.compressionObserved());
        assertEquals("What is our project's deployment approach?",
                report.steps().get(1).result().standaloneQuery());
        assertEquals("Does the project support multi-tenancy?",
                report.steps().get(2).result().standaloneQuery());
        assertTrue(report.steps().get(2).result().compressionTriggered());
        assertEquals("Can you summarize the project's tech stack, deployment, and multi-tenancy again?",
                report.steps().get(4).result().standaloneQuery());
        assertTrue(report.steps().get(4).result().compressionTriggered());
        assertEquals(3, report.finalContextCount());
        assertEquals("system", report.finalMessages().get(0).role());
        assertEquals("user", report.finalMessages().get(1).role());
        assertEquals("assistant", report.finalMessages().get(2).role());
    }

    @Test
    void missingConfigReturnsGuardReportAndBlankQueriesAreRejected() {
        ShowcaseQueryRewriterExample.DemoReport missing =
                ShowcaseQueryRewriterExample.runExample(null,
                        new ShowcaseQueryRewriterExample.DeterministicQueryRewriter(4));
        assertTrue(missing.missingConfig());

        ShowcaseQueryRewriterExample.InMemoryContext ctx = new ShowcaseQueryRewriterExample.InMemoryContext();
        ShowcaseQueryRewriterExample.DeterministicQueryRewriter rewriter =
                new ShowcaseQueryRewriterExample.DeterministicQueryRewriter(4);
        assertThrows(IllegalArgumentException.class, () -> rewriter.rewrite(" ", ctx, "Turn"));
    }
}
