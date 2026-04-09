  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.logging.defaults;

/**
 * Global defaults facade — provides Python-style convenience singletons and
 * configuration entry points for the logging subsystem.
 * <p>
 * Mirrors the Python module-level objects {@code config}, {@code log_config},
 * {@code configure(config_path)}, and {@code configure_log(config_path)}.
 *
 * <pre>
 *   // equivalent of Python: config.get("logging.level")
 *   LoggingDefaults.config().get("logging.level");
 *
 *   // equivalent of Python: configure("/path/to/config.yaml")
 *   LoggingDefaults.configure("/path/to/config.yaml");
 *
 *   // equivalent of Python: log_config.get_common_config()
 *   LoggingDefaults.logConfig().getCommonConfig();
 *
 *   // equivalent of Python: configure_log("/path/to/log_config.yaml")
 *   LoggingDefaults.configureLog("/path/to/log_config.yaml");
 * </pre>
 */
public final class LoggingDefaults {

    private static volatile ConfigManager configManager = new ConfigManager();
    private static volatile LogConfig logConfigInstance = new LogConfig();

    private LoggingDefaults() {
    }

    // ==================== ConfigManager façade ====================

    /**
     * Get the global {@link ConfigManager} singleton.
     * <p>
     * Equivalent of Python's module-level {@code config} object.
     */
    public static ConfigManager config() {
        return configManager;
    }

    /**
     * Reload the global config from a new YAML path.
     * <p>
     * Equivalent of Python's {@code configure(config_path)}.
     *
     * @param configPath YAML configuration file path
     */
    public static void configure(String configPath) {
        configManager.reload(configPath);
    }

    // ==================== LogConfig façade ====================

    /**
     * Get the global {@link LogConfig} singleton.
     * <p>
     * Equivalent of Python's module-level {@code log_config} object.
     */
    public static LogConfig logConfig() {
        return logConfigInstance;
    }

    /**
     * Reload the global log config from a new YAML path.
     * <p>
     * Equivalent of Python's {@code configure_log(config_path)}.
     *
     * @param configPath YAML log configuration file path
     */
    public static void configureLog(String configPath) {
        logConfigInstance.reload(configPath);
    }

    /**
     * Reset all global singletons — primarily for testing.
     */
    public static synchronized void reset() {
        configManager = new ConfigManager();
        logConfigInstance = new LogConfig();
    }
}
