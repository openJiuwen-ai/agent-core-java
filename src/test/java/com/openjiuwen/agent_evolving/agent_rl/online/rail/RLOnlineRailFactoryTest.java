package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.rail.factory} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/factory.py}.
 */
class RLOnlineRailFactoryTest {

    @Test
    void truthyFlagMatchesPythonAcceptedValues() {
        assertThat(RLOnlineRailFactory.isRlOnlineRailEnabledFromEnvironment(
                Map.of("USE_RL_ONLINE_RAIL", "  TrUe  "))).isTrue();
        assertThat(RLOnlineRailFactory.isRlOnlineRailEnabledFromEnvironment(
                Map.of("USE_RL_ONLINE_RAIL", "yes"))).isTrue();
        assertThat(RLOnlineRailFactory.isRlOnlineRailEnabledFromEnvironment(
                Map.of("USE_RL_ONLINE_RAIL", "on"))).isTrue();
        assertThat(RLOnlineRailFactory.isRlOnlineRailEnabledFromEnvironment(
                Map.of("USE_RL_ONLINE_RAIL", "0"))).isFalse();
    }

    @Test
    void disabledFlagReturnsNullWithoutCreatingRail() {
        assertThat(RLOnlineRailFactory.buildRlOnlineRailFromEnvironment(Map.of())).isNull();
    }

    @Test
    void enabledFlagBuildsRailWithPythonEnvDefaultsAndTrimming() throws Exception {
        RLOnlineRail rail = RLOnlineRailFactory.buildRlOnlineRailFromEnvironment(Map.ofEntries(
                Map.entry("USE_RL_ONLINE_RAIL", "1"),
                Map.entry("TRAJECTORY_GATEWAY_URL", "http://gateway.local///"),
                Map.entry("TRAJECTORY_GATEWAY_API_KEY", "secret"),
                Map.entry("RL_ONLINE_TENANT_ID", "  tenant-a  ")
        ));

        assertThat(rail).isNotNull();
        assertThat(rail.getSessionId()).isEmpty();
        assertThat(rail.getTenantId()).isEqualTo("tenant-a");
        TrajectoryUploader uploader = uploaderOf(rail);
        assertThat(uploader.getGatewayEndpoint()).isEqualTo("http://gateway.local");
        assertThat(uploader.getApiKey()).isEqualTo("secret");
    }

    private static TrajectoryUploader uploaderOf(RLOnlineRail rail) throws Exception {
        Field field = RLOnlineRail.class.getDeclaredField("uploader");
        field.setAccessible(true);
        return (TrajectoryUploader) field.get(rail);
    }
}
