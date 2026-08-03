/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMigrationPackageMarkerTest {

    @Test
    void memoryMigrationRemainsMarkerOnly() throws Exception {
        Constructor<MemoryMigrationPackageMarker> constructor =
                MemoryMigrationPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryMigrationPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
