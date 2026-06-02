/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.prompts.sections.CodingMemorySection;
import com.openjiuwen.harness.prompts.sections.SectionName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rail that provides coding memory context for programming agents.
 * <p>
 * Mirrors Python's {@code CodingMemoryRail} in
 * {@code openjiuwen.harness.rails.memory.coding_memory_rail}.
 *
 * <p>Python features:
 * <ul>
 *   <li>Auto-recall: prefetch task on each user turn</li>
 *   <li>Mutual injection: inject top5 full text if recall has results</li>
 *   <li>Data isolation: coding_memory/ directory separate from personal memory</li>
 *   <li>Tool registration: coding_memory_read/write/edit</li>
 * </ul>
 */
public class CodingMemoryRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(CodingMemoryRail.class);

    // Recall limits
    public static final int MAX_RECALL_RESULTS = 5;
    public static final int MAX_RECALL_TOTAL_BYTES = 10240;

    private String codingMemoryDir;
    private Object embeddingConfig;
    private String language = "cn";
    private boolean managerInitialized = false;
    private Object memoryManager;
    private Object systemPromptBuilder;
    private String recalledContent = null;
    private int totalMemories = 0;
    private final Set<String> ownedToolNames = ConcurrentHashMap.newKeySet();
    private final Set<String> ownedToolIds = ConcurrentHashMap.newKeySet();

    public CodingMemoryRail() {
        super();
    }

    public CodingMemoryRail(String codingMemoryDir, Object embeddingConfig, String language) {
        super();
        this.codingMemoryDir = codingMemoryDir;
        this.embeddingConfig = embeddingConfig;
        this.language = language;
    }

    /**
     * Result returned by auto recall.
     */
    public static final class RecallResult {
        private final String content;
        private final int total;

        public RecallResult(String content, int total) {
            this.content = content;
            this.total = total;
        }

        public String getContent() {
            return content;
        }

        public int getTotal() {
            return total;
        }
    }

    /**
     * Initialize with agent - register coding memory tools and setup manager.
     * <p>
     * Mirrors Python's {@code init} method which:
     * <ul>
     *   <li>Gets system_prompt_builder from agent</li>
     *   <li>Registers coding memory tools (read/write/edit)</li>
     *   <li>Initializes MemoryIndexManager</li>
     * </ul>
     */
    @Override
    public void init(Object agent) {
        super.init(agent);

        // Get system_prompt_builder from agent
        systemPromptBuilder = invokeAccessible(agent, "getSystemPromptBuilder");
        if (systemPromptBuilder == null) {
            systemPromptBuilder = readFieldAccessible(agent, "systemPromptBuilder");
        }

        // Register coding memory tools
        registerCodingMemoryTools(agent);

        // Initialize memory manager
        try {
            managerInitialized = true;
            LOG.info("[CodingMemoryRail] Memory manager initialized");
        } catch (Exception e) {
            LOG.warn("[CodingMemoryRail] Failed to initialize memory manager: {}", e.getMessage());
        }

        LOG.info("[CodingMemoryRail] Initialized with coding_memory_dir={}", codingMemoryDir);
    }

    /**
     * Uninitialize with agent - remove tools and cleanup manager.
     * <p>
     * Mirrors Python's {@code uninit} method which:
     * <ul>
     *   <li>Removes tools from agent.ability_manager</li>
     *   <li>Removes tools from Runner.resource_mgr</li>
     *   <li>Removes prompt sections</li>
     *   <li>Shutdowns memory manager</li>
     * </ul>
     */
    @Override
    public void uninit(Object agent) {
        // Remove tools from agent.ability_manager
        if (agent != null) {
            try {
                Object abilityManager = getAbilityManager(agent);
                if (abilityManager != null) {
                    for (String toolName : new ArrayList<>(ownedToolNames)) {
                        try {
                            tryInvoke(abilityManager, "remove", toolName);
                        } catch (Exception exc) {
                            LOG.warn("[CodingMemoryRail] remove tool '{}' failed: {}", toolName, exc.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.debug("[CodingMemoryRail] Could not get ability_manager from agent");
            }
        }

        // Remove tools from resource_mgr
        for (String toolId : new ArrayList<>(ownedToolIds)) {
            try {
                Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
            } catch (Exception exc) {
                LOG.warn("[CodingMemoryRail] Failed to remove tool '{}' from resource_mgr: {}", toolId, exc.getMessage());
            }
        }

        ownedToolIds.clear();
        ownedToolNames.clear();

        // Remove prompt sections
        if (systemPromptBuilder != null) {
            try {
                // Python: self.system_prompt_builder.remove_section(...)
                tryInvoke(systemPromptBuilder, "removeSection", SectionName.MEMORY);
                LOG.debug("[CodingMemoryRail] Removed prompt sections");
            } catch (Exception e) {
                LOG.debug("[CodingMemoryRail] Failed to remove prompt sections");
            }
            systemPromptBuilder = null;
        }

        // Shutdown memory manager
        if (memoryManager != null) {
            try {
                tryInvoke(memoryManager, "shutdown");
                LOG.info("[CodingMemoryRail] Memory manager shutdown completed");
            } catch (Exception e) {
                LOG.warn("[CodingMemoryRail] Memory manager shutdown failed: {}", e.getMessage());
            }
        }

        managerInitialized = false;
        LOG.info("[CodingMemoryRail] Uninitialized");
    }

    /**
     * Register coding memory tools to agent.
     * <p>
     * Mirrors Python's tool registration logic.
     */
    private void registerCodingMemoryTools(Object agent) {
        if (agent == null) {
            return;
        }

        try {
            Object abilityManager = getAbilityManager(agent);
            if (abilityManager == null) {
                LOG.warn("[CodingMemoryRail] Agent has no ability_manager, cannot register tools");
                return;
            }

            List<String> toolNames = Arrays.asList("coding_memory_read", "coding_memory_write", "coding_memory_edit");
            for (String toolName : toolNames) {
                ToolCard card = ToolCard.builder()
                        .id(toolName)
                        .name(toolName)
                        .description("Coding memory tool: " + toolName)
                        .build();
                tryInvoke(abilityManager, "add", card);
                ownedToolNames.add(toolName);
            }

            LOG.info("[CodingMemoryRail] Registered {} coding memory tools", ownedToolNames.size());

        } catch (Exception e) {
            LOG.warn("[CodingMemoryRail] Failed to register coding memory tools: {}", e.getMessage());
        }
    }

    /**
     * Prefetch memory for a query (non-blocking).
     * <p>
     * Mirrors Python's prefetch functionality.
     */
    public void prefetch(String query) {
        if (!managerInitialized) {
            return;
        }

        RecallResult result = autoRecall(query);
        recalledContent = result.getContent();
        totalMemories = result.getTotal();
        LOG.debug("[CodingMemoryRail] Prefetch initiated for query: {}", query);
    }

    /**
     * Recall top matching coding memory files for the user query.
     *
     * <p>Uses the attached manager's {@code search(String, Map)} method when present
     * and reads matching files from {@code codingMemoryDir}. MEMORY.md is skipped.</p>
     */
    @SuppressWarnings("unchecked")
    public RecallResult autoRecall(String query) {
        if (memoryManager == null || codingMemoryDir == null || codingMemoryDir.isBlank()) {
            return new RecallResult(null, 0);
        }
        List<Map<String, Object>> results = List.of();
        try {
            Object raw = invokeAccessible(memoryManager, "search", query, Map.of("max_results", MAX_RECALL_RESULTS));
            if (raw instanceof List<?> list) {
                results = (List<Map<String, Object>>) list;
            }
        } catch (Exception e) {
            return new RecallResult(null, 0);
        }
        if (results.isEmpty()) {
            Object raw = invokeAccessible(memoryManager, "search", query);
            if (raw instanceof List<?> list) {
                results = (List<Map<String, Object>>) list;
            }
        }

        StringBuilder recalled = new StringBuilder();
        int bytes = 0;
        int total = 0;
        for (Map<String, Object> result : results) {
            Object pathObj = result.get("path");
            if (pathObj == null) {
                continue;
            }
            String filename = Path.of(pathObj.toString()).getFileName().toString();
            if ("MEMORY.md".equalsIgnoreCase(filename)) {
                continue;
            }
            Path memoryFile = Path.of(codingMemoryDir, filename);
            if (!Files.exists(memoryFile)) {
                continue;
            }
            try {
                String fileContent = Files.readString(memoryFile);
                String entry = formatRecallEntry(filename, fileContent);
                int entryBytes = entry.getBytes(StandardCharsets.UTF_8).length;
                if (bytes + entryBytes > MAX_RECALL_TOTAL_BYTES) {
                    break;
                }
                if (!recalled.isEmpty()) {
                    recalled.append("\n\n");
                }
                recalled.append(entry);
                bytes += entryBytes;
                total++;
            } catch (Exception e) {
                LOG.debug("[CodingMemoryRail] Failed to read recalled file {}: {}", filename, e.getMessage());
            }
        }
        return new RecallResult(recalled.isEmpty() ? null : recalled.toString(), total);
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (systemPromptBuilder == null) {
            return;
        }
        boolean readOnly = isReadOnlyInvocation(ctx);
        PromptSection section;
        if (readOnly) {
            section = CodingMemorySection.build(language, true, codingMemoryDir);
        } else if (recalledContent != null && !recalledContent.isBlank()) {
            String heading = "cn".equals(language) ? "# 已加载的相关记忆\n\n" : "# Loaded relevant memories\n\n";
            section = new PromptSection(SectionName.MEMORY, Map.of(language, heading + recalledContent), 85);
        } else {
            String indexContent = readMemoryIndex();
            String heading = "cn".equals(language) ? "# 当前记忆索引\n\n" : "# Current memory index\n\n";
            section = new PromptSection(SectionName.MEMORY, Map.of(language, heading + indexContent), 85);
        }
        tryInvoke(systemPromptBuilder, "removeSection", SectionName.MEMORY);
        tryInvoke(systemPromptBuilder, "addSection", section);
    }

    /**
     * Get recalled content.
     */
    public String getRecalledContent() {
        return recalledContent;
    }

    public void setRecalledContent(String recalledContent) {
        this.recalledContent = recalledContent;
    }

    /**
     * Get total memory count.
     */
    public int getTotalMemories() {
        return totalMemories;
    }

    /**
     * Check if manager is initialized.
     */
    public boolean isManagerInitialized() {
        return managerInitialized;
    }

    public String getCodingMemoryDir() {
        return codingMemoryDir;
    }

    public String getLanguage() {
        return language;
    }

    public Set<String> getOwnedToolNames() {
        return Collections.unmodifiableSet(ownedToolNames);
    }

    public void setMemoryManager(Object memoryManager) {
        this.memoryManager = memoryManager;
    }

    /**
     * Set the coding memory directory.
     */
    public void setCodingMemoryDir(String codingMemoryDir) {
        this.codingMemoryDir = codingMemoryDir;
    }

    /**
     * Set the embedding config.
     */
    public void setEmbeddingConfig(Object embeddingConfig) {
        this.embeddingConfig = embeddingConfig;
    }

    /**
     * Set the language.
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    private static String formatRecallEntry(String filename, String fileContent) {
        Map<String, String> frontmatter = Frontmatter.parseFrontmatter(fileContent);
        String title = frontmatter != null ? frontmatter.getOrDefault("name", filename) : filename;
        String body = Frontmatter.extractBody(fileContent);
        return "### " + title + " [" + filename + "]\n\n" + body;
    }

    private String readMemoryIndex() {
        if (codingMemoryDir == null || codingMemoryDir.isBlank()) {
            return "";
        }
        Path index = Path.of(codingMemoryDir, "MEMORY.md");
        try {
            return Files.exists(index) ? Files.readString(index) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isReadOnlyInvocation(AgentCallbackContext ctx) {
        if (ctx == null) {
            return false;
        }
        Object inputs = ctx.getInputs();
        return Boolean.TRUE.equals(ctx.getExtra().get("isCron"))
                || Boolean.TRUE.equals(ctx.getExtra().get("isHeartbeat"))
                || callBoolean(inputs, "isCron")
                || callBoolean(inputs, "isHeartbeat");
    }

    private static boolean callBoolean(Object target, String methodName) {
        if (target == null) {
            return false;
        }
        try {
            Object value = invokeAccessible(target, methodName);
            return Boolean.TRUE.equals(value);
        } catch (Exception e) {
            return false;
        }
    }

    private static void tryInvoke(Object target, String methodName, Object... args) {
        invokeAccessible(target, methodName, args);
    }

    private static Object getAbilityManager(Object agent) {
        Object abilityManager = invokeAccessible(agent, "getAbilityManager");
        return abilityManager != null ? abilityManager : readFieldAccessible(agent, "abilityManager");
    }

    private static Object invokeAccessible(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        for (java.lang.reflect.Method method : methodsFor(target.getClass())) {
            if (!isCompatible(method, methodName, args)) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object readFieldAccessible(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static List<java.lang.reflect.Method> methodsFor(Class<?> type) {
        List<java.lang.reflect.Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
        Class<?> current = type;
        while (current != null) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        return methods;
    }

    private static boolean isCompatible(java.lang.reflect.Method method, String methodName, Object[] args) {
        if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg != null && !wrap(parameterTypes[i]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }
}
