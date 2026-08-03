/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import com.openjiuwen.core.memory.migration.operation.OperationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationPlanTest {

    @AfterEach
    void tearDown() {
        MigrationPlan.getSqlRegistry().clear();
        MigrationPlan.getVectorRegistry().clear();
        MigrationPlan.getKvRegistry().clear();
        MigrationPlan.getMessageRegistry().clear();
        MigrationPlan.getIndexRegistry().clear();
    }

    @Test
    void exposesFiveStableRegistries() {
        assertThat(MigrationPlan.getSqlRegistry()).isSameAs(MigrationPlan.getSqlRegistry());
        assertThat(MigrationPlan.getVectorRegistry()).isSameAs(MigrationPlan.getVectorRegistry());
        assertThat(MigrationPlan.getKvRegistry()).isSameAs(MigrationPlan.getKvRegistry());
        assertThat(MigrationPlan.getMessageRegistry()).isSameAs(MigrationPlan.getMessageRegistry());
        assertThat(MigrationPlan.getIndexRegistry()).isSameAs(MigrationPlan.getIndexRegistry());
    }

    @Test
    void registriesRemainIndependent() {
        OperationRegistry sql = MigrationPlan.getSqlRegistry();
        OperationRegistry vector = MigrationPlan.getVectorRegistry();
        OperationRegistry kv = MigrationPlan.getKvRegistry();
        OperationRegistry message = MigrationPlan.getMessageRegistry();
        OperationRegistry index = MigrationPlan.getIndexRegistry();

        sql.setOperations(java.util.Map.of("sql", java.util.List.of()));
        vector.setOperations(java.util.Map.of("vector", java.util.List.of()));
        kv.setOperations(java.util.Map.of("kv", java.util.List.of()));
        message.setOperations(java.util.Map.of("message", java.util.List.of()));
        index.setOperations(java.util.Map.of("index", java.util.List.of()));

        assertThat(sql.getAllEntities()).containsExactly("sql");
        assertThat(vector.getAllEntities()).containsExactly("vector");
        assertThat(kv.getAllEntities()).containsExactly("kv");
        assertThat(message.getAllEntities()).containsExactly("message");
        assertThat(index.getAllEntities()).containsExactly("index");
    }
}
