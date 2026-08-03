/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.rail;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Environment factory for the online RL rail.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.rail.factory} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/factory.py}.</p>
 */
public final class RLOnlineRailFactory {

    private static final Logger LOGGER = Logger.getLogger(RLOnlineRailFactory.class.getName());
    private static final String ENABLE_FLAG = "USE_RL_ONLINE_RAIL";
    private static final String GATEWAY_URL = "TRAJECTORY_GATEWAY_URL";
    private static final String GATEWAY_API_KEY = "TRAJECTORY_GATEWAY_API_KEY";
    private static final String TENANT_ID = "RL_ONLINE_TENANT_ID";
    private static final String DEFAULT_GATEWAY_URL = "http://127.0.0.1:18080";

    private RLOnlineRailFactory() {
    }

    public static boolean isRlOnlineRailEnabledFromEnv() {
        return isRlOnlineRailEnabledFromEnvironment(System.getenv());
    }

    public static boolean is_rl_online_rail_enabled_from_env() {
        return isRlOnlineRailEnabledFromEnv();
    }

    static boolean isRlOnlineRailEnabledFromEnvironment(Map<String, String> environment) {
        String value = getEnv(environment, ENABLE_FLAG, "");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    public static RLOnlineRail buildRlOnlineRailFromEnv() {
        return buildRlOnlineRailFromEnvironment(System.getenv());
    }

    public static RLOnlineRail build_rl_online_rail_from_env() {
        return buildRlOnlineRailFromEnv();
    }

    static RLOnlineRail buildRlOnlineRailFromEnvironment(Map<String, String> environment) {
        if (!isRlOnlineRailEnabledFromEnvironment(environment)) {
            return null;
        }
        String gatewayEndpoint = stripTrailingSlashes(getEnv(environment, GATEWAY_URL, DEFAULT_GATEWAY_URL));
        String apiKey = getEnv(environment, GATEWAY_API_KEY, "");
        String tenantRaw = getEnv(environment, TENANT_ID, "").trim();
        String tenantId = tenantRaw.isEmpty() ? null : tenantRaw;
        TrajectoryUploader uploader = new TrajectoryUploader(
                gatewayEndpoint,
                256,
                5,
                0.2d,
                Path.of("records", "rail_v1_wal"),
                apiKey
        );
        RLOnlineRail rail = new RLOnlineRail("", gatewayEndpoint, tenantId, uploader);
        LOGGER.info(() -> "build_rl_online_rail_from_env: RLOnlineRail ready (rail-v1), gateway="
                + gatewayEndpoint);
        return rail;
    }

    private static String getEnv(Map<String, String> environment, String key, String defaultValue) {
        if (environment == null) {
            return defaultValue;
        }
        String value = environment.get(key);
        return value == null ? defaultValue : value;
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
