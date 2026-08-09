/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core;

import com.openjiuwen.core.security.CoreSecurityPackageMarker;
import com.openjiuwen.core.singleagent.agents.SingleAgentAgentsPackageMarker;
import com.openjiuwen.core.singleagent.schema.SingleAgentSchemaPackageMarker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreBatch0020PackageMarkersTest {

    @Test
    void packageMarkersRemainMarkerOnly() throws Exception {
        assertMarkerClass(CoreSecurityPackageMarker.class);
        assertMarkerClass(SingleAgentAgentsPackageMarker.class);
        assertMarkerClass(SingleAgentSchemaPackageMarker.class);
    }

    private static void assertMarkerClass(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
