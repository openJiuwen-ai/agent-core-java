/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core;

import com.openjiuwen.core.runner.drunner.dmessage_queue.DMessageQueuePackageMarker;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.DSubscriptionPackageMarker;
import com.openjiuwen.core.runner.resourcemanager.ResourceManagerPackageMarker;
import com.openjiuwen.core.security.CoreSecurityPackageMarker;
import com.openjiuwen.core.session.config.SessionConfigPackageMarker;
import com.openjiuwen.core.session.interaction.SessionInteractionPackageMarker;
import com.openjiuwen.core.session.internal.SessionInternalPackageMarker;
import com.openjiuwen.core.session.state.SessionStatePackageMarker;
import com.openjiuwen.core.single_agent.agents.SingleAgentAgentsPackageMarker;
import com.openjiuwen.core.single_agent.schema.SingleAgentSchemaPackageMarker;
import com.openjiuwen.core.sys_operation.local.SysOperationLocalPackageMarker;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoreBatch0020PackageMarkersTest {

    @Test
    void packageMarkersRemainMarkerOnly() throws Exception {
        assertMarkerClass(DMessageQueuePackageMarker.class);
        assertMarkerClass(DSubscriptionPackageMarker.class);
        assertMarkerClass(ResourceManagerPackageMarker.class);
        assertMarkerClass(CoreSecurityPackageMarker.class);
        assertMarkerClass(SessionConfigPackageMarker.class);
        assertMarkerClass(SessionInteractionPackageMarker.class);
        assertMarkerClass(SessionInternalPackageMarker.class);
        assertMarkerClass(SessionStatePackageMarker.class);
        assertMarkerClass(SingleAgentAgentsPackageMarker.class);
        assertMarkerClass(SingleAgentSchemaPackageMarker.class);
        assertMarkerClass(SysOperationLocalPackageMarker.class);
    }

    private static void assertMarkerClass(Class<?> type) throws Exception {
        Constructor<?> constructor = type.getDeclaredConstructor();
        assertThat(Modifier.isFinal(type.getModifiers())).isTrue();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
