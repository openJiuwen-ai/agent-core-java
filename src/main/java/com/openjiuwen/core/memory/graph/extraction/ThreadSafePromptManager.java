/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.runner.resourcemanager.PromptMgr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Thread-safe prompt manager for entity extraction prompts.
 * <p>
 * Mirrors Python's {@code ThreadSafePromptManager} class from
 * <code>memory/graph/extraction/prompts/manager.py</code>.
 *
 * <p>Manages prompt templates with thread-safe access for concurrent LLM calls.
 */
public class ThreadSafePromptManager {

    private static final Pattern ROLE_MARKER = Pattern.compile("`#(user|system|assistant|tool)#`");
    private static final Set<String> ROLES = Set.of("user", "system", "assistant", "tool");
    private static final ReentrantReadWriteLock LOCK = new ReentrantReadWriteLock();

    private final Set<String> allPromptNames = new HashSet<>();
    private final PromptMgr mgr = new PromptMgr();

    /**
     * Load prompt template messages from a {@code .pr.md} file body.
     * Mirrors Python's {@code load_pr_content}.
     */
    public static List<BaseMessage> loadPrContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] parts = ROLE_MARKER.split(content);
        java.util.regex.Matcher matcher = ROLE_MARKER.matcher(content);
        List<String> roles = new ArrayList<>();
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }

        List<BaseMessage> messages = new ArrayList<>();
        int partIndex = parts.length > 0 && parts[0].isEmpty() ? 1 : 0;
        for (String role : roles) {
            String body = partIndex < parts.length ? parts[partIndex] : "";
            partIndex++;
            if (ROLES.contains(role)) {
                messages.add(messageFor(role, body));
            }
        }
        return messages;
    }

    /**
     * Python-compatible snake_case alias.
     */
    public static List<BaseMessage> load_pr_content(String content) {
        return loadPrContent(content);
    }

    /**
     * Register prompt templates from every {@code *.pr.md} file in the directory.
     */
    public void registerInBulk(String promptDir, String name) {
        LOCK.writeLock().lock();
        try {
            Path dir = Path.of(promptDir).toAbsolutePath().normalize();
            List<Path> promptPaths;
            try (Stream<Path> paths = Files.list(dir)) {
                promptPaths = paths
                        .filter(path -> path.getFileName().toString().endsWith(".pr.md"))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                throw promptFilesMissing(promptDir);
            }

            if (promptPaths.isEmpty()) {
                throw promptFilesMissing(promptDir);
            }

            List<PromptMgr.PromptEntry> entries = new ArrayList<>();
            for (Path promptPath : promptPaths) {
                String fileName = promptPath.getFileName().toString();
                String templateName = fileName.substring(0, fileName.length() - ".pr.md".length());
                String content = Files.readString(promptPath, StandardCharsets.UTF_8);
                PromptTemplate template = PromptTemplate.builder()
                        .name(templateName)
                        .content(loadPrContent(content))
                        .build();
                allPromptNames.add(templateName);
                entries.add(new PromptMgr.PromptEntry(templateName, template));
            }
            mgr.addPrompts(entries);
        } catch (IOException e) {
            throw new BaseError(StatusCode.PROMPT_TEMPLATE_RUNTIME_ERROR,
                    "failed to read prompt file: " + e.getMessage(), null, e);
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    /**
     * Python-compatible snake_case alias.
     */
    public void register_in_bulk(String promptDir, String name) {
        registerInBulk(promptDir, name);
    }

    public PromptTemplate get(String name) {
        LOCK.readLock().lock();
        try {
            return mgr.getPrompt(name);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    /**
     * Check if a prompt is registered.
     */
    public boolean has(String key) {
        return contains(key);
    }

    /**
     * Mirrors Python's {@code __contains__}.
     */
    public boolean contains(String key) {
        LOCK.readLock().lock();
        try {
            return allPromptNames.contains(key);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public Set<String> keys() {
        LOCK.readLock().lock();
        try {
            return Set.copyOf(allPromptNames);
        } finally {
            LOCK.readLock().unlock();
        }
    }

    private static BaseMessage messageFor(String role, String content) {
        return switch (role) {
            case "system" -> SystemMessage.builder().content(content).build();
            case "assistant" -> AssistantMessage.builder().content(content).build();
            case "tool" -> ToolMessage.builder().content(content).build();
            default -> UserMessage.builder().content(content).build();
        };
    }

    private static BaseError promptFilesMissing(String promptDir) {
        return new BaseError(StatusCode.PROMPT_TEMPLATE_NOT_FOUND,
                "prompt files not found: " + promptDir, null, null);
    }
}
