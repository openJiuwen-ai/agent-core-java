/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadErrorCode;
import com.openjiuwen.core.context.offload.model.OffloadException;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRef;
import com.openjiuwen.core.context.offload.model.OffloadRequest;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies concurrent tenants sharing one session id stay isolated and serialize through the session gate.
 */
class OffloadServiceCrossTenantConcurrencyTest {

    private static final String SHARED_SESSION = "shared-session";
    private static final OffloadNamespace TENANT_A_NS =
            OffloadNamespace.of("tenant-a", SHARED_SESSION, "ctx-shared");
    private static final OffloadNamespace TENANT_B_NS =
            OffloadNamespace.of("tenant-b", SHARED_SESSION, "ctx-shared");

    private final MemoryOffloadStore memory = new MemoryOffloadStore();
    private final OffloadCatalogImpl catalog = new OffloadCatalogImpl();
    private final SessionOffloadGate gate = new SessionOffloadGate();
    private final OffloadServiceImpl service = new OffloadServiceImpl(catalog, compositeStore(), gate,
            new OffloadConfig());

    private CompositeOffloadStore compositeStore() {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.MEMORY, memory);
        return new CompositeOffloadStore(chain);
    }

    private static OffloadRequest request(OffloadNamespace ns, String content) {
        List<BaseMessage> messages = new ArrayList<>();
        messages.add(new UserMessage(content));
        return OffloadRequest.builder()
                .namespace(ns)
                .algorithmId("MessageOffloader")
                .source(content)
                .messages(messages)
                .build();
    }

    @Test
    void crossTenantConcurrentWorkOnSharedSessionStaysIsolated() throws Exception {
        int rounds = 40;
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new ArrayList<>();
        Map<String, String> handlesByTenant = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Future<?> tenantA = pool.submit(() -> runTenantRounds(TENANT_A_NS, "tenant-a payload", rounds,
                    ready, successes, handlesByTenant, failures));
            Future<?> tenantB = pool.submit(() -> runTenantRounds(TENANT_B_NS, "tenant-b payload", rounds,
                    ready, successes, handlesByTenant, failures));
            ready.countDown();
            tenantA.get(30, TimeUnit.SECONDS);
            tenantB.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(rounds * 4, successes.get(),
                "every cross-tenant save and verified load must succeed");

        // Lock granularity is session-scoped: both tenants sharing a session id map onto one gate lock.
        assertEquals(1, gate.lockCount(), "the shared session id must serialize both tenants on one lock");
        assertEquals(rounds, catalog.listByNamespace(TENANT_A_NS).size());
        assertEquals(rounds, catalog.listByNamespace(TENANT_B_NS).size());
        assertEquals(0, failures.size());
    }

    private void runTenantRounds(OffloadNamespace ns, String content, int rounds, CountDownLatch ready,
            AtomicInteger successes, Map<String, String> handlesByTenant, List<Throwable> failures) {
        try {
            ready.await();
            for (int round = 0; round < rounds; round++) {
                Optional<OffloadRef> ref = service.save(request(ns, content + " " + round));
                if (ref.isPresent()) {
                    successes.incrementAndGet();
                    handlesByTenant.put(ns.tenantId(), ref.get().handle());
                    OffloadPayload loaded = service.load(ns, ref.get().handle(), "MessageOffloader");
                    if (loaded.messages().size() == 1) {
                        successes.incrementAndGet();
                    }
                }
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            synchronized (failures) {
                failures.add(ex);
            }
        } catch (RuntimeException ex) {
            synchronized (failures) {
                failures.add(ex);
            }
        }
    }

    @Test
    void foreignTenantAccessAndNamespaceCleanupStayScoped() {
        Optional<OffloadRef> tenantARef = service.save(request(TENANT_A_NS, "tenant-a secret"));
        Optional<OffloadRef> tenantBRef = service.save(request(TENANT_B_NS, "tenant-b secret"));
        assertTrue(tenantARef.isPresent() && tenantBRef.isPresent());
        String handleA = tenantARef.get().handle();
        String handleB = tenantBRef.get().handle();

        OffloadException forbidden = assertThrows(OffloadException.class,
                () -> service.load(TENANT_B_NS, handleA, "MessageOffloader"));
        assertEquals(OffloadErrorCode.OFFLOAD_FORBIDDEN, forbidden.code());
        assertTrue(service.delete(TENANT_B_NS, handleA) == false,
                "a foreign tenant must not delete another tenant's entry");

        // Cleanup stays scoped: purging one tenant leaves the other tenant's entries intact.
        service.deleteNamespace(TENANT_A_NS);
        assertEquals(0, catalog.listByNamespace(TENANT_A_NS).size());
        assertTrue(memory.exists(TENANT_B_NS, handleB));
        assertEquals(1, catalog.listByNamespace(TENANT_B_NS).size());
        OffloadPayload stillReadable = service.load(TENANT_B_NS, handleB, "MessageOffloader");
        assertEquals(1, stillReadable.messages().size());
    }
}
