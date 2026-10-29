/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * TC_081: consecutive saves yield globally unique 32-char hex handles that are never reused.
 */
class OffloadServiceImplHandleUniquenessTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");
    private static final Pattern HANDLE_32_HEX = Pattern.compile("^[0-9a-f]{32}$");

    private static OffloadService newService() {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, new MemoryOffloadStore());
        return new OffloadServiceImpl(new OffloadCatalogImpl(), new CompositeOffloadStore(chain),
                new SessionOffloadGate(), new OffloadConfig());
    }

    private static OffloadRequest request(String content) {
        return OffloadRequest.builder()
                .namespace(NS)
                .algorithmId("MessageOffloader")
                .source("tc081")
                .messages(List.of(new ToolMessage(content, "tc-1")))
                .preTokenCount(1L)
                .build();
    }

    @Test
    void consecutiveSavesYieldDistinct32HexHandles() {
        OffloadService service = newService();
        Optional<OffloadRef> first = service.save(request("first-payload"));
        Optional<OffloadRef> second = service.save(request("second-payload"));
        assertTrue(first.isPresent() && second.isPresent());
        String firstHandle = first.get().handle();
        String secondHandle = second.get().handle();
        assertTrue(HANDLE_32_HEX.matcher(firstHandle).matches(),
                "handle must be 32 lowercase hex: " + firstHandle);
        assertTrue(HANDLE_32_HEX.matcher(secondHandle).matches(),
                "handle must be 32 lowercase hex: " + secondHandle);
        assertTrue(!firstHandle.equals(secondHandle), "handles must differ");
    }

    @Test
    void identicalContentStillYieldsDistinctHandles() {
        // TC_053-UT: 同一内容两次 save——句柄互异且载荷逐字一致（save 凭据
        // pathOrKey 各自有效），marker 预览仅句柄段不同。
        OffloadService service = newService();
        String firstHandle = service.save(request("same")).orElseThrow().handle();
        String secondHandle = service.save(request("same")).orElseThrow().handle();
        assertTrue(!firstHandle.equals(secondHandle), "identical payloads must never reuse a handle");

        com.openjiuwen.core.context.offload.model.OffloadPayload firstPayload =
                service.load(NS, firstHandle, "in_memory");
        com.openjiuwen.core.context.offload.model.OffloadPayload secondPayload =
                service.load(NS, secondHandle, "in_memory");
        assertEquals(firstPayload.serializeJson().length(), secondPayload.serializeJson().length());
        assertEquals(firstPayload.toBaseMessages().size(), secondPayload.toBaseMessages().size());
        assertEquals(firstPayload.toBaseMessages().get(0).getContentAsString(),
                secondPayload.toBaseMessages().get(0).getContentAsString(),
                "identical inputs must produce byte-identical payloads");
    }

    @Test
    void handlesStayUniqueAcrossServicesAndRounds() {
        Set<String> handles = new HashSet<>();
        for (int s = 0; s < 2; s++) {
            OffloadService service = newService();
            for (int r = 0; r < 8; r++) {
                handles.add(service.save(request("svc" + s + "-round" + r)).orElseThrow().handle());
            }
        }
        assertEquals(16, handles.size(), "all 16 handles across services must be unique");
    }
}
