/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checkpointer 层面的多租户隔离测试，对应设计 §10.1。
 */
class TenantCheckpointerIsolationTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearTenantBefore() {
        TenantContextHolder.clearCurrentTenant();
    }

    @AfterEach
    void clearTenantAfter() {
        TenantContextHolder.clearCurrentTenant();
    }

    private static String invokeTenantAwareSessionId(InMemoryCheckpointer cp, String sessionId) throws Exception {
        Method m = InMemoryCheckpointer.class.getDeclaredMethod("tenantAwareSessionId", String.class);
        m.setAccessible(true);
        return (String) m.invoke(cp, sessionId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getAgentStores(InMemoryCheckpointer cp) throws Exception {
        Field f = InMemoryCheckpointer.class.getDeclaredField("agentStores");
        f.setAccessible(true);
        return (Map<String, Object>) (Map<?, ?>) f.get(cp);
    }

    @Test
    void testInMemoryCheckpointer_tenantIsolation() throws Exception {
        InMemoryCheckpointer cp = new InMemoryCheckpointer();
        String sessionId = "session-shared";

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        String keyA = invokeTenantAwareSessionId(cp, sessionId);
        assertThat(keyA).isEqualTo("tenant_a:session-shared");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());
        String keyB = invokeTenantAwareSessionId(cp, sessionId);
        assertThat(keyB).isEqualTo("tenant_b:session-shared");

        assertThat(keyA).isNotEqualTo(keyB);

        Map<String, Object> agentStores = getAgentStores(cp);
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        agentStores.put(invokeTenantAwareSessionId(cp, sessionId), "marker_a");
        assertThat(cp.sessionExists(sessionId)).isTrue();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());
        assertThat(cp.sessionExists(sessionId)).isFalse();
    }

    @Test
    void testPersistenceCheckpointer_tenantIsolation() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        PersistenceCheckpointer cp = new PersistenceCheckpointer(kvStore);
        String sessionId = "session-shared";
        String agentId = "agent-1";

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        String rawKey = Checkpointer.buildKeyWithNamespace(sessionId,
                Checkpointer.SESSION_NAMESPACE_AGENT, agentId, "agent_state_blobs");
        String resolvedKeyA = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKeyA).isEqualTo("tenant_a:session-shared:agent:agent-1:agent_state_blobs");
        kvStore.set(resolvedKeyA, "data_a");
        assertThat(cp.sessionExists(sessionId)).isTrue();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_b").build());
        String resolvedKeyB = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKeyB).isEqualTo("tenant_b:session-shared:agent:agent-1:agent_state_blobs");
        assertThat(resolvedKeyA).isNotEqualTo(resolvedKeyB);
        assertThat(cp.sessionExists(sessionId)).isFalse();

        kvStore.set(resolvedKeyB, "data_b");
        assertThat(cp.sessionExists(sessionId)).isTrue();
        assertThat(kvStore.get(resolvedKeyA)).isEqualTo("data_a");
        assertThat(kvStore.get(resolvedKeyB)).isEqualTo("data_b");
    }

    @Test
    @Disabled("需 Redis 环境时启用：RedisCheckpointer 依赖真实 RedisStore/Redis 客户端")
    void testRedisCheckpointer_tenantIsolation() {
        // 骨架：当 Redis 可用时，验证 RedisCheckpointer 的租户隔离。
        // RedisCheckpointer.sessionExists 与 release 通过
        //   TenantKVStoreKeyResolver.resolvePrefix(sessionId + ":")
        // 计算 tenantId: 前缀，预期行为：
        //   1) tenant_a 下写入 "tenant_a:session-1:agent:..." 键 → sessionExists("session-1") 为 true
        //   2) 切换 tenant_b 后 sessionExists("session-1") 为 false（不同租户同 sessionId 隔离）
        //   3) release("session-1") 在 tenant_a 下仅删除 "tenant_a:session-1:" 前缀键，tenant_b 数据保留
        // 启用方式：构造 RedisStore(RedisClient) → new RedisCheckpointer(redisStore, ttl) 后填入上述断言。
        assertThat(true).as("Redis 隔离骨架占位").isTrue();
    }

    @Test
    void testCheckpointer_releaseByPrefix_deletesTenantData() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        PersistenceCheckpointer cp = new PersistenceCheckpointer(kvStore);
        String sessionId = "session-1";

        kvStore.set("tenant_a:session-1:agent:agent-1:agent_state_blobs", "a_blob");
        kvStore.set("tenant_a:session-1:agent:agent-1:agent_state_blobs_dump_type", "a_type");
        kvStore.set("tenant_a:session-1:workflow:wf-1:workflow_state_blobs", "a_wf");
        kvStore.set("tenant_b:session-1:agent:agent-1:agent_state_blobs", "b_blob");
        kvStore.set("tenant_b:session-1:workflow:wf-1:workflow_state_blobs", "b_wf");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenant_a").build());
        assertThat(kvStore.getByPrefix("tenant_a:session-1:")).hasSize(3);
        cp.release(sessionId);

        assertThat(kvStore.getByPrefix("tenant_a:session-1:")).isEmpty();
        assertThat(kvStore.isExists("tenant_b:session-1:agent:agent-1:agent_state_blobs")).isTrue();
        assertThat(kvStore.isExists("tenant_b:session-1:workflow:wf-1:workflow_state_blobs")).isTrue();
        assertThat(kvStore.getByPrefix("tenant_b:session-1:")).hasSize(2);
    }

    @Test
    void testCheckpointer_backwardCompat_noTenant() throws Exception {
        assertThat(tempDir).isDirectory();

        TenantContext noTenant = TenantContext.builder().tenantId(null).build();
        assertThat(noTenant.isTenantAware()).isFalse();
        String key = Checkpointer.buildKeyWithTenant(noTenant, "session-1",
                Checkpointer.SESSION_NAMESPACE_AGENT, "agent-1", "agent_state_blobs");
        assertThat(key).isEqualTo("session-1:agent:agent-1:agent_state_blobs");

        assertThat(TenantKVStoreKeyResolver.resolveKey("session-1:agent:agent-1:agent_state_blobs"))
                .isEqualTo("session-1:agent:agent-1:agent_state_blobs");
        assertThat(TenantKVStoreKeyResolver.resolvePrefix("session-1:"))
                .isEqualTo("session-1:");

        InMemoryKVStore kvStore = new InMemoryKVStore();
        PersistenceCheckpointer cp = new PersistenceCheckpointer(kvStore);
        kvStore.set("session-1:agent:agent-1:agent_state_blobs", "data");
        assertThat(cp.sessionExists("session-1")).isTrue();
        cp.release("session-1");
        assertThat(cp.sessionExists("session-1")).isFalse();

        InMemoryCheckpointer inMem = new InMemoryCheckpointer();
        assertThat(invokeTenantAwareSessionId(inMem, "session-1")).isEqualTo("session-1");
        assertThat(inMem.sessionExists("session-1")).isFalse();
    }
}
