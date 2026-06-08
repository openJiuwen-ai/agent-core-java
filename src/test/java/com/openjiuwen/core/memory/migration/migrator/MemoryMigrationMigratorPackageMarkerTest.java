/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.migration.migrator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryMigrationMigratorPackageMarkerTest {

    @Test
    void memoryMigrationMigratorRemainsMarkerOnly() throws Exception {
        Constructor<MemoryMigrationMigratorPackageMarker> constructor =
                MemoryMigrationMigratorPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryMigrationMigratorPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
