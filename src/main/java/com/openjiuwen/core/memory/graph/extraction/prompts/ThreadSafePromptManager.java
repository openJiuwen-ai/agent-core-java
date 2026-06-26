/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.runner.resourcemanager.PromptManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Thread-safe prompt template manager for graph-memory extraction prompts.
 *
 * <p>Mirrors Python's {@code ThreadSafePromptManager} in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/manager.py}.</p>
 */
public final class ThreadSafePromptManager {

    private static final Pattern PR_PATTERN = Pattern.compile("`#((?:user)|(?:system)|(?:assistant)|(?:tool))#`",
            Pattern.DOTALL);
    private static final Set<String> ROLES = Set.of("user", "system", "assistant", "tool");
    private static final String RESOURCE_ROOT = "openjiuwen/core/memory/graph/extraction/prompts";
    private static final ReentrantLock THREAD_LOCK = new ReentrantLock(true);

    private static volatile ThreadSafePromptManager instance;

    private final Set<String> allPromptNames = new LinkedHashSet<>();
    private final PromptManager manager = new PromptManager();
    private boolean initialized;

    private ThreadSafePromptManager() {
        this(defaultPromptRoots());
    }

    ThreadSafePromptManager(Collection<Path> promptRoots) {
        initialize(promptRoots);
    }

    /**
     * Return the singleton prompt manager, matching Python's {@code __new__} guarded instance.
     *
     * @return singleton manager
     */
    public static ThreadSafePromptManager getInstance() {
        THREAD_LOCK.lock();
        try {
            if (instance == null) {
                instance = new ThreadSafePromptManager();
            }
            return instance;
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    /**
     * Load prompt template messages from a {@code .pr.md} file body.
     *
     * @param content prompt file content
     * @return ordered message dictionaries with {@code role} and {@code content}
     */
    public static List<Map<String, String>> loadPrContent(String content) {
        List<String> segments = splitWithRoles(content == null ? "" : content);
        boolean matchingRole = true;
        Map<String, String> currentMessage = null;
        List<Map<String, String>> messages = new ArrayList<>();
        for (String segment : segments) {
            if (segment == null || segment.isEmpty()) {
                continue;
            }
            if (matchingRole) {
                if (ROLES.contains(segment)) {
                    currentMessage = new LinkedHashMap<>();
                    currentMessage.put("role", segment);
                    currentMessage.put("content", "");
                    matchingRole = false;
                }
            } else {
                currentMessage.put("content", segment);
                messages.add(currentMessage);
                currentMessage = null;
                matchingRole = true;
            }
        }
        return messages;
    }

    /**
     * Python {@code __contains__} equivalent.
     *
     * @param key prompt name candidate
     * @return true when the prompt has been registered
     */
    public boolean contains(Object key) {
        THREAD_LOCK.lock();
        try {
            return allPromptNames.contains(key);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    /**
     * Get a registered prompt template.
     *
     * @param name prompt name
     * @return prompt template or null
     */
    public PromptTemplate get(String name) {
        THREAD_LOCK.lock();
        try {
            return manager.getPrompt(name);
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    public void registerInBulk(String promptDir) {
        registerInBulk(Path.of(promptDir), "");
    }

    public void registerInBulk(String promptDir, String name) {
        registerInBulk(Path.of(promptDir), name);
    }

    public void registerInBulk(Path promptDir, String name) {
        THREAD_LOCK.lock();
        try {
            List<Path> promptPaths = listPromptFiles(promptDir);
            if (promptPaths.isEmpty()) {
                throw ErrorHelper.buildError(
                        StatusCode.MEMORY_GRAPH_PROMPT_FILES_MISSING,
                        "prompt_dir",
                        promptDir.toString()
                );
            }
            registerTemplates(promptPaths);
            String effectiveName = name == null || name.isEmpty()
                    ? promptDir.getFileName().toString()
                    : name;
            Loggers.MEMORY.info(String.format(Locale.ROOT,
                    "Graph Memory: loaded %d prompts from %s",
                    promptPaths.size(),
                    effectiveName));
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    private static List<String> splitWithRoles(String content) {
        List<String> segments = new ArrayList<>();
        Matcher matcher = PR_PATTERN.matcher(content);
        int cursor = 0;
        while (matcher.find()) {
            segments.add(content.substring(cursor, matcher.start()));
            segments.add(matcher.group(1));
            cursor = matcher.end();
        }
        segments.add(content.substring(cursor));
        return segments;
    }

    private void initialize(Collection<Path> promptRoots) {
        THREAD_LOCK.lock();
        try {
            if (initialized) {
                return;
            }
            Set<Path> languageDirs = new java.util.TreeSet<>(Comparator.comparing(Path::toString));
            for (Path promptRoot : promptRoots) {
                if (promptRoot == null || !Files.isDirectory(promptRoot)) {
                    continue;
                }
                try (Stream<Path> stream = Files.walk(promptRoot)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".pr.md"))
                            .map(Path::getParent)
                            .forEach(languageDirs::add);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
            for (Path languageDir : languageDirs) {
                String name = languageDir.getFileName().toString().strip();
                registerInBulk(languageDir, name);
            }
            initialized = true;
        } finally {
            THREAD_LOCK.unlock();
        }
    }

    private void registerTemplates(List<Path> templatePaths) {
        List<PromptManager.PromptEntry> prompts = new ArrayList<>();
        for (Path templatePath : templatePaths) {
            String templateName = removePromptSuffix(templatePath.getFileName().toString());
            String content;
            try {
                content = Files.readString(templatePath, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            PromptTemplate template = PromptTemplate.builder()
                    .name(templateName)
                    .content(loadPrContent(content))
                    .build();
            allPromptNames.add(templateName);
            prompts.add(new PromptManager.PromptEntry(templateName, template));
        }
        manager.addPrompts(prompts);
    }

    private static List<Path> listPromptFiles(Path promptDir) {
        if (promptDir == null || !Files.isDirectory(promptDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(promptDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".pr.md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String removePromptSuffix(String fileName) {
        return fileName.endsWith(".pr.md")
                ? fileName.substring(0, fileName.length() - ".pr.md".length())
                : fileName;
    }

    private static List<Path> defaultPromptRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ThreadSafePromptManager.class.getClassLoader();
        }
        try {
            Enumeration<URL> urls = classLoader.getResources(RESOURCE_ROOT);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if ("file".equals(url.getProtocol())) {
                    roots.add(Path.of(url.toURI()));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid prompt resource URL", exception);
        }
        roots.add(Path.of("src", "main", "resources").resolve(RESOURCE_ROOT));
        return List.copyOf(roots);
    }
}
