/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.process.refine;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryProcessRefinePackageMarkerTest {

    @Test
    void memoryProcessRefineRemainsMarkerOnly() throws Exception {
        Constructor<MemoryProcessRefinePackageMarker> constructor =
                MemoryProcessRefinePackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MemoryProcessRefinePackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
