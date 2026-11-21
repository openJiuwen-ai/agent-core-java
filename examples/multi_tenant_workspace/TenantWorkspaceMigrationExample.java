/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.multi_tenant_workspace;

import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantWorkspaceLocator;
import com.openjiuwen.core.multitenant.TenantWorkspacePolicy;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reference implementation for migrating tenant workspaces from the legacy
 * {@code base/tenants/{tenantId}} layout to a caller-defined sharded layout.
 * <p>
 * The core module deliberately does not implement old/new path compatibility. Migration is a business-side
 * responsibility: keep a workspace registry, move tenant directories in batches, and keep a bounded legacy
 * fallback only during the transition. Once every tenant has been migrated, disable the fallback and update all
 * registry records instead of falling back again.
 *
 * @since 0.1.17
 */
public final class TenantWorkspaceMigrationExample {
    private static final Logger LOGGER = LoggerFactory.getLogger(TenantWorkspaceMigrationExample.class);
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int HASH_LEVEL_LENGTH = 2;
    private static final String LEGACY_TENANTS_DIRECTORY = "tenants";
    private static final String REGISTRY_COMMENT_PREFIX = "#";

    private TenantWorkspaceMigrationExample() {
    }

    /**
     * Runs the migration demo with two legacy tenants.
     *
     * @param args unused
     * @throws IOException when the demo workspace or registry cannot be created
     */
    public static void main(String[] args) throws IOException {
        Path demoDirectory = Files.createTempDirectory("tenant-workspace-migration-");
        String baseDir = demoDirectory.toString();
        prepareLegacyTenant(baseDir, "dept-01");
        prepareLegacyTenant(baseDir, "dept-02");

        Path registryFile = demoDirectory.resolve("registry").resolve("tenant-workspaces.properties");
        TenantWorkspaceRegistry registry = new FileTenantWorkspaceRegistry(registryFile);
        List<String> tenantIds = List.of("dept-01", "dept-02");
        System.out.println("=== 租户 workspace 迁移示例 ===");
        System.out.println("demo 目录: " + baseDir);
        System.out.println("第一次迁移: " + migrateBatch(registry, baseDir, tenantIds) + " 个");
        System.out.println("第二次迁移: " + migrateBatch(registry, baseDir, tenantIds) + " 个（幂等）");
        printRegistry(registry);
        printResolvedRoots(registry, baseDir, tenantIds);

        DeepAgentConfig config = exampleConfig(registry, baseDir);
        System.out.println("DeepAgentConfig: tenantDataRoot=" + config.getTenantDataRoot()
                + ", locator=" + config.getTenantWorkspaceLocator().getClass().getSimpleName());
    }

    /**
     * Builds a business-side directory layout: {@code baseDir/ab/cd/tenantId}, where {@code ab} and {@code cd} are
     * the first four hexadecimal characters of {@code SHA-256(tenantId)}.
     * <p>
     * Note: the hash only spreads directories for operational convenience. It is not a security boundary; the
     * core still validates the resolved root against the configured allowed roots.
     *
     * @param baseDir the base directory configured for tenant isolation
     * @param tenantId the tenant identifier
     * @return the sharded tenant workspace root
     */
    static Path shardedWorkspace(String baseDir, String tenantId) {
        String hash = sha256Hex(tenantId);
        String firstLevel = hash.substring(0, HASH_LEVEL_LENGTH);
        String secondLevel = hash.substring(HASH_LEVEL_LENGTH, HASH_LEVEL_LENGTH * 2);
        return Path.of(baseDir).resolve(firstLevel).resolve(secondLevel).resolve(tenantId);
    }

    /**
     * Returns the legacy tenant workspace layout used before the migration.
     *
     * @param baseDir the base directory configured for tenant isolation
     * @param tenantId the tenant identifier
     * @return the legacy tenant workspace root
     */
    static String legacyWorkspace(String baseDir, String tenantId) {
        return Path.of(baseDir).resolve(LEGACY_TENANTS_DIRECTORY).resolve(tenantId).toString();
    }

