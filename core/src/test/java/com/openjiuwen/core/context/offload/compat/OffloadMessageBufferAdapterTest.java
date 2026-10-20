/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.compat;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.context.OffloadMessageBuffer;
import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Unit tests for {@link OffloadMessageBufferAdapter}: the legacy-buffer API
 * shim routed through the v2 {@link OffloadService} pipeline
 * (架构文档 §2.2 — in_memory 镜像、v2 优先/legacy 兜底、store 链单源).
 */
class OffloadMessageBufferAdapterTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "u1", "s1", "c1");

    @Test
    void offloadInMemoryMirrorsIntoLegacyBufferAndLegacyServesReload() {
        OffloadService service = newService();
        OffloadMessageBuffer legacy = new OffloadMessageBuffer();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(service, NS, legacy);

        List<BaseMessage> original = List.of(new ToolMessage("ctx-payload", "tc-1"));
        adapter.offload("handle-a", "in_memory", original);

        // in_memory lane mirrors the entry into the legacy buffer (saveState/getAll);
        // the v2 chain holds no entry for this handle, so reload resolves through
        // the legacy fallback (v2-first, legacy-fallback).
        assertThat(legacy.getAll()).containsKey("handle-a");
        assertThat(adapter.reloadBlocking("handle-a", "in_memory"))
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(ToolMessage.class,
                        message -> assertThat(message.getContentAsString()).isEqualTo("ctx-payload"));
    }

    @Test
    void reloadPrefersV2StoreChainOverLegacyBuffer() {
        OffloadService service = newService();
        OffloadMessageBuffer legacy = new OffloadMessageBuffer();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(service, NS, legacy);

        // v2-published entry: the legacy buffer never sees it, reload is served
        // from the v2 store chain (single source of truth, 架构文档 §2.2).
        String handle = adapter.saveViaV2("MessageOffloader", "src",
                List.of(new ToolMessage("v2-payload", "tc-2")), 1L).orElseThrow();
        assertThat(legacy.getAll()).doesNotContainKey(handle);

        assertThat(adapter.reloadBlocking(handle, "in_memory"))
                .hasSize(1)
                .first()
                .isInstanceOfSatisfying(ToolMessage.class,
                        message -> assertThat(message.getContentAsString()).isEqualTo("v2-payload"));
    }

    @Test
    void reloadFallsBackToLegacyBufferWhenV2EntryMissing() {
        OffloadService service = newService();
        OffloadMessageBuffer legacy = new OffloadMessageBuffer();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(service, NS, legacy);

        // Pre-v2 entry exists only in the legacy buffer.
        legacy.offload("legacy-handle", "in_memory", List.of(new UserMessage("legacy-only")));

        List<BaseMessage> reloaded = adapter.reloadBlocking("legacy-handle", "in_memory");
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getContentAsString()).isEqualTo("legacy-only");
    }

    @Test
    void reloadUnknownHandleReturnsEmptyListNeverThrows() {
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(newService(), NS);
        assertThat(adapter.reloadBlocking("no-such-handle", "in_memory")).isEmpty();
        assertThat(adapter.reloadBlocking("", "in_memory")).isEmpty();
        assertThat(adapter.reloadBlocking(null, "in_memory")).isEmpty();
    }

    @Test
    void clearRemovesBothV2EntryAndLegacyMirror() {
        OffloadService service = newService();
        OffloadMessageBuffer legacy = new OffloadMessageBuffer();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(service, NS, legacy);

        adapter.offload("handle-c", "in_memory", List.of(new UserMessage("doomed")));
        assertThat(legacy.getAll()).containsKey("handle-c");

        adapter.clear("handle-c", "in_memory");

        assertThat(legacy.getAll()).doesNotContainKey("handle-c");
        assertThat(adapter.reloadBlocking("handle-c", "in_memory")).isEmpty();
    }

    @Test
    void saveViaV2ReturnsHandleAndRoundTripsThroughLoad() {
        OffloadService service = newService();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(service, NS);

        String handle = adapter.saveViaV2("MessageOffloader", "tool-call-1",
                List.of(new UserMessage("structured")), 42L).orElseThrow();

        assertThat(handle).isNotBlank();
        com.openjiuwen.core.context.offload.model.OffloadPayload payload =
                service.load(NS, handle, "in_memory");
        assertThat(payload.messages()).hasSize(1);
        assertThat(payload.toBaseMessages().get(0).getContentAsString()).isEqualTo("structured");
    }

    @Test
    void nonInMemoryOffloadTypesDoNotTouchLegacyBuffer() {
        OffloadMessageBuffer legacy = new OffloadMessageBuffer();
        OffloadMessageBufferAdapter adapter = new OffloadMessageBufferAdapter(newService(), NS, legacy);

        adapter.offload("handle-fs", "filesystem", List.of(new UserMessage("fs-owned")));

        assertThat(legacy.getAll()).doesNotContainKey("handle-fs");
    }

    private static OffloadService newService() {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, new MemoryOffloadStore());
        return new OffloadServiceImpl(new OffloadCatalogImpl(),
                new CompositeOffloadStore(chain),
                new SessionOffloadGate(),
                new OffloadConfig());
    }
}
