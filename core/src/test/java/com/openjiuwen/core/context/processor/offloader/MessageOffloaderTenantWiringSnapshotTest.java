/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.context.schema.OffloadMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Records the current tenant wiring: namespaces resolve with an empty tenant segment on every path.
 */
class MessageOffloaderTenantWiringSnapshotTest {

    private static final String SESSION = "tenant-snapshot-session";

    @Test
    void contextNamespaceSupplierResolvesWithEmptyTenantSegment() {
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(service, fixedTokenCounter(100));

        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("T".repeat(50), "tc-tenant")
        )).toCompletableFuture().join();

        List<BaseMessage> result = context.getMessages();
        assertThat(result.get(1)).isInstanceOf(OffloadMessage.class);
        OffloadMessage marker = result.get(1) instanceof OffloadMessage om ? om : null;
        assertThat(marker).isNotNull();

        // Snapshot: reload resolves ownership through the same empty-tenant namespace and succeeds.
        Object rawReloader = context.reloaderTool();
        SessionModelContext.ReloaderTool reloader =
                rawReloader instanceof SessionModelContext.ReloaderTool rt ? rt : null;
        assertThat(reloader).isNotNull();
        assertThat(reloader.reloadOriginalContextMessages(marker.getOffloadHandle(), marker.getOffloadType()))
                .contains("T".repeat(50));
    }

    @Test
    void storeKeyPathCarriesTheEmptyTenantSegment() {
        // Snapshot: with the tenant segment empty, the sanitized storage path substitutes "_".
        assertThat(OffloadNamespace.of("", SESSION, "ctx").sanitized().tenantId()).isEqualTo("_");
        assertThat(OffloadNamespace.of("", SESSION, "ctx").toRedisKey("h"))
                .isEqualTo("offload:_:" + SESSION + ":ctx:h");
        assertThat(OffloadNamespace.of(null, SESSION, "ctx").toFsDirName())
                .isEqualTo("_/" + SESSION + "_context");
    }

    @Test
    void tenantlessNamespaceRejectsAnyTenantQualifiedOwner() {
        // Snapshot: ownership matching is segment-wise, so the empty-tenant
        // namespace never matches a tenant-qualified one (FORBIDDEN on reload).
        OffloadService service = memoryService();
        SessionModelContext context = v2Context(service, fixedTokenCounter(100));
        context.addMessages(List.of(
                new UserMessage("u"),
                new ToolMessage("X".repeat(50), "tc-owned")
        )).toCompletableFuture().join();

        OffloadMessage marker = context.getMessages().get(1) instanceof OffloadMessage om ? om : null;
        assertThat(marker).isNotNull();
        OffloadNamespace tenantQualified = OffloadNamespace.of("tenant-a", SESSION, "ctx-v2");
        Optional<OffloadPayload> foreignLoad = java.util.Optional.empty();
        try {
            foreignLoad = Optional.of(service.load(tenantQualified, marker.getOffloadHandle(), "in_memory"));
        } catch (com.openjiuwen.core.context.offload.api.OffloadException expected) {
            // Snapshot: the tenant-qualified owner is rejected rather than served.
        }
        assertThat(foreignLoad).isEmpty();
    }

    private static SessionModelContext v2Context(OffloadService service,
                                                 ModelContext.TokenCounterPort tokenCounter) {
        MessageOffloader offloader = new MessageOffloader(v2Config());
        SessionModelContext context = new SessionModelContext("ctx-v2", SESSION, new ContextEngineConfig(),
                List.of(), List.of(offloader), tokenCounter);
        // Snapshot wiring: replicates the agent's namespace supplier shape —
        // a fixed tenant segment (empty), the live session id and the context id.
        java.util.function.Supplier<OffloadNamespace> namespaceSupplier =
                () -> OffloadNamespace.of("", SESSION, "ctx-v2");
        offloader.setOffloadService(service, namespaceSupplier);
        context.bindOffloadService(service, namespaceSupplier);
        return context;
    }

    private static MessageOffloaderConfig v2Config() {
        MessageOffloaderConfig config = new MessageOffloaderConfig();
        config.setTokensThreshold(10);
        config.setLargeMessageThreshold(10);
        config.setTrimSize(5);
        config.setRecentMessageProtectionCount(0);
        return config;
    }

    private static ModelContext.TokenCounterPort fixedTokenCounter(long tokens) {
        return messages -> (int) tokens;
    }

    private static OffloadService memoryService() {
        LinkedHashMap<OffloadBackend, com.openjiuwen.core.context.offload.api.OffloadStore> chain =
                new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, new MemoryOffloadStore());
        return new OffloadServiceImpl(new OffloadCatalogImpl(), new CompositeOffloadStore(chain),
                new SessionOffloadGate(), new OffloadConfig());
    }
}
