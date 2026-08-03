/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DataIdManagerTest {

    @Test
    void generatedIdsUseExpectedHexShape() {
        DataIdManager manager = new DataIdManager();

        String generated = manager.generateNextId("user-1").join();

        assertThat(generated).matches("[0-9a-f]{24}");
    }

    @Test
    void generatedIdsVaryAcrossCalls() {
        DataIdManager manager = new DataIdManager();

        String first = manager.generateNextId("user-1").join();
        String second = manager.generateNextId("user-1").join();

        assertThat(second).isNotEqualTo(first);
    }
}
