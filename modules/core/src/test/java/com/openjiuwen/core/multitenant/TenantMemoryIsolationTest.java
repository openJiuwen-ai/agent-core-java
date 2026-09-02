/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.common.DistributedLock;
import com.openjiuwen.core.memory.common.MemoryUtils;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multitenant isolation tests for the memory subsystem covering LongTermMemory,
 * UserMemStore, VariableManager, DistributedLock and MemoryUtils.
 *
 * <p>These tests verify that KV keys produced by the memory modules are properly
 * prefixed with the active tenant identifier via {@link TenantKVStoreKeyResolver}
 * and that data from different tenants cannot leak across boundaries.
 */
class TenantMemoryIsolationTest {

    @TempDir
    Path tempDir;

    private TestInMemoryKVStore kvStore;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        kvStore = new TestInMemoryKVStore();
        LongTermMemory.resetInstance();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
        LongTermMemory.resetInstance();
    }

    // ========================= LongTermMemory KV key prefix (3) =========================

    @Test
    void testLongTermMemory_writeWithTenant_keyPrefixed() throws Exception {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        LongTermMemory memory = LongTermMemory.getInstance();
        injectStoresForScopeConfig(memory);

        boolean ok = memory.setScopeConfig("scope1", MemoryScopeConfig.builder().build());

        assertThat(ok).isTrue();
        assertThat(kvStore.isExists("tenantA:memory_scope_config/scope1")).isTrue();
        assertThat(kvStore.isExists("memory_scope_config/scope1")).isFalse();
    }

    @Test
    void testLongTermMemory_readWithTenant_keyPrefixed() throws Exception {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        LongTermMemory memory = LongTermMemory.getInstance();
        injectStoresForScopeConfig(memory);

        memory.setScopeConfig("scope1", MemoryScopeConfig.builder().build());

        MemoryScopeConfig loaded = memory.getScopeConfig("scope1");

        assertThat(loaded).isNotNull();
        assertThat(kvStore.isExists("tenantA:memory_scope_config/scope1")).isTrue();
        assertThat(kvStore.isExists("memory_scope_config/scope1")).isFalse();
    }

    @Test
    void testLongTermMemory_deleteWithTenant_onlyDeletesTenantData() throws Exception {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        LongTermMemory memory = LongTermMemory.getInstance();
        injectStoresForScopeConfig(memory);
        memory.setScopeConfig("scope1", MemoryScopeConfig.builder().build());

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        memory.setScopeConfig("scope1", MemoryScopeConfig.builder().build());

        assertThat(kvStore.isExists("tenantA:memory_scope_config/scope1")).isTrue();
        assertThat(kvStore.isExists("tenantB:memory_scope_config/scope1")).isTrue();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        boolean deleted = memory.deleteScopeConfig("scope1");

        assertThat(deleted).isTrue();
        assertThat(kvStore.isExists("tenantA:memory_scope_config/scope1")).isFalse();
        assertThat(kvStore.isExists("tenantB:memory_scope_config/scope1")).isTrue();
    }

    // ========================= UserMemStore (3) =========================

    @Test
    void testUserMemStore_getConcatenationKey_withTenant_prefixed() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        UserMemStore store = new UserMemStore(kvStore);
        String memId = "000000000000000000000001";

        boolean result = store.write("user1", "scope1", memId,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "data"));

        assertThat(result).isTrue();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/" + memId)).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/" + memId)).isFalse();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/user_profile/ids")).isTrue();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/UPT/ids")).isTrue();
    }

    @Test
    void testUserMemStore_differentTenants_isolated() {
        String memIdA = "000000000000000000000001";
        String memIdB = "000000000000000000000002";

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        UserMemStore storeA = new UserMemStore(kvStore);
        storeA.write("user1", "scope1", memIdA,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "A"));

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        UserMemStore storeB = new UserMemStore(kvStore);
        storeB.write("user1", "scope1", memIdB,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "B"));

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        assertThat(storeA.get("user1", "scope1", memIdA)).isNotNull();
        assertThat(storeA.get("user1", "scope1", memIdB)).isNull();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        assertThat(storeB.get("user1", "scope1", memIdB)).isNotNull();
        assertThat(storeB.get("user1", "scope1", memIdA)).isNull();
    }

    @Test
    void testUserMemStore_noTenant_backwardCompat() {
        UserMemStore store = new UserMemStore(kvStore);
        String memId = "000000000000000000000001";

        boolean result = store.write("user1", "scope1", memId,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "data"));

        assertThat(result).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/" + memId)).isTrue();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/" + memId)).isFalse();
    }

    // ========================= VariableManager (4) =========================

    @Test
    void testVariableManager_userVariable_withTenant_prefixed() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vm = new VariableManager(kvStore, new byte[0]);

        vm.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("var1").variableMem("v1").build()),
                null, null);

        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/var1")).isTrue();
        assertThat(kvStore.isExists("user_var/user1/scope1/var1")).isFalse();
    }

    @Test
    void testVariableManager_sessionVariable_withTenant_prefixed() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vm = new VariableManager(kvStore, new byte[0]);

        kvStore.set("tenantA:session_var/user1/scope1/session1/var1", "tenant_value");
        kvStore.set("session_var/user1/scope1/session1/var1", "non_tenant_value");

        Map<String, String> result = vm.queryVariable("user1", "scope1", "var1", "session1");

        assertThat(result).isNotNull();
        assertThat(result.get("var1")).isEqualTo("tenant_value");

        assertThat(kvStore.isExists("tenantA:session_var/user1/scope1/session1/var1")).isTrue();
    }

    @Test
    void testVariableManager_differentTenants_isolated() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vmA = new VariableManager(kvStore, new byte[0]);
        vmA.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varA").variableMem("A_value").build()),
                null, null);

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        VariableManager vmB = new VariableManager(kvStore, new byte[0]);
        vmB.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varB").variableMem("B_value").build()),
                null, null);

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        Map<String, String> resultA = vmA.queryVariable("user1", "scope1", null, null);
        assertThat(resultA).containsKey("varA");
        assertThat(resultA).doesNotContainKey("varB");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        Map<String, String> resultB = vmB.queryVariable("user1", "scope1", null, null);
        assertThat(resultB).containsKey("varB");
        assertThat(resultB).doesNotContainKey("varA");
    }

    @Test
    void testVariableManager_noTenant_backwardCompat() {
        VariableManager vm = new VariableManager(kvStore, new byte[0]);
        vm.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("var1").variableMem("v1").build()),
                null, null);

        assertThat(kvStore.isExists("user_var/user1/scope1/var1")).isTrue();
        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/var1")).isFalse();
    }

    // ========================= DistributedLock (2) =========================

    @Test
    void testDistributedLock_lockKey_withTenant_prefixed() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        DistributedLock lock = new DistributedLock(kvStore, "user/user1");
        try {
            lock.acquire();
            assertThat(kvStore.isExists("tenantA:_lock/user/user1")).isTrue();
            assertThat(kvStore.isExists("_lock/user/user1")).isFalse();
        } finally {
            lock.release();
        }
    }

    @Test
    void testDistributedLock_differentTenants_lockNotShared() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        DistributedLock lockA = new DistributedLock(kvStore, "user/user1");
        lockA.acquire();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        DistributedLock lockB = new DistributedLock(kvStore, "user/user1");
        lockB.acquire();

        try {
            assertThat(kvStore.isExists("tenantA:_lock/user/user1")).isTrue();
            assertThat(kvStore.isExists("tenantB:_lock/user/user1")).isTrue();
        } finally {
            lockB.release();
            lockA.release();
        }
    }

    // ========================= MemoryUtils (2) =========================

    @Test
    void testGenerateTenantAwareIdxName_withTenant_prefixed() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String idxName = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");

        assertThat(idxName).isEqualTo("tenantA_uid_user1_gid_scope1_mtype_user_profile");
        assertThat(idxName).startsWith("tenantA_");
    }

    @Test
    void testGenerateTenantAwareIdxName_noTenant_unprefixed() {
        String idxName = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");

        assertThat(idxName).isEqualTo("uid_user1_gid_scope1_mtype_user_profile");
        assertThat(idxName).doesNotStartWith("tenantA_");
    }

    // ========================= Backward compat (1) =========================

    @Test
    void testMemory_backwardCompat_noTenant() throws Exception {
        assertThat(TenantContextHolder.getCurrentTenant()).isNull();

        LongTermMemory memory = LongTermMemory.getInstance();
        injectStoresForScopeConfig(memory);
        memory.setScopeConfig("scope1", MemoryScopeConfig.builder().build());
        assertThat(kvStore.isExists("memory_scope_config/scope1")).isTrue();
        assertThat(kvStore.isExists("tenantA:memory_scope_config/scope1")).isFalse();

        UserMemStore userStore = new UserMemStore(kvStore);
        String memId = "000000000000000000000001";
        userStore.write("user1", "scope1", memId,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "data"));
        assertThat(kvStore.isExists("UMD/user1/scope1/" + memId)).isTrue();

        VariableManager vm = new VariableManager(kvStore, new byte[0]);
        vm.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("var1").variableMem("v1").build()),
                null, null);
        assertThat(kvStore.isExists("user_var/user1/scope1/var1")).isTrue();

        DistributedLock lock = new DistributedLock(kvStore, "user/user1");
        try {
            lock.acquire();
            assertThat(kvStore.isExists("_lock/user/user1")).isTrue();
        } finally {
            lock.release();
        }

        String idxName = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");
        assertThat(idxName).isEqualTo("uid_user1_gid_scope1_mtype_user_profile");

        assertThat(tempDir).isNotNull();
        assertThat(tempDir.toFile()).exists();
    }

    // ========================= Helpers =========================

    /**
     * Injects the minimum state required by {@link LongTermMemory#setScopeConfig},
     * {@link LongTermMemory#getScopeConfig} and {@link LongTermMemory#deleteScopeConfig}
     * without invoking the full {@code registerStore} pipeline (which would pull in
     * H2 / vector store / embedding dependencies). Reflection is used solely for setup;
     * the actual test invokes the public API.
     */
    private void injectStoresForScopeConfig(LongTermMemory memory) throws Exception {
        Field kvStoreField = LongTermMemory.class.getDeclaredField("kvStore");
        kvStoreField.setAccessible(true);
        kvStoreField.set(memory, kvStore);

        Field sysMemConfigField = LongTermMemory.class.getDeclaredField("sysMemConfig");
        sysMemConfigField.setAccessible(true);
        sysMemConfigField.set(memory, MemoryEngineConfig.builder().build());
    }
}
