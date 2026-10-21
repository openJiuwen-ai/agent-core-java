/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills.repository;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Read-only Skill repository that discovers SKILL.md resources on the classpath, including entries
 * inside JAR files.
 *
 * @since 0.1.17
 */
public final class ClasspathSkillRepository implements SkillRepository {
    private static final String[] SKILL_FILE_NAMES = {"SKILL.md", "Skill.md", "skill.md"};

    private final String classpathRoot;

    public ClasspathSkillRepository(String classpathRoot) {
        this.classpathRoot = classpathRoot == null ? "" : classpathRoot.replaceAll("^/+", "");
    }

    @Override
    public SkillSource source() {
        return SkillSource.CLASSPATH;
    }

    @Override
    public List<Skill> loadAll() {
        List<Skill> skills = new ArrayList<>();
        Set<String> resourcePaths = discoverSkillFileNames();
        for (String resourcePath : resourcePaths) {
            loadSkillFromResource(resourcePath).ifPresent(skills::add);
        }
        return List.copyOf(skills);
    }

    private Set<String> discoverSkillFileNames() {
        Set<String> paths = new LinkedHashSet<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = ClasspathSkillRepository.class.getClassLoader();
            }
            Enumeration<URL> roots =
                    classLoader.getResources(classpathRoot.isEmpty() ? "" : classpathRoot);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                URLConnection connection = root.openConnection();
                if (connection instanceof JarURLConnection jarConnection) {
                    collectFromJar(jarConnection, paths);
                }
                if ("file".equals(root.getProtocol())) {
                    collectFromFilesystem(root, paths, classpathRoot);
                }
            }
        } catch (IOException exception) {
            Loggers.COMMON.debug("Optional classpath skill resource lookup failed", exception);
        }
        return paths;
    }

    private void collectFromFilesystem(URL rootUrl, Set<String> paths, String classpathRoot)
            throws IOException {
        try {
            Path baseDir = Path.of(rootUrl.toURI());
            Path rootDir = baseDir;
            if (!classpathRoot.isEmpty() && !rootDir.endsWith(classpathRoot)) {
                rootDir = baseDir.resolve(classpathRoot);
            }
            if (!java.nio.file.Files.isDirectory(rootDir)) {
                return;
            }
            Path finalRootDir = rootDir;
            try (java.util.stream.Stream<Path> stream = java.nio.file.Files.walk(finalRootDir)) {
                stream.filter(java.nio.file.Files::isRegularFile)
                        .filter(path -> isSkillFileName(path.getFileName().toString()))
                        .forEach(
                                path ->
                                        paths.add(
                                                classpathRoot
                                                        + "/"
                                                        + finalRootDir
                                                                .relativize(path)
                                                                .toString()
                                                                .replace('\\', '/')));
            }
        } catch (java.net.URISyntaxException exception) {
            Loggers.COMMON.debug("Invalid classpath skill resource URI", exception);
        }
    }

    private void collectFromJar(JarURLConnection connection, Set<String> paths) throws IOException {
        JarFile jarFile = connection.getJarFile();
        try {
            String rootPrefix = classpathRoot.isEmpty() ? "" : classpathRoot + "/";
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(rootPrefix)) {
                    continue;
                }
                String relative = name.substring(rootPrefix.length());
                if (isSkillFileName(relative)) {
                    paths.add(name);
                }
            }
        } finally {
            jarFile.close();
        }
    }

    private static boolean isSkillFileName(String relativePath) {
        String fileName =
                relativePath.contains("/")
                        ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                        : relativePath;
        for (String skillFileName : SKILL_FILE_NAMES) {
            if (skillFileName.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Skill> loadSkillFromResource(String resourcePath) {
        try (InputStream inputStream =
                Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            return buildSkill(text, resourcePath);
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Skill> buildSkill(String text, String logicalPath) {
        Map<String, String> frontMatter = SkillFrontMatterParser.parse(text);
        String name = frontMatter.getOrDefault("name", deriveNameFromPath(logicalPath));
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String directoryPath =
                logicalPath.contains("/")
                        ? logicalPath.substring(0, logicalPath.lastIndexOf('/'))
                        : "";
        Skill skill =
                new Skill(
                        name,
                        frontMatter.get("description"),
                        directoryPath.isEmpty() ? null : Path.of(directoryPath));
        skill.setSource(SkillSource.CLASSPATH);
        skill.setId(frontMatter.getOrDefault("id", name));
        skill.setProtectedSkill(
                Boolean.parseBoolean(frontMatter.getOrDefault("protected", "false")));
        return Optional.of(skill);
    }

    private static String deriveNameFromPath(String logicalPath) {
        String withoutFile =
                logicalPath.contains("/")
                        ? logicalPath.substring(0, logicalPath.lastIndexOf('/'))
                        : logicalPath;
        return withoutFile.contains("/")
                ? withoutFile.substring(withoutFile.lastIndexOf('/') + 1)
                : withoutFile;
    }
}
