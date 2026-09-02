/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyed sandbox-client registry backed by a lifecycle record store.
 * 
 * @since 0.1.7
 */
public class ContainerManager {
    private final Map<String, SandboxClient> clients = new ConcurrentHashMap<>();

    /**
     * ConcurrentHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, Container> containers = new ConcurrentHashMap<>();
    private final AbstractSandboxStore store;

    /**
     * ContainerManager.
     * 
     * @since 0.1.7
     */
    public ContainerManager() {
        this(new InMemorySandboxStore());
    }

    /**
     * ContainerManager.
     * 
     * @param store store
     * @since 0.1.7
     */
    public ContainerManager(AbstractSandboxStore store) {
        this.store = store != null ? store : new InMemorySandboxStore();
        SandboxRegistryBootstrap.ensureInitialized();
    }

    /**
     * acquire.
     * 
     * @param key key
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public SandboxClient acquire(String key, SandboxGatewayConfig config) {
        String normalizedKey = normalizeKey(key, config);
        SandboxGatewayConfig effectiveConfig = config != null ? config : SandboxGatewayConfig.builder().build();
        double now = System.currentTimeMillis() / 1000.0;

        SandboxRecord record = store.get(normalizedKey);
        if (record != null && record.getStatus() == SandboxStatus.RUNNING) {
            record.setLastUsedTs(now);
            store.set(normalizedKey, record);
            containers.putIfAbsent(normalizedKey, toContainer(normalizedKey, record));
            return clients.computeIfAbsent(normalizedKey, ignored -> new SandboxClient(effectiveConfig));
        }

        Container container = record != null
                ? resumeOrReplace(normalizedKey, effectiveConfig, record, now)
                : createNew(normalizedKey, effectiveConfig, now);
        containers.put(normalizedKey, container);
        return clients.computeIfAbsent(normalizedKey, ignored -> new SandboxClient(effectiveConfig));
    }

    /**
     * get.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public SandboxClient get(String key) {
        return clients.get(key);
    }

    /**
     * getContainer.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public Container getContainer(String key) {
        return containers.get(key);
    }

    /**
     * release.
     * 
     * @param key key
     * @return the result
     * @since 0.1.7
     */
    public boolean release(String key) {
        return release(key, "delete");
    }

    /**
     * release.
     * 
     * @param key key
     * @param onStop onStop
     * @return the result
     * @since 0.1.7
     */
    public boolean release(String key, String onStop) {
        boolean isRemovedClient = clients.remove(key) != null;
        Container removedContainer = containers.remove(key);
        SandboxRecord record = store.hdel(key);
        if (record != null) {
            SandboxLauncher launcher = launcherFor(record.getLauncherType());
            if ("pause".equalsIgnoreCase(onStop)) {
                launcher.pause(record.getSandboxId());
            } else if (!"keep".equalsIgnoreCase(onStop)) {
                launcher.delete(record.getSandboxId());
            } else {
                // no-op
            }
        }
        boolean isRemovedContainerFlag = removedContainer != null || record != null;
        return isRemovedClient || isRemovedContainerFlag;
    }

    /**
     * keys.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> keys() {
        return Set.copyOf(clients.keySet());
    }

    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int size() {
        return clients.size();
    }

    /**
     * store.
     * 
     * @return the result
     * @since 0.1.7
     */
    public AbstractSandboxStore store() {
        return store;
    }

    /**
     * evictExpired.
     * 
     * @param config config
     * @param now now
     * @return the result
     * @since 0.1.7
     */
    public List<SandboxRecord> evictExpired(SandboxGatewayConfig config, double now) {
        SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
        Integer idleTtlSeconds = launcherConfig != null ? launcherConfig.getIdleTtlSeconds() : null;
        if (idleTtlSeconds == null) {
            return List.of();
        }
        List<SandboxRecord> evicted = store.evictExpired(idleTtlSeconds, now);
        for (SandboxRecord record : evicted) {
            clients.remove(record.getSandboxId());
            containers.entrySet().removeIf(entry -> record.getSandboxId().equals(entry.getValue().getSandboxId()));
            launcherFor(record.getLauncherType()).delete(record.getSandboxId());
        }
        return evicted;
    }

