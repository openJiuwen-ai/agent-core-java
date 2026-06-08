/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multi_agent.schema;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MultiAgentSchemaPackageMarkerTest {

    @Test
    void multiAgentSchemaRemainsMarkerOnly() throws Exception {
        Constructor<MultiAgentSchemaPackageMarker> constructor =
                MultiAgentSchemaPackageMarker.class.getDeclaredConstructor();

        assertThat(Modifier.isFinal(MultiAgentSchemaPackageMarker.class.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