    /**
     * Migrates a batch of tenants to the sharded layout, skipping tenants that are already registered.
     * <p>
     * The directory is moved before the registry record is written. If the process crashes between the two steps,
     * the next run takes the "target already exists" branch and only repairs the registry record.
     *
     * @param registry the workspace registry
     * @param baseDir the base directory configured for tenant isolation
     * @param tenantIds the tenants to migrate
     * @return the number of tenants migrated in this batch
     */
    static int migrateBatch(TenantWorkspaceRegistry registry, String baseDir, List<String> tenantIds) {
        int migratedCount = 0;
        for (String tenantId : tenantIds) {
            try {
                if (migrateTenant(registry, baseDir, tenantId)) {
                    migratedCount++;
                }
            } catch (IOException | UncheckedIOException ex) {
                LOGGER.error("Failed to migrate tenant workspace, tenantId={}", tenantId, ex);
            }
        }
        return migratedCount;
    }

    /**
     * Builds an example DeepAgent configuration that resolves tenant workspaces through the registry.
     *
     * @param registry the workspace registry
     * @param baseDir the base directory configured for tenant isolation
     * @return the example configuration
     */
    static DeepAgentConfig exampleConfig(TenantWorkspaceRegistry registry, String baseDir) {
        return DeepAgentConfig.builder()
                .enableTenantIsolation(true)
                .tenantDataRoot(baseDir)
                .allowedTenantRoots(List.of(baseDir))
                .tenantWorkspaceLocator(new RegistryBackedLocator(registry, baseDir, false))
                .build();
    }

