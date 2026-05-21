// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

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
    
    private RlOnlineRailFactory() {
        // Utility class
    }
    
    /**
     * True when USE_RL_ONLINE_RAIL is set to a truthy string.
     * 
     * @return true if RL online rail is enabled
     */
    public static boolean isRlOnlineRailEnabledFromEnv() {
        String envValue = System.getenv("USE_RL_ONLINE_RAIL");
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
    public static Object buildRlOnlineRailFromEnv() {
        if (!isRlOnlineRailEnabledFromEnv()) {
            return null;
        }
        
        try {
            // Get environment variables
            String gw = System.getenv("TRAJECTORY_GATEWAY_URL");
            if (gw == null || gw.isEmpty()) {
                gw = "http://127.0.0.1:18080";
            }
            gw = gw.endsWith("/") ? gw.substring(0, gw.length() - 1) : gw;
            
            String apiKey = System.getenv("TRAJECTORY_GATEWAY_API_KEY");
            if (apiKey == null) {
                apiKey = "";
            }
            
            String tenantRaw = System.getenv("RL_ONLINE_TENANT_ID");
            String tenantId = tenantRaw != null && !tenantRaw.trim().isEmpty() ? tenantRaw.trim() : null;
            
            // PLACEHOLDER: Requires TrajectoryUploader and RLOnlineRail Java classes
            // TrajectoryUploader uploader = new TrajectoryUploader(gw, apiKey);
            // RLOnlineRail rail = new RLOnlineRail("", gw, tenantId, uploader);
            
            logger.info("buildRlOnlineRailFromEnv: RLOnlineRail ready (rail-v1), gateway=" + gw);
            
            throw new UnsupportedOperationException(
                "buildRlOnlineRailFromEnv requires TrajectoryUploader and RLOnlineRail Java classes. " +
                "Placeholder until online_rail.py and uploader.py are fully translated."
            );
            
        } catch (Exception exc) {
            logger.warning("buildRlOnlineRailFromEnv: import failed (" + exc.getMessage() + 
                "). Install openjiuwen with online-rl extra.");
            return null;
        }
    }
    
    /**
     * Get gateway URL from environment.
     * 
     * @return Gateway URL, default http://127.0.0.1:18080
     */
    public static String getGatewayUrlFromEnv() {
        String gw = System.getenv("TRAJECTORY_GATEWAY_URL");
        if (gw == null || gw.isEmpty()) {
            gw = "http://127.0.0.1:18080";
        }
        return gw.endsWith("/") ? gw.substring(0, gw.length() - 1) : gw;
    }
    
    /**
     * Get API key from environment.
     * 
     * @return API key, or empty string if not set
     */
    public static String getApiKeyFromEnv() {
        String apiKey = System.getenv("TRAJECTORY_GATEWAY_API_KEY");
        return apiKey != null ? apiKey : "";
    }
    
    /**
     * Get tenant ID from environment.
     * 
     * @return Tenant ID, or null if not set
     */
    public static String getTenantIdFromEnv() {
        String tenantRaw = System.getenv("RL_ONLINE_TENANT_ID");
        return tenantRaw != null && !tenantRaw.trim().isEmpty() ? tenantRaw.trim() : null;
    }
}