// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.optimizer;

import com.openjiuwen.agent_evolving.agent_rl.optimizer.runtime.PpoRayRuntimeEnvHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Task runner for PPO training with Ray integration.
 * <p>
 * Mirrors Python's {@code task_runner.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.optimizer.task_runner}.
 */
public final class TaskRunner {
    
    private static final Logger logger = Logger.getLogger(TaskRunner.class.getName());
    
    // Agent core directory (Python project root)
    private static final Path AGENT_CORE_DIR = resolveAgentCoreDir();

    private static volatile RemoteArtifactResolver remoteArtifactResolver;
    
    private TaskRunner() {
        // Utility class
    }

    @FunctionalInterface
    public interface RemoteArtifactResolver {
        Path copyToLocal(String source, Path target) throws IOException;
    }

    public record ModelComponentRef(String componentType, Path modelPath, Map<String, Object> options) {
        public ModelComponentRef {
            Objects.requireNonNull(componentType, "componentType must not be null");
            Objects.requireNonNull(modelPath, "modelPath must not be null");
            options = Map.copyOf(options == null ? Map.of() : options);
        }
    }

    public record ModelComponents(ModelComponentRef tokenizer, ModelComponentRef processor) {
        public ModelComponents {
            Objects.requireNonNull(tokenizer, "tokenizer must not be null");
            Objects.requireNonNull(processor, "processor must not be null");
        }
    }

    public static void setRemoteArtifactResolver(RemoteArtifactResolver resolver) {
        remoteArtifactResolver = resolver;
    }
    
    /**
     * Get PPO Ray runtime environment configuration.
     */
    public static Map<String, Object> getPpoRayRuntimeEnv() {
        return PpoRayRuntimeEnvHelper.buildRuntimeEnv(
            AGENT_CORE_DIR.toString(),
            System.getenv("PYTHONPATH"),
            System.getenv()
        );
    }

    private static Path resolveAgentCoreDir() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core-0.1.12"));
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("agent-core-0.1.12"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("openjiuwen"))) {
                return candidate;
            }
        }
        return cwd;
    }
    
    /**
     * Get default Ray runtime environment.
     */
    public static Map<String, Object> getDefaultRayRuntimeEnv() {
        return getPpoRayRuntimeEnv();
    }
    
    /**
     * Load HF tokenizer.
     */
    public static Object loadTokenizer(String modelPath) {
        return loadTokenizer(modelPath, false);
    }

    /**
     * Load HF tokenizer metadata after resolving the model path locally.
     */
    public static ModelComponentRef loadTokenizer(String modelPath, boolean trustRemoteCode) {
        Path localModelPath = copyToLocal(modelPath);
        return new ModelComponentRef(
            "tokenizer",
            localModelPath,
            Map.of("trust_remote_code", trustRemoteCode)
        );
    }
    
    /**
     * Load HF processor.
     */
    public static Object loadProcessor(String modelPath) {
        return loadProcessor(modelPath, true);
    }

    /**
     * Load HF processor metadata after resolving the model path locally.
     */
    public static ModelComponentRef loadProcessor(String modelPath, boolean useFast) {
        Path localModelPath = copyToLocal(modelPath);
        return new ModelComponentRef(
            "processor",
            localModelPath,
            Map.of("use_fast", useFast)
        );
    }

    /**
     * Mirrors Python's BaseTaskRunner._init_model_components(config).
     */
    public static ModelComponents initModelComponents(Map<String, Object> config) {
        String modelPath = stringAt(config, "actor_rollout_ref", "model", "path");
        boolean trustRemoteCode = booleanAt(config, false, "data", "trust_remote_code");
        Path localModelPath = copyToLocal(modelPath);
        return new ModelComponents(
            new ModelComponentRef(
                "tokenizer",
                localModelPath,
                Map.of("trust_remote_code", trustRemoteCode)
            ),
            new ModelComponentRef(
                "processor",
                localModelPath,
                Map.of("use_fast", true)
            )
        );
    }
    
    /**
     * Copy to local path.
     * <p>
     * Mirrors Python's {@code copy_to_local} from {@code verl.utils.fs}.
     * <p>
     * For remote sources (hf://, s3://), delegates to the configured resolver.
     * For local sources, performs direct file copy.
     *
     * @param source Source path (local, hf://, or s3://)
     * @param target Target path
     * @return Resolved local path
     */
    public static Path copyToLocal(String source) {
        return copyToLocal(source, null);
    }

    public static Path copyToLocal(String source, Path target) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }

        try {
            if (isRemoteSource(source)) {
                RemoteArtifactResolver resolver = remoteArtifactResolver;
                if (resolver == null) {
                    throw new IllegalStateException("remote artifact resolver is not configured for " + source);
                }
                Path resolvedTarget = target != null ? target : defaultRemoteTarget(source);
                Path resolved = resolver.copyToLocal(source, resolvedTarget);
                if (resolved == null) {
                    throw new IllegalStateException("remote artifact resolver returned null for " + source);
                }
                return resolved.toAbsolutePath().normalize();
            }

            Path sourcePath = Paths.get(source).toAbsolutePath().normalize();
            if (!Files.exists(sourcePath)) {
                throw new IllegalArgumentException("source path does not exist: " + source);
            }
            if (target == null) {
                return sourcePath;
            }

            Path targetPath = target.toAbsolutePath().normalize();
            if (Files.isDirectory(sourcePath)) {
                copyDirectory(sourcePath, targetPath);
            } else {
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Copied artifact from " + sourcePath + " to " + targetPath);
            return targetPath;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to copy artifact from " + source, exception);
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    if (destination.getParent() != null) {
                        Files.createDirectories(destination.getParent());
                    }
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean isRemoteSource(String source) {
        return source.startsWith("hf://")
            || source.startsWith("s3://")
            || source.startsWith("http://")
            || source.startsWith("https://");
    }

    private static Path defaultRemoteTarget(String source) {
        String safeName = Integer.toHexString(source.hashCode());
        return Paths.get(System.getProperty("java.io.tmpdir"), "openjiuwen-agent-rl-cache", safeName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
        Object current = root;
        for (String part : path) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return Map.of();
            }
            current = currentMap.get(part);
        }
        if (current instanceof Map<?, ?> result) {
            return (Map<String, Object>) result;
        }
        return Map.of();
    }

    private static String stringAt(Map<String, Object> root, String... path) {
        if (root == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (path.length == 0) {
            throw new IllegalArgumentException("path must not be empty");
        }
        Map<String, Object> parent = path.length == 1
            ? root
            : mapAt(root, Arrays.copyOf(path, path.length - 1));
        Object value = parent.get(path[path.length - 1]);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("missing config value: " + String.join(".", path));
        }
        return String.valueOf(value);
    }

    private static boolean booleanAt(Map<String, Object> root, boolean defaultValue, String... path) {
        if (root == null || path.length == 0) {
            return defaultValue;
        }
        Map<String, Object> parent = path.length == 1
            ? root
            : mapAt(root, Arrays.copyOf(path, path.length - 1));
        Object value = parent.get(path[path.length - 1]);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
