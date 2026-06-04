// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Factory: build RLOnlineRail from process environment (for DeepAgent integration).
 * <p>
 * Mirrors Python's {@code factory.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.factory}.
 */
public final class RlOnlineRailFactory {
    
    private static final Logger logger = Logger.getLogger(RlOnlineRailFactory.class.getName());
    
    private static final Set<String> TRUTHY_VALUES = Set.of("1", "true", "yes", "on");
    private static final String DEFAULT_GATEWAY_URL = "http://127.0.0.1:18080";
    
    private RlOnlineRailFactory() {
        // Utility class
    }
    
    /**
     * True when USE_RL_ONLINE_RAIL is set to a truthy string.
     * 
     * @return true if RL online rail is enabled
     */
    public static boolean isRlOnlineRailEnabledFromEnv() {
        return isRlOnlineRailEnabledFromEnv(System.getenv());
    }

    public static boolean isRlOnlineRailEnabledFromEnv(Map<String, String> env) {
        String envValue = env != null ? env.get("USE_RL_ONLINE_RAIL") : null;
        if (envValue == null) {
            return false;
        }
        return TRUTHY_VALUES.contains(envValue.trim().toLowerCase());
    }
    
    /**
     * Instantiate RLOnlineRail + TrajectoryUploader from env, or return null.
     * <p>
     * Environment variables:
     * <ul>
     *   <li>USE_RL_ONLINE_RAIL — must be truthy to build (otherwise returns null)</li>
     *   <li>TRAJECTORY_GATEWAY_URL — default http://127.0.0.1:18080</li>
     *   <li>TRAJECTORY_GATEWAY_API_KEY — optional Bearer token for the gateway</li>
     *   <li>RL_ONLINE_TENANT_ID — optional tenant / user namespace for LoRA routing</li>
     * </ul>
     * 
     * @return RLOnlineRail instance, or null if not enabled or classes not available
     */
    public static RLOnlineRail buildRlOnlineRailFromEnv() {
        return buildRlOnlineRailFromEnv(System.getenv());
    }

    public static RLOnlineRail buildRlOnlineRailFromEnv(Map<String, String> env) {
        if (!isRlOnlineRailEnabledFromEnv(env)) {
            return null;
        }

        String gw = getGatewayUrlFromEnv(env);
        String apiKey = getApiKeyFromEnv(env);
        String tenantId = getTenantIdFromEnv(env);
        TrajectoryUploader uploader = new GatewayTrajectoryUploader(gw, apiKey);
        RLOnlineRail rail = new RLOnlineRail("", gw, tenantId, uploader);
        logger.info("build_rl_online_rail_from_env: RLOnlineRail ready (rail-v1), gateway=" + gw);
        return rail;
    }
    
    /**
     * Get gateway URL from environment.
     * 
     * @return Gateway URL, default http://127.0.0.1:18080
     */
    public static String getGatewayUrlFromEnv() {
        return getGatewayUrlFromEnv(System.getenv());
    }

    public static String getGatewayUrlFromEnv(Map<String, String> env) {
        String gw = env != null ? env.get("TRAJECTORY_GATEWAY_URL") : null;
        if (gw == null) {
            gw = DEFAULT_GATEWAY_URL;
        }
        return stripTrailingSlashes(gw);
    }
    
    /**
     * Get API key from environment.
     * 
     * @return API key, or empty string if not set
     */
    public static String getApiKeyFromEnv() {
        return getApiKeyFromEnv(System.getenv());
    }

    public static String getApiKeyFromEnv(Map<String, String> env) {
        String apiKey = env != null ? env.get("TRAJECTORY_GATEWAY_API_KEY") : null;
        return apiKey != null && !apiKey.isEmpty() ? apiKey : "";
    }
    
    /**
     * Get tenant ID from environment.
     * 
     * @return Tenant ID, or null if not set
     */
    public static String getTenantIdFromEnv() {
        return getTenantIdFromEnv(System.getenv());
    }

    public static String getTenantIdFromEnv(Map<String, String> env) {
        String tenantRaw = env != null ? env.get("RL_ONLINE_TENANT_ID") : null;
        return tenantRaw != null && !tenantRaw.trim().isEmpty() ? tenantRaw.trim() : null;
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
