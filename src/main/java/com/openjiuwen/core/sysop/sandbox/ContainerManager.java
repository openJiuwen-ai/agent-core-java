/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.AbstractSandboxStore;
import com.openjiuwen.core.sysop.sandbox.gateway.InMemorySandboxStore;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxRecord;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyed sandbox-client registry backed by a lifecycle record store.
 *
 * <p>Adapted to the async launcher / Optional store APIs introduced with the
 * {@code sys_operation} merge into {@code sysop}.</p>
 *
 * @deprecated 730-era client cache. Prefer
 *     {@link com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ContainerManager {
    private final Map<String, SandboxClient> clients = new ConcurrentHashMap<>();
    private final Map<String, Container> containers = new ConcurrentHashMap<>();
    private final AbstractSandboxStore store;

    public ContainerManager() {
        this(new InMemorySandboxStore());
    }

    public ContainerManager(AbstractSandboxStore store) {
        this.store = store != null ? store : new InMemorySandboxStore();
        SandboxRegistryBootstrap.ensureInitialized();
    }

    public SandboxClient acquire(String key, SandboxGatewayConfig config) {
        String normalizedKey = normalizeKey(key, config);
        SandboxGatewayConfig effectiveConfig =
                config != null ? config : SandboxGatewayConfig.builder().build();
        double now = System.currentTimeMillis() / 1000.0;

        SandboxRecord record = store.get(normalizedKey).orElse(null);
        if (record != null && record.getStatus() == SandboxStatus.RUNNING) {
            record.setLastUsedTs(now);
            store.set(normalizedKey, record);
            containers.putIfAbsent(normalizedKey, toContainer(normalizedKey, record));
            return clients.computeIfAbsent(normalizedKey, ignored -> new SandboxClient(effectiveConfig));
        }

        Container container =
                record != null
                        ? resumeOrReplace(normalizedKey, effectiveConfig, record, now)
                        : createNew(normalizedKey, effectiveConfig, now);
        containers.put(normalizedKey, container);
        return clients.computeIfAbsent(normalizedKey, ignored -> new SandboxClient(effectiveConfig));
    }

    public SandboxClient get(String key) {
        return clients.get(key);
    }

    public Container getContainer(String key) {
        return containers.get(key);
    }

    public boolean release(String key) {
        return release(key, "delete");
    }

    public boolean release(String key, String onStop) {
        boolean isRemovedClient = clients.remove(key) != null;
        Container removedContainer = containers.remove(key);
        Optional<SandboxRecord> removed = store.hdel(key);
        if (removed.isPresent()) {
            SandboxRecord record = removed.get();
            SandboxLauncher launcher = launcherFor(record.getLauncherType());
            if ("pause".equalsIgnoreCase(onStop)) {
                launcher.pause(record.getSandboxId()).join();
            } else if (!"keep".equalsIgnoreCase(onStop)) {
                launcher.delete(record.getSandboxId()).join();
            }
        }
        boolean isRemovedContainerFlag = removedContainer != null || removed.isPresent();
        return isRemovedClient || isRemovedContainerFlag;
    }

    public Set<String> keys() {
        return Set.copyOf(clients.keySet());
    }

    public int size() {
        return clients.size();
    }

    public AbstractSandboxStore store() {
        return store;
    }

    public List<SandboxRecord> evictExpired(SandboxGatewayConfig config, double now) {
        SandboxLauncherConfig launcherConfig = config != null ? config.getLauncherConfig() : null;
        Integer idleTtlSeconds = launcherConfig != null ? launcherConfig.getIdleTtlSeconds() : null;
        if (idleTtlSeconds == null) {
            return List.of();
        }
        List<SandboxRecord> evicted = store.evictExpired(idleTtlSeconds, now);
        for (SandboxRecord record : evicted) {
            clients.remove(record.getSandboxId());
            containers
                    .entrySet()
                    .removeIf(entry -> record.getSandboxId().equals(entry.getValue().getSandboxId()));
            launcherFor(record.getLauncherType()).delete(record.getSandboxId()).join();
        }
        return evicted;
    }

    private String normalizeKey(String key, SandboxGatewayConfig config) {
        if (key != null && !key.isBlank()) {
            return key;
        }
        SandboxGatewayConfig effectiveConfig =
                config != null ? config : SandboxGatewayConfig.builder().build();
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

    private String rootPath(SandboxGatewayConfig config) {
        Map<String, Object> params = config != null ? config.getParams() : null;
        return String.valueOf(
                (params != null) ? params.getOrDefault("root_path", ".") : ".");
    }

    private Container createNew(String key, SandboxGatewayConfig config, double now) {
        evictExpired(config, now);
        SandboxLauncherConfig launcherConfig = launcherConfig(config);
        SandboxLauncher launcher = launcherFor(launcherConfig.getLauncherType());
        LaunchedSandbox launched =
                launcher.launch(launcherConfig, config.getTimeoutSeconds(), key).join();
        SandboxRecord record = new SandboxRecord(
                launched.getSandboxId(),
                launched.getBaseUrl(),
                SandboxStatus.RUNNING,
                launcherConfig.getLauncherType(),
                launcherConfig.getSandboxType(),
                containerConfigHash(launcherConfig),
                now,
                now,
                null);
        store.set(key, record);
        return toContainer(key, record);
    }

    private Container resumeOrReplace(
            String key, SandboxGatewayConfig config, SandboxRecord record, double now) {
        SandboxLauncher launcher = launcherFor(record.getLauncherType());
        SandboxStatus status = launcher.checkStatus(record.getSandboxId()).join();
        if (status == SandboxStatus.RUNNING) {
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return toContainer(key, record);
        }
        if (status == SandboxStatus.PAUSED) {
            launcher.resume(record.getSandboxId()).join();
            record.setStatus(SandboxStatus.RUNNING);
            record.setLastUsedTs(now);
            store.set(key, record);
            return toContainer(key, record);
        }
        store.hdel(key);
        return createNew(key, config, now);
    }

    private SandboxLauncher launcherFor(String launcherType) {
        return SandboxRegistry.createLauncher(
                launcherType != null && !launcherType.isBlank() ? launcherType : "pre_deploy");
    }

    private SandboxLauncherConfig launcherConfig(SandboxGatewayConfig config) {
        if (config == null || config.getLauncherConfig() == null) {
            throw new IllegalArgumentException("sandbox gateway requires launcher_config");
        }
        return config.getLauncherConfig();
    }

    private Container toContainer(String key, SandboxRecord record) {
        return Container.builder()
                .key(key)
                .sandboxId(record.getSandboxId())
                .baseUrl(record.getBaseUrl())
                .build();
    }

    private String containerConfigHash(SandboxLauncherConfig config) {
        return com.openjiuwen.core.common.utils.HashUtil.generateKey(
                String.valueOf(config.getBaseUrl()),
                String.valueOf(config.getGatewayUrl()),
                String.valueOf(config.getSandboxType()) + ":" + String.valueOf(config.getExtraParams()));
    }
}
