/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discover and manage installed harness config packages.
 *
 * <p>Mirrors Python's {@code HarnessConfigRegistry} in
 * {@code openjiuwen/harness/harness_config/registry.py}.</p>
 */
public final class HarnessConfigRegistry {

    private static List<HarnessConfigInfo> cache;
    private static final Set<String> DISABLED = new LinkedHashSet<>();

    private HarnessConfigRegistry() {
    }

    /**
     * Java service-provider counterpart to Python's {@code openjiuwen.harness_config}
     * entry point group.
     */
    public interface ConfigPathProvider {

        String id();

        Path getConfigPath();

        default String name() {
            return id();
        }

        default String version() {
            return null;
        }

        default String packageName() {
            Package providerPackage = getClass().getPackage();
            return providerPackage == null ? null : providerPackage.getName();
        }
    }

    public static synchronized List<HarnessConfigInfo> discover() {
        if (cache == null) {
            cache = scanEntryPoints();
        }
        return cache.stream()
                .filter(HarnessConfigInfo::isEnabled)
                .filter(info -> !DISABLED.contains(info.getId()))
                .toList();
    }

    public static HarnessConfigInfo get(String configId) {
        if (configId == null) {
            return null;
        }
        return discover().stream()
                .filter(info -> configId.equals(info.getId()))
                .findFirst()
                .orElse(null);
    }

    public static DeepAgent load(String configId, Object model) {
        return load(configId, model, null, (Path) null);
    }

    public static DeepAgent load(String configId,
                                 Object model,
                                 Map<String, Object> params,
                                 String workspaceRoot) {
        return load(configId, model, params, workspaceRoot == null ? null : Path.of(workspaceRoot));
    }

    public static DeepAgent load(String configId,
                                 Object model,
                                 Map<String, Object> params,
                                 Path workspaceRoot) {
        HarnessConfigInfo info = get(configId);
        if (info == null) {
            List<String> installed = discover().stream().map(HarnessConfigInfo::getId).toList();
            throw new NoSuchElementException(
                    "HarnessConfig '" + configId + "' not found or is disabled. Installed: " + installed
            );
        }
        if (info.getConfigPath() == null) {
            throw new IllegalArgumentException(
                    "HarnessConfig '" + configId + "' has no configPath. "
                            + "Ensure the provider returns a valid Path."
            );
        }

        ResolvedHarnessConfig resolved = HarnessConfigLoader.load(info.getConfigPath(), params, workspaceRoot);
        DeepAgentConfig agentConfig = HarnessConfigBuilder.build(resolved, model, workspaceRoot);
        AgentCard card = new AgentCard(
                info.getId(),
                info.getName(),
                resolved.getConfig() == null ? info.getName() : resolved.getConfig().getDescription()
        );
        DeepAgent agent = new DeepAgent(card);
        agent.configure(agentConfig);
        return agent;
    }

    public static synchronized void disable(String configId) {
        if (configId != null) {
            DISABLED.add(configId);
        }
    }

    public static synchronized void enable(String configId) {
        if (configId != null) {
            DISABLED.remove(configId);
        }
    }

    public static synchronized List<HarnessConfigInfo> inspect(String packageName) {
        if (cache == null) {
            cache = scanEntryPoints();
        }
        return cache.stream()
                .filter(info -> Objects.equals(info.getPackageName(), packageName))
                .toList();
    }

    public static synchronized void invalidateCache() {
        cache = null;
    }

    static synchronized void replaceCacheForTesting(List<HarnessConfigInfo> infos) {
        cache = infos == null ? null : new ArrayList<>(infos);
        DISABLED.clear();
    }

    static synchronized List<HarnessConfigInfo> scanEntryPoints() {
        List<HarnessConfigInfo> results = new ArrayList<>();
        ServiceLoader<ConfigPathProvider> loader = ServiceLoader.load(ConfigPathProvider.class);
        for (ConfigPathProvider provider : loader) {
            try {
                Path configPath = provider.getConfigPath();
                results.add(new HarnessConfigInfo(
                        provider.id(),
                        provider.name(),
                        provider.version(),
                        provider.packageName(),
                        configPath == null ? null : configPath.toAbsolutePath().normalize()
                ));
            } catch (RuntimeException ignored) {
                // Python registry ignores broken entry points during discovery.
            }
        }
        return results;
    }
}
