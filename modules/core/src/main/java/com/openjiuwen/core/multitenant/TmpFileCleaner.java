/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multitenant;

import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Background cleaner that periodically purges expired files from tenant tmp directories.
 *
 * @since 0.1.7
 */
public class TmpFileCleaner {
    private final Duration ttl;
    private final Duration scanInterval;
    private final String baseWorkspacePath;
    private final TenantWorkspaceResolver workspaceResolver;
    private final ScheduledExecutorService scheduler;

    public TmpFileCleaner(Duration ttl, Duration scanInterval, String baseWorkspacePath,
                          TenantWorkspaceResolver workspaceResolver) {
        this.ttl = ttl;
        this.scanInterval = scanInterval;
        this.baseWorkspacePath = baseWorkspacePath;
        this.workspaceResolver = workspaceResolver;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "tmp-file-cleaner");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                Loggers.AGENT.warn("Uncaught exception in tmp-file-cleaner thread", ex));
            return t;
        });
    }

    /**
     * Start the scheduled tmp file scan loop.
     *
     * @since 0.1.7
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::scanAndClean,
            scanInterval.toMillis(), scanInterval.toMillis(), TimeUnit.MILLISECONDS);
        Loggers.AGENT.info("TmpFileCleaner started with TTL={}, scanInterval={}", ttl, scanInterval);
    }

    /**
     * Stop the cleaner and shut down its scheduler, draining pending tasks.
     *
     * @since 0.1.7
     */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        Loggers.AGENT.info("TmpFileCleaner stopped");
    }

    private void scanAndClean() {
        Instant cutoff = Instant.now().minus(ttl);
        Path defaultTmp = Path.of(baseWorkspacePath, "tmp").toAbsolutePath().normalize();
        cleanDirectory(defaultTmp, cutoff);
        if (workspaceResolver != null) {
            cleanTenantDirectories(cutoff);
        }
    }

    private void cleanDirectory(Path dir, Instant cutoff) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> isExpired(p, cutoff))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                        Loggers.AGENT.debug("Deleted expired tmp file: {}", p);
                    } catch (IOException e) {
                        Loggers.AGENT.warn("Failed to delete expired tmp file: {}", p, e);
                    }
                });
        } catch (IOException e) {
            Loggers.AGENT.warn("Failed to walk tmp directory: {}", dir, e);
        }
    }

    private void cleanTenantDirectories(Instant cutoff) {
        Path tenantsRoot = Path.of(baseWorkspacePath, "tenants").toAbsolutePath().normalize();
        if (!Files.exists(tenantsRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.list(tenantsRoot)) {
            stream.filter(Files::isDirectory)
                .forEach(tenantDir -> {
                    Path tmpDir = tenantDir.resolve("tmp");
                    cleanDirectory(tmpDir, cutoff);
                });
        } catch (IOException e) {
            Loggers.AGENT.warn("Failed to list tenants directory: {}", tenantsRoot, e);
        }
    }

    private boolean isExpired(Path file, Instant cutoff) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            return attrs.lastModifiedTime().toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }
}
