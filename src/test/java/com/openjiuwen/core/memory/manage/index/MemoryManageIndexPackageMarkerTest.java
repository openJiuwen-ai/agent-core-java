/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.manage.index;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryManageIndexPackageMarkerTest {

    @Test
    void memoryManageIndexRemainsMarkerOnly() throws Exception {
        Constructor<MemoryManageIndexPackageMarker> constructor =
                MemoryManageIndexPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryManageIndexPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
