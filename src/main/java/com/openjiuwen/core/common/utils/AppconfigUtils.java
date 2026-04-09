/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.Map;

public final class AppconfigUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static volatile Map<String, String> configCache;

    /**
     * Loads and caches the API configuration from the classpath resource.
     *
     * @return an unmodifiable map of configuration key-value pairs
     */
    private static Map<String, String> load() {
        if (configCache == null) {
            synchronized (IpUtils.class) {
                if (configCache == null) {
                    try (InputStream is = IpUtils.class.getClassLoader()
                            .getResourceAsStream("common/appconfig.json")) {
                        if (is == null) {
                            throw new IllegalStateException(
                                    "appconfig.json not found on classpath at common/appconfig.json");
                        }
                        configCache = MAPPER.readValue(is, new TypeReference<Map<String, String>>() {
                        });
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to load appconfig.json", e);
                    }
                }
            }
        }
        return configCache;
    }

    /**
     * Gets the default IP address from the configuration.
     *
     * @return the default IP address string
     */
    public static String getDefaultIp() {
        return load().get("DEFAULT_IP");
    }
}
