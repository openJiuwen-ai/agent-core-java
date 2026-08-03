/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.update;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryManageUpdatePackageMarkerTest {

    @Test
    void memoryManageUpdateRemainsMarkerOnly() throws Exception {
        Constructor<MemoryManageUpdatePackageMarker> constructor =
                MemoryManageUpdatePackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryManageUpdatePackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
