/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLitePackageMarkerTest {

    @Test
    void memoryLiteRemainsMarkerOnly() throws Exception {
        Constructor<MemoryLitePackageMarker> constructor =
                MemoryLitePackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryLitePackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
