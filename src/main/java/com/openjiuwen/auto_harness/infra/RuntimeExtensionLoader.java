/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Load runtime extension resources from a session-local runtime directory.
 * <p>
 * Mirrors Python's runtime loader functions in
 * {@code openjiuwen/auto_harness/infra/runtime_extension_loader.py}.
 */
public final class RuntimeExtensionLoader {

    private static final String OFFICIAL_PREFIX = "openjiuwen.extensions.harness.";
    private static final String RUNTIME_PREFIX = "openjiuwen_runtime_extensions";
    private static final String PACKAGE_RESOURCE_TYPE = "package";

    private RuntimeExtensionLoader() {
    }

    /**
     * Load rail classes declared by a runtime extension manifest.
     *
     * @param runtimeExt runtime extension artifact
     * @param sessionId session-local extension namespace id
     * @return loaded rail classes
     */
    public static List<Class<?>> loadRuntimeRails(RuntimeExtensionArtifact runtimeExt, String sessionId) {
        ResolvedHarnessConfig resolved = loadRuntimeConfig(runtimeExt);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null || resources.getRails() == null) {
            return List.of();
        }

        List<Class<?>> rails = new ArrayList<>();
        for (HarnessConfig.RailResourceSchema spec : resources.getRails()) {
            if (spec == null || !PACKAGE_RESOURCE_TYPE.equals(spec.getType())) {
                continue;
            }
            if (isBlank(spec.getModule()) || isBlank(spec.getClassName())) {
                continue;
            }
            rails.add(loadRuntimeClass(
                    runtimeExt,
                    sessionId,
                    spec.getModule(),
                    spec.getClassName()
            ));
        }
        return rails;
    }

    /**
     * Load tool classes declared by a runtime extension manifest.
     *
     * @param runtimeExt runtime extension artifact
     * @param sessionId session-local extension namespace id
     * @return loaded tool classes
     */
    public static List<Class<?>> loadRuntimeTools(RuntimeExtensionArtifact runtimeExt, String sessionId) {
        ResolvedHarnessConfig resolved = loadRuntimeConfig(runtimeExt);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null || resources.getTools() == null) {
            return List.of();
        }

        List<Class<?>> tools = new ArrayList<>();
        for (HarnessConfig.ToolResourceSchema spec : resources.getTools()) {
            if (spec == null || !PACKAGE_RESOURCE_TYPE.equals(spec.getType())) {
                continue;
            }
            if (isBlank(spec.getModule()) || isBlank(spec.getClassName())) {
                continue;
            }
            tools.add(loadRuntimeClass(
                    runtimeExt,
                    sessionId,
                    spec.getModule(),
                    spec.getClassName()
            ));
        }
        return tools;
    }

    /**
     * Return absolute skill directory paths declared by a runtime extension.
     *
     * @param runtimeExt runtime extension artifact
     * @return existing absolute skill directory paths
     */
    public static List<String> loadRuntimeSkillDirs(RuntimeExtensionArtifact runtimeExt) {
        ResolvedHarnessConfig resolved = loadRuntimeConfig(runtimeExt);
        HarnessConfig.ResourcesSchema resources = resourcesOf(resolved);
        if (resources == null || resources.getSkills() == null || resources.getSkills().getDirs() == null) {
            return List.of();
        }

        Path root = Path.of(nullToEmpty(runtimeExt.getRuntimePath())).toAbsolutePath().normalize();
        List<String> dirs = new ArrayList<>();
        for (String dir : resources.getSkills().getDirs()) {
            Path skillPath = root.resolve(nullToEmpty(dir)).normalize();
            if (Files.isDirectory(skillPath)) {
                dirs.add(skillPath.toString());
            }
        }
        return dirs;
    }

    private static ResolvedHarnessConfig loadRuntimeConfig(RuntimeExtensionArtifact runtimeExt) {
        if (runtimeExt == null) {
            throw new IllegalArgumentException("runtimeExt must not be null");
        }
        return HarnessConfigLoader.load(Path.of(nullToEmpty(runtimeExt.getConfigPath())));
    }

    private static Class<?> loadRuntimeClass(
            RuntimeExtensionArtifact runtimeExt,
            String sessionId,
            String moduleName,
            String className
    ) {
        String extensionName = nullToEmpty(runtimeExt.getExtensionName());
        String prefix = OFFICIAL_PREFIX + extensionName;
        if (!moduleName.equals(prefix) && !moduleName.startsWith(prefix + ".")) {
            throw new IllegalArgumentException(
                    "Runtime module does not belong to runtime extension '" + extensionName + "': " + moduleName
            );
        }

        String relativeModule = moduleName.substring(prefix.length());
        if (relativeModule.startsWith(".")) {
            relativeModule = relativeModule.substring(1);
        }

        Path root = Path.of(nullToEmpty(runtimeExt.getRuntimePath())).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Runtime extension path not found: " + root);
        }

        List<String> candidates = buildClassNameCandidates(
                sessionId,
                extensionName,
                moduleName,
                relativeModule,
                className
        );
        ClassLoader urlLoader = createRuntimeClassLoader(root);
        List<ClassLoader> loaders = new ArrayList<>();
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            loaders.add(contextLoader);
        }
        loaders.add(RuntimeExtensionLoader.class.getClassLoader());
        loaders.add(urlLoader);
        for (String candidate : candidates) {
            for (ClassLoader loader : loaders) {
                try {
                    return Class.forName(candidate, true, loader);
                } catch (ClassNotFoundException ignored) {
                    // Try the next candidate or class loader.
                }
            }
        }
        throw new IllegalArgumentException(
                "Runtime extension class not found: " + className + " (module " + moduleName + ")"
        );
    }

    private static List<String> buildClassNameCandidates(
            String sessionId,
            String extensionName,
            String moduleName,
            String relativeModule,
            String className
    ) {
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(className);
        if (!className.contains(".") && !className.contains("$")) {
            candidates.add(moduleName + "." + className);
            String uniqueBase = RUNTIME_PREFIX + "." + nullToEmpty(sessionId) + "." + extensionName;
            String uniqueModule = relativeModule.isBlank() ? uniqueBase : uniqueBase + "." + relativeModule;
            candidates.add(uniqueModule + "." + className);
            if (!relativeModule.isBlank()) {
                candidates.add(relativeModule + "." + className);
            }
        }
        return new ArrayList<>(candidates);
    }

    private static ClassLoader createRuntimeClassLoader(Path root) {
        try {
            URL[] urls = new URL[] {root.toUri().toURL()};
            return new URLClassLoader(urls, RuntimeExtensionLoader.class.getClassLoader());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid runtime extension path: " + root, e);
        }
    }

    private static HarnessConfig.ResourcesSchema resourcesOf(ResolvedHarnessConfig resolved) {
        HarnessConfig config = resolved.getConfig();
        return config == null ? null : config.getResources();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
