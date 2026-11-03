// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agentevolving.agent_rl.optimizer;

import com.openjiuwen.agentevolving.agent_rl.optimizer.runtime.PpoRayRuntimeEnvHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Shared helpers for PPO task runners.
 *
 * <p>Mirrors Python's module-level helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/optimizer/task_runner.py}.</p>
 */
public final class TaskRunner {

    private static final Logger LOGGER = Logger.getLogger(TaskRunner.class.getName());

    private static final Path AGENT_CORE_DIR = resolveAgentCoreDir();

    private static volatile RemoteArtifactResolver remoteArtifactResolver;

    private TaskRunner() {
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

    public record ResourcePoolSpec(Map<String, List<Integer>> resourcePoolSpec, Map<String, String> mapping) {
        public ResourcePoolSpec {
            resourcePoolSpec = deepImmutableResourcePoolSpec(resourcePoolSpec);
            mapping = Map.copyOf(mapping == null ? Map.of() : mapping);
        }
    }

    public static void setRemoteArtifactResolver(RemoteArtifactResolver resolver) {
        remoteArtifactResolver = resolver;
    }

    public static Map<String, Object> getPpoRayRuntimeEnv() {
        return PpoRayRuntimeEnvHelper.buildRuntimeEnv(
                AGENT_CORE_DIR.toString(),
                System.getenv("PYTHONPATH"),
                System.getenv()
        );
    }

    public static Map<String, Object> getDefaultRayRuntimeEnv() {
        return getPpoRayRuntimeEnv();
    }

    public static ModelComponents initModelComponents(Map<String, Object> config) {
        String modelPath = stringAt(config, "actor_rollout_ref", "model", "path");
        boolean trustRemoteCode = booleanAt(config, false, "data", "trust_remote_code");
        Path localModelPath = copyToLocal(modelPath);
        return new ModelComponents(
                new ModelComponentRef("tokenizer", localModelPath, Map.of("trust_remote_code", trustRemoteCode)),
                new ModelComponentRef("processor", localModelPath, Map.of("use_fast", true))
        );
    }

    public static ResourcePoolSpec initResourcePools(Map<String, Object> config, Map<String, String> roleMapping) {
        int perNode = intAt(config, "trainer", "n_gpus_per_node");
        int nodeCount = intAt(config, "trainer", "nnodes");
        List<Integer> perNodeSpec = new ArrayList<>();
        for (int index = 0; index < nodeCount; index++) {
            perNodeSpec.add(perNode);
        }
        return new ResourcePoolSpec(Map.of("global_pool", perNodeSpec), roleMapping);
    }

    public static Path copyToLocal(String source) {
        return copyToLocal(source, null);
    }

    public static Map<String, Object> deepMutableCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        source.forEach((key, value) -> copy.put(key, deepMutableValue(value)));
        return copy;
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
            LOGGER.info("Copied artifact from " + sourcePath + " to " + targetPath);
            return targetPath;
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to copy artifact from " + source, exception);
        }
    }

    public static String stringAt(Map<String, Object> root, String... path) {
        if (root == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (path.length == 0) {
            throw new IllegalArgumentException("path must not be empty");
        }
        Map<String, Object> parent = path.length == 1 ? root : mapAt(root, Arrays.copyOf(path, path.length - 1));
        Object value = parent.get(path[path.length - 1]);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("missing config value: " + String.join(".", path));
        }
        return String.valueOf(value);
    }

    public static boolean booleanAt(Map<String, Object> root, boolean defaultValue, String... path) {
        if (root == null || path.length == 0) {
            return defaultValue;
        }
        Map<String, Object> parent = path.length == 1 ? root : mapAt(root, Arrays.copyOf(path, path.length - 1));
        Object value = parent.get(path[path.length - 1]);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> mapAt(Map<String, Object> root, String... path) {
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

    private static int intAt(Map<String, Object> root, String... path) {
        Object value = mapAt(root, Arrays.copyOf(path, path.length - 1)).get(path[path.length - 1]);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
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

    private static Path resolveAgentCoreDir() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd);
        candidates.add(cwd.resolve("agent-core-0.1.14"));
        if (cwd.getParent() != null) {
            candidates.add(cwd.getParent().resolve("agent-core-0.1.14"));
        }
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate.resolve("openjiuwen"))) {
                return candidate;
            }
        }
        return cwd;
    }

    private static Map<String, List<Integer>> deepImmutableResourcePoolSpec(Map<String, List<Integer>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Integer>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, List.copyOf(value == null ? List.of() : value)));
        return Map.copyOf(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object deepMutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepMutableValue(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list.stream().map(TaskRunner::deepMutableValue).toList());
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            for (Object item : iterable) {
                copy.add(deepMutableValue(item));
            }
            return copy;
        }
        return value;
    }
}
