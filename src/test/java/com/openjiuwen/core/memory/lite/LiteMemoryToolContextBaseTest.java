/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Focused validation for {@link LiteMemoryToolContextBase}.
 *
 * <p>Mirrors Python's {@code LiteMemoryToolContextBase} in
 * {@code openjiuwen/core/memory/lite/memory_tool_context_base.py}.</p>
 */
public final class LiteMemoryToolContextBaseTest {

    private LiteMemoryToolContextBaseTest() {
    }

    public static void main(String[] args) {
        returnsTrueForActiveManager();
        returnsFalseWithoutWorkspace();
        initializesManagerWhenWorkspaceExists();
        returnsFalseWhenProviderFails();
        System.out.println("PASS LiteMemoryToolContextBaseTest");
    }

    private static void returnsTrueForActiveManager() {
        LiteMemoryToolContextBase context = new LiteMemoryToolContextBase();
        RecordingManager manager = new RecordingManager(false);
        context.setManager(manager);

        require(context.ensureManager().toCompletableFuture().join(), "active manager");
        require(context.getManager() == manager, "active manager retained");
    }

    private static void returnsFalseWithoutWorkspace() {
        LiteMemoryToolContextBase context = new LiteMemoryToolContextBase();

        require(!context.ensureManager().toCompletableFuture().join(), "no workspace");
        require(context.getSettings() == null, "settings not initialized without workspace");
    }

    private static void initializesManagerWhenWorkspaceExists() {
        LiteMemoryToolContextBase context = new LiteMemoryToolContextBase();
        RecordingProvider provider = new RecordingProvider(new RecordingManager(false));
        context.setWorkspace(new Workspace(Path.of("./")));
        context.setAgentId("agent-a");
        context.setNodeName("coding_memory");
        context.setManagerProvider(provider);

        require(context.ensureManager().toCompletableFuture().join(), "initialized");
        require(context.getSettings() != null, "settings defaulted");
        require(context.getManager() == provider.manager, "manager assigned");
        require("agent-a".equals(provider.params.agentId()), "agent id");
        require("coding_memory".equals(provider.params.nodeName()), "node name");
        require(provider.params.settings() == context.getSettings(), "settings identity");
    }

    private static void returnsFalseWhenProviderFails() {
        LiteMemoryToolContextBase context = new LiteMemoryToolContextBase();
        context.setWorkspace(new Workspace(Path.of("./")));
        context.setManagerProvider(params -> {
            throw new IllegalStateException("boom");
        });

        require(!context.ensureManager().toCompletableFuture().join(), "provider failure");
        require(context.getManager() == null, "manager stays null");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record RecordingManager(boolean closed) implements LiteMemoryToolContextBase.MemoryIndexManagerView {
        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    private static final class RecordingProvider implements LiteMemoryToolContextBase.MemoryIndexManagerProvider {
        private final RecordingManager manager;
        private LiteMemoryToolContextBase.MemoryManagerParams params;

        private RecordingProvider(RecordingManager manager) {
            this.manager = manager;
        }

        @Override
        public CompletionStage<LiteMemoryToolContextBase.MemoryIndexManagerView> get(
                LiteMemoryToolContextBase.MemoryManagerParams params
        ) {
            this.params = params;
            return CompletableFuture.completedFuture(manager);
        }
    }
}