    private static boolean migrateTenant(TenantWorkspaceRegistry registry, String baseDir, String tenantId)
            throws IOException {
        Optional<String> recordedPath = registry.findWorkspace(tenantId);
        if (recordedPath.isPresent()) {
            return false;
        }
        Path target = shardedWorkspace(baseDir, tenantId);
        Path source = Path.of(legacyWorkspace(baseDir, tenantId));
        if (!Files.isDirectory(source)) {
            registry.saveWorkspace(tenantId, target.toString());
            return true;
        }
        if (!Files.exists(target)) {
            moveDirectory(source, target);
        }
        registry.saveWorkspace(tenantId, target.toString());
        return true;
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target);
        } catch (IOException ex) {
            // A move across file systems is not supported; copy the tree and remove the source instead.
            copyRecursively(source, target);
            deleteRecursively(source);
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        List<Path> sourcePaths;
        try (Stream<Path> paths = Files.walk(source)) {
            sourcePaths = paths.toList();
        }
        for (Path path : sourcePaths) {
            Path destination = target.resolve(source.relativize(path));
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination);
            } else {
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        List<Path> pathsToDelete;
        try (Stream<Path> paths = Files.walk(root)) {
            pathsToDelete = paths.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : pathsToDelete) {
            Files.deleteIfExists(path);
        }
    }

    private static String sha256Hex(String tenantId) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(tenantId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is not available", ex);
        }
    }

    private static void prepareLegacyTenant(String baseDir, String tenantId) throws IOException {
        Path skillsDirectory = Path.of(legacyWorkspace(baseDir, tenantId)).resolve("skills");
        Files.createDirectories(skillsDirectory);
        Files.writeString(skillsDirectory.resolve("skill.md"), "legacy skill of " + tenantId,
                StandardCharsets.UTF_8);
    }

    private static void printRegistry(TenantWorkspaceRegistry registry) {
        System.out.println("登记表内容:");
        for (String tenantId : registry.listTenantIds()) {
            String workspacePath = registry.findWorkspace(tenantId).orElse("");
            System.out.println("  " + tenantId + "=" + workspacePath);
        }
    }

    private static void printResolvedRoots(TenantWorkspaceRegistry registry, String baseDir,
            List<String> tenantIds) {
        TenantWorkspacePolicy policy = TenantWorkspacePolicy.builder()
                .baseWorkspacePath(baseDir)
                .allowedTenantRoots(List.of(baseDir))
                .locator(new RegistryBackedLocator(registry, baseDir, false))
                .build();
        for (String tenantId : tenantIds) {
            Optional<String> recordedPath = registry.findWorkspace(tenantId);
            if (recordedPath.isEmpty()) {
                continue;
            }
            TenantContext explicitContext = TenantContext.builder()
                    .tenantId(tenantId)
                    .workspaceRoot(recordedPath.get())
                    .build();
            TenantContext locatorContext = TenantContext.builder().tenantId(tenantId).build();
            System.out.println("  " + tenantId
                    + " explicit=" + policy.resolveTenantRoot(explicitContext)
                    + " locator=" + policy.resolveTenantRoot(locatorContext));
        }
    }

    /**
     * Business-side registry that records the workspace path of each migrated tenant.
     *
     * @since 0.1.17
     */
    interface TenantWorkspaceRegistry {
        /**
         * Looks up the registered workspace path of a tenant.
         *
         * @param tenantId the tenant identifier
         * @return the registered path, or {@link Optional#empty()} when the tenant is not registered
         * @throws UncheckedIOException when the registry file cannot be read
         * @since 0.1.17
         */
        Optional<String> findWorkspace(String tenantId);

        /**
         * Registers the workspace path of a tenant, replacing any previous value.
         *
         * @param tenantId the tenant identifier
         * @param workspacePath the workspace path to record
         * @throws UncheckedIOException when the registry file cannot be written
         * @since 0.1.17
         */
        void saveWorkspace(String tenantId, String workspacePath);

        /**
         * Lists all registered tenant identifiers.
         *
         * @return the registered tenant identifiers, never null
         * @throws UncheckedIOException when the registry file cannot be read
         * @since 0.1.17
         */
        List<String> listTenantIds();
    }

    /**
     * File-backed {@link TenantWorkspaceRegistry} using one {@code tenantId=workspacePath} line per tenant.
     *
     * @since 0.1.17
     */
    static final class FileTenantWorkspaceRegistry implements TenantWorkspaceRegistry {
        private final Path registryFile;

        FileTenantWorkspaceRegistry(Path registryFile) {
            this.registryFile = registryFile;
        }

        @Override
        public synchronized Optional<String> findWorkspace(String tenantId) {
            return Optional.ofNullable(readEntries().get(tenantId));
        }

        @Override
        public synchronized void saveWorkspace(String tenantId, String workspacePath) {
            Map<String, String> entries = new LinkedHashMap<>(readEntries());
            entries.put(tenantId, workspacePath);
            try {
                writeEntries(entries);
            } catch (IOException ex) {
                throw new UncheckedIOException(
                        "Failed to save tenant workspace registry: " + registryFile, ex);
            }
        }

        @Override
        public synchronized List<String> listTenantIds() {
            return List.copyOf(readEntries().keySet());
        }

        private Map<String, String> readEntries() {
            if (!Files.exists(registryFile)) {
                return new LinkedHashMap<>();
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(registryFile, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException(
                        "Failed to read tenant workspace registry: " + registryFile, ex);
            }
            return parseEntries(lines);
        }

        private static Map<String, String> parseEntries(List<String> lines) {
            Map<String, String> entries = new LinkedHashMap<>();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith(REGISTRY_COMMENT_PREFIX)) {
                    continue;
                }
                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }
                String tenantId = trimmed.substring(0, separatorIndex).trim();
                String workspacePath = trimmed.substring(separatorIndex + 1).trim();
                entries.put(tenantId, workspacePath);
            }
            return entries;
        }

        private void writeEntries(Map<String, String> entries) throws IOException {
            Path parent = registryFile.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tempFile = Files.createTempFile(parent, "tenant-workspace-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    writer.write(entry.getKey() + "=" + entry.getValue());
                    writer.newLine();
                }
            }
            try {
                Files.move(tempFile, registryFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempFile, registryFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Resolves a tenant root from the registry and optionally falls back to the legacy layout.
     * <p>
     * The fallback is only a business policy for the transition period. Disable it once all tenants have been
     * migrated and their registry records point to the new paths.
     *
     * @since 0.1.17
     */
    static final class RegistryBackedLocator implements TenantWorkspaceLocator {
        private final TenantWorkspaceRegistry registry;
        private final String baseDir;
        private final boolean isLegacyFallbackEnabled;

        RegistryBackedLocator(TenantWorkspaceRegistry registry, String baseDir, boolean isLegacyFallbackEnabled) {
            this.registry = registry;
            this.baseDir = baseDir;
            this.isLegacyFallbackEnabled = isLegacyFallbackEnabled;
        }

        @Override
        public Optional<Path> locate(TenantContext ctx) {
            if (ctx == null || !ctx.isTenantAware()) {
                return Optional.empty();
            }
            String tenantId = ctx.getTenantId();
            Optional<String> recordedPath = registry.findWorkspace(tenantId);
            if (recordedPath.isPresent()) {
                return Optional.of(Path.of(recordedPath.get()));
            }
            Path legacyPath = Path.of(legacyWorkspace(baseDir, tenantId));
            if (isLegacyFallbackEnabled && Files.isDirectory(legacyPath)) {
                return Optional.of(legacyPath);
            }
            return Optional.empty();
        }
    }
}
