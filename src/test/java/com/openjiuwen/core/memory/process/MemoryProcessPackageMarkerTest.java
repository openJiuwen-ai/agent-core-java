/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProcessPackageMarkerTest {

    @Test
    void memoryProcessRemainsMarkerOnly() throws Exception {
        Constructor<MemoryProcessPackageMarker> constructor =
                MemoryProcessPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryProcessPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
