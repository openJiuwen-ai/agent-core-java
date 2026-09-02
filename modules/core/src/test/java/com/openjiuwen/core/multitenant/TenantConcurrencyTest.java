package com.openjiuwen.core.multitenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.cwd.CwdContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@DisplayName("Tenant Concurrency Tests")
class TenantConcurrencyTest {

    @TempDir
    Path dataRoot;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Nested
    @DisplayName("Concurrent TenantContextHolder")
    class ConcurrentContextHolder {
        @Test
        @DisplayName("Different threads with different tenants don't interfere")
        void testDifferentTenantsDifferentThreads() throws Exception {
            ExecutorService executor = ExecutorServiceWrapper.newFixedThreadPool(10);
            ConcurrentHashMap<String, String> capturedTenantIds = new ConcurrentHashMap<>();
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                String tenantId = "tenant_" + i;
                futures.add(executor.submit(() -> {
                    TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId(tenantId).build());
                    capturedTenantIds.put(Thread.currentThread().getName(), tenantId);
                    Thread.sleep(50);
                    TenantContext ctx = TenantContextHolder.getCurrentTenant();
                    assertThat(ctx.getTenantId()).isEqualTo(tenantId);
                    TenantContextHolder.clearCurrentTenant();
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertThat(capturedTenantIds.size()).isEqualTo(10);
        }

        @Test
        @DisplayName("InheritableThreadLocal propagates to child threads")
        void testInheritableThreadLocalPropagation() throws Exception {
            TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("parent_tenant").build());

            AtomicReference<String> childTenantId = new AtomicReference<>();
            Thread childThread = new Thread(() -> {
                TenantContext ctx = TenantContextHolder.getCurrentTenant();
                childTenantId.set(ctx != null ? ctx.getTenantId() : null);
            });
            childThread.start();
            childThread.join(5000);

            assertThat(childTenantId.get()).isEqualTo("parent_tenant");
            assertThat(TenantContextHolder.getCurrentTenant().getTenantId()).isEqualTo("parent_tenant");

            TenantContextHolder.clearCurrentTenant();
        }
    }

    @Nested
    @DisplayName("Concurrent TenantWorkspaceResolver")
    class ConcurrentWorkspaceResolver {
        @Test
        @DisplayName("Concurrent initializeTenantSpace for same tenant is safe")
        void testConcurrentInitSameTenant() throws Exception {
            TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(dataRoot.toString());
            TenantContext tenant = TenantContext.builder().tenantId("concurrent_tenant").build();

            ExecutorService executor = ExecutorServiceWrapper.newFixedThreadPool(8);
            List<Future<Path>> futures = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                futures.add(executor.submit(() -> {
                    resolver.initializeTenantSpace(tenant);
                    return resolver.resolveWorkspaceRoot(tenant);
                }));
            }

            List<Path> results = new ArrayList<>();
            for (Future<Path> f : futures) {
                results.add(f.get(5, TimeUnit.SECONDS));
            }
            executor.shutdown();

            assertThat(results).allSatisfy(path -> assertThat(path).isEqualTo(results.get(0)));
            assertThat(Files.exists(results.get(0))).isTrue();
        }

        @Test
        @DisplayName("Different tenants concurrent access doesn't mix data")
        void testDifferentTenantsConcurrentNoMix() throws Exception {
            TenantWorkspaceResolver resolver = new TenantWorkspaceResolver(dataRoot.toString());

            ExecutorService executor = ExecutorServiceWrapper.newFixedThreadPool(4);
            ConcurrentHashMap<String, String> workspacePaths = new ConcurrentHashMap<>();
            List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                String tenantId = "mix_tenant_" + i;
                TenantContext ctx = TenantContext.builder().tenantId(tenantId).build();
                futures.add(executor.submit(() -> {
                    resolver.initializeTenantSpace(ctx);
                    Path ws = resolver.resolveWorkspaceRoot(ctx);
                    workspacePaths.put(tenantId, ws.toString());
                    assertThat(ws.toString()).contains(tenantId);
                    return null;
                }));
            }

            for (Future<Void> f : futures) {
                f.get(5, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertThat(workspacePaths.size()).isEqualTo(10);
            workspacePaths.forEach((tid, path) -> assertThat(path).contains(tid));
        }
    }

    private static class ExecutorServiceWrapper {
        static ExecutorService newFixedThreadPool(int n) {
            return java.util.concurrent.Executors.newFixedThreadPool(n);
        }
    }
}
