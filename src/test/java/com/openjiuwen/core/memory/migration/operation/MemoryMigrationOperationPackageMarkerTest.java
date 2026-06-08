/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.operation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMigrationOperationPackageMarkerTest {

    @Test
    void memoryMigrationOperationRemainsMarkerOnly() throws Exception {
        Constructor<MemoryMigrationOperationPackageMarker> constructor =
                MemoryMigrationOperationPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryMigrationOperationPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
