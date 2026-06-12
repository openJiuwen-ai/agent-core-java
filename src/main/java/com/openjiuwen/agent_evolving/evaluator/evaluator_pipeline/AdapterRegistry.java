/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.stream.Stream;

/**
 * Adapter registry and factory helpers for evaluator pipeline agents and benchmarks.
 *
 * <p>Mirrors Python's registry helpers in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/base.py}.</p>
 */
public final class AdapterRegistry {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final String ROOT_PACKAGE = "com.openjiuwen.agent_evolving.evaluator.evaluator_pipeline.adapters";

    private static final Map<String, Class<? extends BaseAgentAdapter>> AGENT_REGISTRY =
            Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, Class<? extends BaseBenchAdapter>> BENCH_REGISTRY =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private AdapterRegistry() {
    }

    public static void registerAgent(String name, Class<? extends BaseAgentAdapter> adapterClass) {
        validateRegistration(name, adapterClass);
        AGENT_REGISTRY.put(name, adapterClass);
    }

    public static void registerBenchmark(String name, Class<? extends BaseBenchAdapter> adapterClass) {
        validateRegistration(name, adapterClass);
        BENCH_REGISTRY.put(name, adapterClass);
    }

    public static BaseAgentAdapter createAgent(String name, Map<String, Object> config) {
        if (!AGENT_REGISTRY.containsKey(name)) {
            tryLoadBuiltinAgentAdapters();
            discoverAdapters("agents");
        }
        Class<? extends BaseAgentAdapter> adapterClass = AGENT_REGISTRY.get(name);
        if (adapterClass == null) {
            throw new IllegalArgumentException(
                    "Unknown agent: " + name + ". Available: " + new ArrayList<>(AGENT_REGISTRY.keySet()));
        }
        return instantiate(adapterClass, config);
    }

    public static BaseBenchAdapter createBenchmark(String name, Map<String, Object> config) {
        if (!BENCH_REGISTRY.containsKey(name)) {
            tryLoadBuiltinBenchmarkAdapters();
            discoverAdapters("benchmarks");
        }
        Class<? extends BaseBenchAdapter> adapterClass = BENCH_REGISTRY.get(name);
        if (adapterClass == null) {
            throw new IllegalArgumentException(
                    "Unknown benchmark: " + name + ". Available: " + new ArrayList<>(BENCH_REGISTRY.keySet()));
        }
        return instantiate(adapterClass, config);
    }

    public static List<String> getRegisteredAgentNames() {
        return new ArrayList<>(AGENT_REGISTRY.keySet());
    }

    public static List<String> getRegisteredBenchmarkNames() {
        return new ArrayList<>(BENCH_REGISTRY.keySet());
    }

    static void clearRegistriesForTesting() {
        AGENT_REGISTRY.clear();
        BENCH_REGISTRY.clear();
    }

    private static void tryLoadBuiltinAgentAdapters() {
        tryLoadClass(ROOT_PACKAGE + ".agents.JiuWenSwarmAgent");
        tryLoadClass(ROOT_PACKAGE + ".agents.JiuwenSwarmAgent");
    }

    private static void tryLoadBuiltinBenchmarkAdapters() {
        tryLoadClass(ROOT_PACKAGE + ".benchmarks.SkillsBenchAdapter");
    }

    private static void discoverAdapters(String packageName) {
        String resourcePath = ROOT_PACKAGE.replace('.', '/') + "/" + packageName;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            Enumeration<URL> resources = classLoader.getResources(resourcePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equalsIgnoreCase(url.getProtocol())) {
                    discoverFileAdapters(packageName, url);
                } else if ("jar".equalsIgnoreCase(url.getProtocol())) {
                    discoverJarAdapters(packageName, url, resourcePath);
                }
            }
        } catch (IOException exception) {
            LOGGER.warning("Failed to discover {} adapters: {}", packageName, exception.getMessage());
        }
    }

    private static void discoverFileAdapters(String packageName, URL url) {
        try {
            Path dir = Path.of(url.toURI());
            if (!Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> children = Files.list(dir)) {
                children.filter(path -> path.getFileName().toString().endsWith(".class"))
                        .filter(path -> !path.getFileName().toString().contains("$"))
                        .forEach(path -> {
                            String simpleName = path.getFileName().toString().replace(".class", "");
                            tryLoadClass(ROOT_PACKAGE + "." + packageName + "." + simpleName);
                        });
            }
        } catch (IOException | URISyntaxException exception) {
            LOGGER.warning("Failed to scan {} adapters at {}: {}", packageName, url, exception.getMessage());
        }
    }

    private static void discoverJarAdapters(String packageName, URL url, String resourcePath) {
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (var jarFile = connection.getJarFile()) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (!entryName.startsWith(resourcePath) || !entryName.endsWith(".class") || entryName.contains("$")) {
                        continue;
                    }
                    String className = entryName.replace('/', '.').replace(".class", "");
                    tryLoadClass(className);
                }
            }
        } catch (IOException exception) {
            LOGGER.warning("Failed to scan jar adapters for {}: {}", packageName, exception.getMessage());
        }
    }

    private static void tryLoadClass(String className) {
        try {
            Class.forName(className, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException ignored) {
            // Optional built-in adapter may not be translated yet.
        } catch (Exception exception) {
            String simpleName = className.substring(className.lastIndexOf('.') + 1);
            String packageLabel = className.toLowerCase(Locale.ROOT).contains(".benchmarks.") ? "benchmarks" : "agents";
            LOGGER.warning("Failed to load {} adapter module '{}': {}", packageLabel, simpleName, exception.getMessage());
        }
    }

    private static <T> T instantiate(Class<? extends T> adapterClass, Map<String, Object> config) {
        try {
            Constructor<? extends T> mapConstructor = adapterClass.getDeclaredConstructor(Map.class);
            mapConstructor.setAccessible(true);
            return mapConstructor.newInstance(config != null ? config : Map.of());
        } catch (NoSuchMethodException ignored) {
            try {
                Constructor<? extends T> emptyConstructor = adapterClass.getDeclaredConstructor();
                emptyConstructor.setAccessible(true);
                return emptyConstructor.newInstance();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to instantiate " + adapterClass.getName(), exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate " + adapterClass.getName(), exception);
        }
    }

    private static void validateRegistration(String name, Class<?> adapterClass) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("registration name must not be blank");
        }
        if (adapterClass == null) {
            throw new IllegalArgumentException("adapterClass must not be null");
        }
    }
}
