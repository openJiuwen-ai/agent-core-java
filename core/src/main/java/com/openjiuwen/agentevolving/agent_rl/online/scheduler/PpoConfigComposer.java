/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.scheduler;

import com.openjiuwen.agentevolving.agent_rl.config.OnlinePpoVerlConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Build Hydra-like PPO config maps for online training.
 * <p>
 * Mirrors Python's {@code compose_online_ppo_config} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/scheduler/ppo_config.py}.
 */
public final class PpoConfigComposer {

    private static final String DEFAULT_LOCAL_DIR = "/tmp/online_ppo_ckpt";

    private PpoConfigComposer() {
    }

    public static Map<String, Object> composeOnlinePpoConfig(
            String modelPath,
            int nGpusPerNode,
            String configPath) {
        Map<String, Object> cfg;
        if (configPath == null) {
            cfg = deepCopyMap(OnlinePpoVerlConfig.getOnlinePpoVerlHydraOverlay());
        } else {
            cfg = loadYamlConfig(Paths.get(configPath));
        }

        Map<String, Object> actorRolloutRef = nestedMap(cfg, "actor_rollout_ref");
        nestedMap(actorRolloutRef, "model").put("path", modelPath);

        Map<String, Object> trainer = nestedMap(cfg, "trainer");
        trainer.put("n_gpus_per_node", nGpusPerNode);
        if (!pythonTruthy(trainer.get("default_local_dir"))) {
            trainer.put("default_local_dir", DEFAULT_LOCAL_DIR);
        }
        return cfg;
    }

    public static Map<String, Object> composeOnlinePpoConfig(String modelPath) {
        return composeOnlinePpoConfig(modelPath, 2, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlConfig(Path configPath) {
        try {
            Object loaded = new Yaml().load(Files.readString(configPath.toAbsolutePath()));
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("PPO config YAML must contain a mapping: " + configPath);
            }
            return deepCopyMap((Map<String, Object>) map);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to load PPO config: " + configPath, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        Object existing = parent.get(key);
        if (existing instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        parent.put(key, created);
        return created;
    }

    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), deepCopyValue(value)));
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return deepCopyMap((Map<String, Object>) map);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(PpoConfigComposer::deepCopyValue).toList();
        }
        return value;
    }

    private static boolean pythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
