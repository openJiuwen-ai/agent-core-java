/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory;

import com.openjiuwen.core.memory.common.DistributedLock;
import com.openjiuwen.core.memory.common.MemoryUtils;
import com.openjiuwen.core.memory.manage.index.VariableManager;
import com.openjiuwen.core.memory.manage.mem_model.MemoryType;
import com.openjiuwen.core.memory.manage.mem_model.UserMemStore;
import com.openjiuwen.core.memory.manage.mem_model.VariableUnit;
import com.openjiuwen.core.memory.support.TestInMemoryKVStore;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantMemoryIsolationTest {

    private TestInMemoryKVStore kvStore;

    @BeforeEach
    void setUp() {
        TenantContextHolder.clearCurrentTenant();
        kvStore = new TestInMemoryKVStore();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clearCurrentTenant();
    }

    // ========================= LongTermMemory KVStore Key =========================

    @Test
    void scopeConfigKey_withTenant_hasTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String configKey = TenantKVStoreKeyResolver.resolveKey("memory_scope_config/scope1");
        assertThat(configKey).isEqualTo("tenantA:memory_scope_config/scope1");
        kvStore.set(configKey, "test_value");
        assertThat(kvStore.get(configKey)).isEqualTo("test_value");
    }

    @Test
    void scopeConfigKey_noTenant_backwardCompat() {
        String configKey = TenantKVStoreKeyResolver.resolveKey("memory_scope_config/scope1");
        assertThat(configKey).isEqualTo("memory_scope_config/scope1");
        kvStore.set(configKey, "test_value");
        assertThat(kvStore.get(configKey)).isEqualTo("test_value");
    }

    // ========================= UserMemStore KVStore Key =========================

    @Test
    void userMemStoreKey_withTenant_hasTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        UserMemStore store = new UserMemStore(kvStore);
        String memId = "000000000000000000000001";
        boolean result = store.write("user1", "scope1", memId,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "profile_content"));
        assertThat(result).isTrue();

        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/000000000000000000000001")).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/000000000000000000000001")).isFalse();

        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/user_profile/ids")).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/user_profile/ids")).isFalse();

        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/UPT/ids")).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/UPT/ids")).isFalse();
    }

    @Test
    void userMemStoreKey_noTenant_backwardCompat() {
        UserMemStore store = new UserMemStore(kvStore);
        String memId = "000000000000000000000001";
        boolean result = store.write("user1", "scope1", memId,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "profile_content"));
        assertThat(result).isTrue();

        assertThat(kvStore.isExists("UMD/user1/scope1/000000000000000000000001")).isTrue();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/000000000000000000000001")).isFalse();

        assertThat(kvStore.isExists("UMD/user1/scope1/user_profile/ids")).isTrue();
        assertThat(kvStore.isExists("UMD/user1/scope1/UPT/ids")).isTrue();
    }

    @Test
    void userMemStoreKey_isolationBetweenTenants() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        UserMemStore storeA = new UserMemStore(kvStore);
        String memId1 = "000000000000000000000001";
        storeA.write("user1", "scope1", memId1,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "tenantA_content"));

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        UserMemStore storeB = new UserMemStore(kvStore);
        String memId2 = "000000000000000000000002";
        storeB.write("user1", "scope1", memId2,
                Map.of("mem_type", MemoryType.USER_PROFILE.getValue(), "mem", "tenantB_content"));

        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/000000000000000000000001")).isTrue();
        assertThat(kvStore.isExists("tenantB:UMD/user1/scope1/000000000000000000000002")).isTrue();
        assertThat(kvStore.isExists("tenantA:UMD/user1/scope1/000000000000000000000002")).isFalse();
        assertThat(kvStore.isExists("tenantB:UMD/user1/scope1/000000000000000000000001")).isFalse();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        assertThat(storeA.get("user1", "scope1", memId1)).isNotNull();
        assertThat(storeA.get("user1", "scope1", memId2)).isNull();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        assertThat(storeB.get("user1", "scope1", memId2)).isNotNull();
        assertThat(storeB.get("user1", "scope1", memId1)).isNull();
    }

    // ========================= VariableManager KVStore Key =========================

    @Test
    void variableKey_withTenant_hasTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vm = new VariableManager(kvStore, new byte[0]);
        vm.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("var1").variableMem("value1").build()),
                null, null);

        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/var1")).isTrue();
        assertThat(kvStore.isExists("user_var/user1/scope1/var1")).isFalse();
    }

    @Test
    void variableKey_noTenant_backwardCompat() {
        VariableManager vm = new VariableManager(kvStore, new byte[0]);
        vm.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("var1").variableMem("value1").build()),
                null, null);

        assertThat(kvStore.isExists("user_var/user1/scope1/var1")).isTrue();
        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/var1")).isFalse();
    }

    @Test
    void variablePrefixDeleteByUserId_withTenant_deletesOnlyTenantData() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vmA = new VariableManager(kvStore, new byte[0]);
        vmA.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varA").variableMem("tenantA_value").build()),
                null, null);

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        VariableManager vmB = new VariableManager(kvStore, new byte[0]);
        vmB.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varB").variableMem("tenantB_value").build()),
                null, null);

        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/varA")).isTrue();
        assertThat(kvStore.isExists("tenantB:user_var/user1/scope1/varB")).isTrue();

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        vmA.deleteByUserId("user1", "scope1", null);

        assertThat(kvStore.isExists("tenantA:user_var/user1/scope1/varA")).isFalse();
        assertThat(kvStore.isExists("tenantB:user_var/user1/scope1/varB")).isTrue();
    }

    @Test
    void variableQueryByPrefix_withTenant_returnsOnlyTenantData() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        VariableManager vmA = new VariableManager(kvStore, new byte[0]);
        vmA.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varA").variableMem("tenantA_value").build()),
                null, null);

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        VariableManager vmB = new VariableManager(kvStore, new byte[0]);
        vmB.addMemories("user1", "scope1",
                List.of(VariableUnit.builder().variableName("varB").variableMem("tenantB_value").build()),
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

    // ========================= DistributedLock Key =========================

    @Test
    void distributedLockKey_withTenant_hasTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        DistributedLock lock = new DistributedLock(kvStore, "user/user1");
        lock.acquire();
        assertThat(kvStore.isExists("tenantA:_lock/user/user1")).isTrue();
        assertThat(kvStore.isExists("_lock/user/user1")).isFalse();
        lock.release();
    }

    @Test
    void distributedLockKey_noTenant_backwardCompat() {
        DistributedLock lock = new DistributedLock(kvStore, "user/user1");
        lock.acquire();
        assertThat(kvStore.isExists("_lock/user/user1")).isTrue();
        assertThat(kvStore.isExists("tenantA:_lock/user/user1")).isFalse();
        lock.release();
    }

    // ========================= VectorStore Collection Name =========================

    @Test
    void vectorCollectionName_withTenant_hasTenantPrefix() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String idxName = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");
        assertThat(idxName).isEqualTo("tenantA_uid_user1_gid_scope1_mtype_user_profile");
    }

    @Test
    void vectorCollectionName_noTenant_backwardCompat() {
        String idxName = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");
        assertThat(idxName).isEqualTo("uid_user1_gid_scope1_mtype_user_profile");
    }

    @Test
    void vectorCollectionName_matchesOriginalWithoutTenant() {
        String tenantAware = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "summary");
        String original = MemoryUtils.generateIdxName("user1", "scope1", "summary");
        assertThat(tenantAware).isEqualTo(original);
    }

    @Test
    void vectorCollectionName_isolationBetweenTenants() {
        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantA").build());
        String idxNameA = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");

        TenantContextHolder.setCurrentTenant(TenantContext.builder().tenantId("tenantB").build());
        String idxNameB = MemoryUtils.generateTenantAwareIdxName("user1", "scope1", "user_profile");

        assertThat(idxNameA).isEqualTo("tenantA_uid_user1_gid_scope1_mtype_user_profile");
        assertThat(idxNameB).isEqualTo("tenantB_uid_user1_gid_scope1_mtype_user_profile");
        assertThat(idxNameA).isNotEqualTo(idxNameB);
    }
}
