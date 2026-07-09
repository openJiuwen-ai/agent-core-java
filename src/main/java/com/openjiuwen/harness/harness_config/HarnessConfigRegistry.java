/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HarnessConfigRegistry.
 * @since 0.1.7
 */
public final class HarnessConfigRegistry {
    private static final Map<String, HarnessConfigInfo> MANUAL = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private static final Map<String, LoadedConfig> LOADED = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap.newKeySet.
     * 
     * @since 0.1.7
     */
    private static final Set<String> DISABLED = ConcurrentHashMap.newKeySet();
    private static volatile List<HarnessConfigInfo> cache;

    /**
     * HarnessConfigRegistry.
     * @since 0.1.7
     */
    private HarnessConfigRegistry() {
    }

    /**
     * register.
     * @param info info
     * @since 0.1.7
     */
    public static void register(HarnessConfigInfo info) {
        MANUAL.put(info.getId(), info);
        invalidateCache();
    }

    /**
     * discover.
     * @return the result
     * @since 0.1.7
     */
    public static List<HarnessConfigInfo> discover() {
        if (cache == null) {
            synchronized (HarnessConfigRegistry.class) {
                if (cache == null) {
                    cache = scan();
                }
            }
        }
        return cache.stream().filter(HarnessConfigInfo::isEnabled).filter(info -> !DISABLED.contains(info.getId()))
                .toList();
    }

    /**
     * get.
     * @param configId configId
     * @return the result
     * @since 0.1.7
     */
    public static HarnessConfigInfo get(String configId) {
        return discover().stream().filter(info -> info.getId().equals(configId)).findFirst().orElse(null);
    }

    /**
     * load.
     * @param configId configId
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent load(String configId) {
        HarnessConfigInfo info = get(configId);
        if (info == null) {
            throw new IllegalArgumentException("HarnessConfig not found or disabled: " + configId);
        }
        Path configPath = info.getConfigPath();
        if (configPath == null) {
            throw new IllegalArgumentException("HarnessConfig has no config_path: " + configId);
        }
        Path normalized = configPath.toAbsolutePath().normalize();
        FileTime modifiedAt = lastModifiedTime(normalized);
        DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(normalized));
        LOADED.put(configId, new LoadedConfig(normalized, modifiedAt, agent));
        return agent;
    }

    /**
     * reloadIfChanged.
     * @param configId configId
     * @return the result
     * @since 0.1.7
     */
    public static ReloadResult reloadIfChanged(String configId) {
        HarnessConfigInfo info = get(configId);
        if (info == null) {
            throw new IllegalArgumentException("HarnessConfig not found or disabled: " + configId);
        }
        Path configPath = info.getConfigPath();
        if (configPath == null) {
            throw new IllegalArgumentException("HarnessConfig has no config_path: " + configId);
        }
        Path normalized = configPath.toAbsolutePath().normalize();
        FileTime modifiedAt = lastModifiedTime(normalized);
        LoadedConfig loaded = LOADED.get(configId);
        if (loaded != null && normalized.equals(loaded.configPath()) && modifiedAt.equals(loaded.modifiedAt())) {
            return new ReloadResult(false, loaded.agent());
        }
        DeepAgent agent = HarnessConfigBuilder.build(HarnessConfigLoader.load(normalized));
        LOADED.put(configId, new LoadedConfig(normalized, modifiedAt, agent));
        return new ReloadResult(true, agent);
    }

    /**
     * getLoaded.
     * @param configId configId
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent getLoaded(String configId) {
        LoadedConfig loaded = LOADED.get(configId);
        return loaded != null ? loaded.agent() : null;
    }

    /**
     * disable.
     * @param configId configId
     * @since 0.1.7
     */
    public static void disable(String configId) {
        DISABLED.add(configId);
        LOADED.remove(configId);
    }

    /**
     * enable.
     * @param configId configId
     * @since 0.1.7
     */
    public static void enable(String configId) {
        DISABLED.remove(configId);
    }

    /**
     * inspect.
     * @param packageName packageName
     * @return the result
     * @since 0.1.7
     */
    public static List<HarnessConfigInfo> inspect(String packageName) {
        return scan().stream().filter(info -> packageName.equals(info.getPackageName())).toList();
    }

    /**
     * invalidateCache.
     * @since 0.1.7
     */
    public static void invalidateCache() {
        cache = null;
    }

    /**
     * clearLoaded.
     * @param configId configId
     * @since 0.1.7
     */
    public static void clearLoaded(String configId) {
        LOADED.remove(configId);
    }

    /**
     * scan.
     * @return the result
     * @since 0.1.7
     */
    private static List<HarnessConfigInfo> scan() {
        Map<String, HarnessConfigInfo> discovered = new LinkedHashMap<>();
        for (HarnessConfigInfo info : MANUAL.values()) {
            discovered.put(info.getId(), normalize(info));
        }
        for (HarnessConfigProvider provider : ServiceLoader.load(HarnessConfigProvider.class)) {
            try {
                HarnessConfigInfo info = normalize(provider.describe());
                info.setConfigPath(provider.getConfigPath());
                discovered.put(info.getId(), info);
            } catch (Exception ignored) {
                // skip broken provider
            }
        }
        return List.copyOf(new ArrayList<>(discovered.values()));
    }

    /**
     * normalize.
     * @param info info
     * @return the result
     * @since 0.1.7
     */
    private static HarnessConfigInfo normalize(HarnessConfigInfo info) {
        if (info.getName() == null || info.getName().isBlank()) {
            info.setName(info.getId());
        }
        return info;
    }

    /**
     * lastModifiedTime.
     * @param configPath configPath
     * @return the result
     * @since 0.1.7
     */
    private static FileTime lastModifiedTime(Path configPath) {
        try {
            return Files.getLastModifiedTime(configPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read HarnessConfig mtime: " + configPath, ex);
        }
    }

    /**
     * LoadedConfig.
     * @param configPath configPath
     * @param modifiedAt modifiedAt
     * @param agent agent
     * @since 0.1.7
     */
    private record LoadedConfig(Path configPath, FileTime modifiedAt, DeepAgent agent) {
    }

    /**
     * Public record ReloadResult used by the Java parity implementation.
     * @since 0.1.7
     */
    public record ReloadResult(boolean isReloaded, DeepAgent agent) {
        /**
         * reloaded.
         * @return the result
         * @since 0.1.7
         */
        public boolean reloaded() {
            return isReloaded();
        }
    }
}
