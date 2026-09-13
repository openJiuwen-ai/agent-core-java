/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.spi.store.BaseKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceCheckpointerTenantTest {

    private InMemoryKVStore kvStore;
    private PersistenceCheckpointer checkpointer;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        kvStore = new InMemoryKVStore();
        checkpointer = new PersistenceCheckpointer(kvStore);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    @Test
    void testAgentStateKey_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String sessionId = "sessionId";
        String agentId = "agentId";
        String rawKey = Checkpointer.buildKeyWithNamespace(sessionId,
                Checkpointer.SESSION_NAMESPACE_AGENT, agentId, "agent_state_blobs");
        String resolvedKey = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKey).isEqualTo("tenantA:sessionId:agent:agentId:agent_state_blobs");

        kvStore.set(resolvedKey, "some_value");
        assertThat(kvStore.isExists(resolvedKey)).isTrue();
    }

    @Test
    void testAgentStateKey_noTenant() {
        String sessionId = "sessionId";
        String agentId = "agentId";
        String rawKey = Checkpointer.buildKeyWithNamespace(sessionId,
                Checkpointer.SESSION_NAMESPACE_AGENT, agentId, "agent_state_blobs");
        String resolvedKey = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKey).isEqualTo("sessionId:agent:agentId:agent_state_blobs");

        kvStore.set(resolvedKey, "some_value");
        assertThat(kvStore.isExists(resolvedKey)).isTrue();
    }

    @Test
    void testWorkflowStateKey_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String sessionId = "sessionId";
        String workflowId = "workflowId";
        String rawKey = Checkpointer.buildKeyWithNamespace(sessionId,
                Checkpointer.SESSION_NAMESPACE_WORKFLOW, workflowId, "workflow_state_blobs");
        String resolvedKey = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKey).isEqualTo("tenantA:sessionId:workflow:workflowId:workflow_state_blobs");

        kvStore.set(resolvedKey, "workflow_data");
        assertThat(kvStore.isExists(resolvedKey)).isTrue();
    }

    @Test
    void testGraphStateKey_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String sessionId = "sessionId";
        String ns = "graphNs";
        String rawKey = Checkpointer.buildKeyWithNamespace(sessionId,
                Checkpointer.WORKFLOW_NAMESPACE_GRAPH, ns, "checkpoint_data_value");
        String resolvedKey = TenantKVStoreKeyResolver.resolveKey(rawKey);
        assertThat(resolvedKey).isEqualTo("tenantA:sessionId:workflow-graph:graphNs:checkpoint_data_value");

        kvStore.set(resolvedKey, "graph_data");
        assertThat(kvStore.isExists(resolvedKey)).isTrue();
    }

    @Test
    void testRelease_withTenant_deletesByPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:sessionId:agent:agentId:agent_state_blobs", "data1");
        kvStore.set("tenantA:sessionId:agent:agentId:agent_state_blobs_dump_type", "data2");
        kvStore.set("tenantA:sessionId:workflow:workflowId:workflow_state_blobs", "data3");
        kvStore.set("tenantB:sessionId:agent:agentId:agent_state_blobs", "data_other_tenant");

        assertThat(kvStore.getByPrefix("tenantA:sessionId:")).hasSize(3);

        checkpointer.release("sessionId");

        assertThat(kvStore.getByPrefix("tenantA:sessionId:")).isEmpty();
        assertThat(kvStore.isExists("tenantB:sessionId:agent:agentId:agent_state_blobs")).isTrue();
    }

    @Test
    void testSessionExists_withTenant() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:sessionId:agent:agentId:agent_state_blobs", "data1");

        assertThat(checkpointer.sessionExists("sessionId")).isTrue();

        TenantContextHolder.clearCurrentTenant();
        assertThat(checkpointer.sessionExists("sessionId")).isFalse();
    }

    @Test
    void testTenantIsolation_sameSession_differentTenants() {
        String sessionId = "sharedSession";
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        kvStore.set("tenantA:" + sessionId + ":agent:agent1:agent_state_blobs", "tenantA_data");
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        assertThat(checkpointer.sessionExists(sessionId)).isFalse();

        kvStore.set("tenantB:" + sessionId + ":agent:agent1:agent_state_blobs", "tenantB_data");
        assertThat(checkpointer.sessionExists(sessionId)).isTrue();

        assertThat(kvStore.get("tenantA:" + sessionId + ":agent:agent1:agent_state_blobs")).isEqualTo("tenantA_data");
        assertThat(kvStore.get("tenantB:" + sessionId + ":agent:agent1:agent_state_blobs")).isEqualTo("tenantB_data");
    }
}
