/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.tiered;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads the built-in parameter-level security rules from the classpath.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.tiered_policy.get_builtin_security_rules}.
 * The resource {@code harness/security/builtin_rules.yaml} ships with the jar; results are cached
 * for the process lifetime.
 *
 * @since 0.1.15
 */
public final class BuiltinRules {
    private static final Logger logger = LoggerFactory.getLogger(BuiltinRules.class);
    private static final String RESOURCE = "/harness/security/builtin_rules.yaml";
    private static volatile List<Map<String, Object>> cached;

    private BuiltinRules() {
    }

    /**
     * Built-in security rules (process-cached).
     *
     * @return unmodifiable list of rule maps; empty when the resource is missing
     * @since 0.1.15
     */
    public static List<Map<String, Object>> get() {
        List<Map<String, Object>> snapshot = cached;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (BuiltinRules.class) {
            if (cached != null) {
                return cached;
            }
            List<Map<String, Object>> rules = new ArrayList<>();
            try (InputStream in = BuiltinRules.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    rules = parseRules(in);
                }
            } catch (IOException | YAMLException ex) {
                logger.warn("Failed to load built-in permission rules from {}", RESOURCE, ex);
            }
            cached = Collections.unmodifiableList(rules);
            return cached;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseRules(InputStream in) {
        List<Map<String, Object>> rules = new ArrayList<>();
        Object data = new Yaml().load(in);
        if (!(data instanceof Map<?, ?> root)) {
            return rules;
        }
        Object raw = root.get("rules");
        if (!(raw instanceof List<?> list)) {
            return rules;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rules.add((Map<String, Object>) map);
            }
        }
        return rules;
    }
}