    /**
     * normalizeKey.
     * 
     * @param key key
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private String normalizeKey(String key, SandboxGatewayConfig config) {
        if (key != null && !key.isBlank()) {
            return key;
        }
        SandboxGatewayConfig effectiveConfig = config != null ? config : SandboxGatewayConfig.builder().build();
        if (effectiveConfig.getIsolation() != null) {
            if (effectiveConfig.getIsolation().getCustomId() != null
                    && !effectiveConfig.getIsolation().getCustomId().isBlank()) {
                return effectiveConfig.getIsolation().getCustomId();
            }
            if (effectiveConfig.getIsolation().getPrefix() != null
                    && !effectiveConfig.getIsolation().getPrefix().isBlank()) {
                return effectiveConfig.getIsolation().getPrefix() + ":" + rootPath(effectiveConfig);
            }
        }
        return "sandbox:" + rootPath(effectiveConfig);
    }

    /**
     * rootPath.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private String rootPath(SandboxGatewayConfig config) {
        return String.valueOf((config != null && config.getParams() != null)
                ? config.getParams().getOrDefault("root_path", ".")
                : ".");
    }

    /**
     * createNew.
     * 
     * @param key key
     * @param config config
     * @param now now
     * @return the result
     * @since 0.1.7
     */
    private Container createNew(String key, SandboxGatewayConfig config, double now) {
        evictExpired(config, now);
        SandboxLauncherConfig launcherConfig = launcherConfig(config);
        SandboxLauncher launcher = launcherFor(launcherConfig.getLauncherType());
        LaunchedSandbox launched = launcher.launch(launcherConfig, config.getTimeoutSeconds(), key);
        SandboxRecord record = SandboxRecord.builder().sandboxId(launched.getSandboxId()).baseUrl(launched.getBaseUrl())
                .status(SandboxStatus.RUNNING).launcherType(launcherConfig.getLauncherType())
                .sandboxType(launcherConfig.getSandboxType()).containerConfigHash(containerConfigHash(launcherConfig))
                .createdTs(now).lastUsedTs(now).build();
        store.set(key, record);
        return toContainer(key, record);
    }

    /**
     * resumeOrReplace.
     * 
     * @param key key
     * @param config config
     * @param record record
     * @param now now
     * @return the result
     * @since 0.1.7
     */
    private Container resumeOrReplace(String key, SandboxGatewayConfig config, SandboxRecord record, double now) {
        SandboxLauncher launcher = launcherFor(record.getLauncherType());
        SandboxStatus status = launcher.checkStatus(record.getSandboxId());
        if (status == SandboxStatus.RUNNING) {
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return toContainer(key, record);
        }
        if (status == SandboxStatus.PAUSED) {
            launcher.resume(record.getSandboxId());
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return toContainer(key, record);
        }
        store.hdel(key);
        return createNew(key, config, now);
    }

    /**
     * launcherFor.
     * 
     * @param launcherType launcherType
     * @return the result
     * @since 0.1.7
     */
    private SandboxLauncher launcherFor(String launcherType) {
        return SandboxRegistry
                .createLauncher(launcherType != null && !launcherType.isBlank() ? launcherType : "pre_deploy");
    }

    /**
     * launcherConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private SandboxLauncherConfig launcherConfig(SandboxGatewayConfig config) {
        if (config == null || config.getLauncherConfig() == null) {
            throw new IllegalArgumentException("sandbox gateway requires launcher_config");
        }
        return config.getLauncherConfig();
    }

    /**
     * toContainer.
     * 
     * @param key key
     * @param record record
     * @return the result
     * @since 0.1.7
     */
    private Container toContainer(String key, SandboxRecord record) {
        return Container.builder().key(key).sandboxId(record.getSandboxId()).baseUrl(record.getBaseUrl()).build();
    }

    /**
     * containerConfigHash.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private String containerConfigHash(SandboxLauncherConfig config) {
        return com.openjiuwen.core.common.utils.HashUtil.generateKey(String.valueOf(config.getBaseUrl()),
                String.valueOf(config.getGatewayUrl()), String.valueOf(config.getSandboxType()),
                String.valueOf(config.getExtraParams()));
    }
}
