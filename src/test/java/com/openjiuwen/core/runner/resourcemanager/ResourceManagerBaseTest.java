/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.single_agent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for {@code openjiuwen/core/runner/resources_manager/base.py}.
 */
class ResourceManagerBaseTest {

    @Test
    void constantsMirrorPythonTagValues() {
        assertEquals("*", ResourceManagerBase.ALL);
        assertEquals("__global__", ResourceManagerBase.GLOBAL);
        assertEquals("__active__", ResourceManagerBase.ACTIVE);
        assertEquals("__inactive__", ResourceManagerBase.INACTIVE);
    }

    @Test
    void enumValuesMirrorPythonStringEnums() {
        assertEquals("all", TagMatchStrategy.ALL.value());
        assertEquals("any", TagMatchStrategy.ANY.value());
        assertEquals(TagMatchStrategy.ALL, TagMatchStrategy.fromValue("all"));
        assertEquals(TagMatchStrategy.ANY, TagMatchStrategy.fromValue("any"));

        assertEquals("merge", TagUpdateStrategy.MERGE.value());
        assertEquals("replace", TagUpdateStrategy.REPLACE.value());
        assertEquals(TagUpdateStrategy.MERGE, TagUpdateStrategy.fromValue("merge"));
        assertEquals(TagUpdateStrategy.REPLACE, TagUpdateStrategy.fromValue("replace"));

        assertThrows(IllegalArgumentException.class, () -> TagMatchStrategy.fromValue("missing"));
        assertThrows(IllegalArgumentException.class, () -> TagUpdateStrategy.fromValue("missing"));
    }

    @Test
    void resultWrappersExposePythonMsgSemantics() {
        Ok<String> ok = new Ok<>("done");
        assertTrue(ok.isOk());
        assertFalse(ok.isErr());
        assertEquals("done", ok.msg());
        assertEquals("done", ok.value());

        ErrorResult<Integer> error = new ErrorResult<>(42);
        assertFalse(error.isOk());
        assertTrue(error.isErr());
        assertEquals(42, error.msg());
        assertEquals(42, error.error());

        assertNull(new ErrorResult<>().error());
    }

    @Test
    void providerAdaptersSupportSyncAndAsyncCallables() {
        AgentCard card = new AgentCard("agent-1", "agent", "demo");

        ResourceManagerBase.AgentProvider<String> sync =
                ResourceManagerBase.AgentProvider.fromSync(agentCard -> agentCard.getId());
        assertEquals("agent-1", sync.provide(card).toCompletableFuture().join());

        ResourceManagerBase.AgentProvider<String> async =
                ResourceManagerBase.AgentProvider.fromAsync(agentCard ->
                        CompletableFuture.completedFuture(agentCard.getName()));
        assertEquals("agent", async.provide(card).toCompletableFuture().join());

        ResourceManagerBase.ModelProvider<String> modelProvider =
                ResourceManagerBase.ModelProvider.fromSync(args -> args[0] + ":" + args.length);
        assertEquals("model:2", modelProvider.provide("model", 7).toCompletableFuture().join());
    }
}
