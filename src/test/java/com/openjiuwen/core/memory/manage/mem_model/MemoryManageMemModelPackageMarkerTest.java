/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.mem_model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryManageMemModelPackageMarkerTest {

    @Test
    void memoryManageMemModelRemainsMarkerOnly() throws Exception {
        Constructor<MemoryManageMemModelPackageMarker> constructor =
                MemoryManageMemModelPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryManageMemModelPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
