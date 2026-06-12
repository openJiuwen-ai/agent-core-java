/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.team_workspace;

import com.openjiuwen.agent_teams.rails.TeamToolRail;
import com.openjiuwen.agent_teams.tools.locales.CnLocaleStrings;
import com.openjiuwen.agent_teams.tools.locales.EnLocaleStrings;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Tool for team shared workspace lock metadata and history.
 *
 * <p>Mirrors Python's {@code WorkspaceMetaTool} in
 * {@code openjiuwen/agent_teams/team_workspace/tools.py}.</p>
 */
public final class WorkspaceMetaTool implements TeamToolRail.ToolView {

    private static final String TOOL_ID = "team.workspace_meta";
    private static final String TOOL_NAME = "workspace_meta";
    private static final String ACTION_LOCK = "lock";
    private static final String ACTION_UNLOCK = "unlock";
    private static final String ACTION_LOCKS = "locks";
    private static final String ACTION_HISTORY = "history";
    private static final String DEFAULT_DESCRIPTION = """
            Metadata tool for the team shared workspace: file lock management and git version history queries. \
            Use standard file tools against .team/... paths for file I/O; this tool only handles locks and history.
            """;

    private final TeamWorkspaceManager workspace;
    private final WorkspaceMetaToolCard card;

    public WorkspaceMetaTool(TeamWorkspaceManager workspace, String language) {
        this(workspace, WorkspaceMetaTranslator.forLanguage(language));
    }

    public WorkspaceMetaTool(TeamWorkspaceManager workspace, WorkspaceMetaTranslator translator) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        WorkspaceMetaTranslator effectiveTranslator = translator == null
                ? WorkspaceMetaTranslator.forLanguage("cn")
                : translator;
        this.card = new WorkspaceMetaToolCard(
                TOOL_ID,
                TOOL_NAME,
                effectiveTranslator.translate(TOOL_NAME, "_desc"),
                buildInputParams(effectiveTranslator));
    }

    @Override
    public WorkspaceMetaToolCard getCard() {
        return card;
    }

    public CompletionStage<ToolOutput> invoke(Map<String, ?> inputs) {
        return invoke(inputs, Map.of());
    }

    public CompletionStage<ToolOutput> invoke(Map<String, ?> inputs, Map<String, ?> kwargs) {
        Map<String, ?> effectiveInputs = inputs == null ? Map.of() : inputs;
        Map<String, ?> effectiveKwargs = kwargs == null ? Map.of() : kwargs;
        String action = stringOrNull(effectiveInputs.get("action"));
        String path = stringOrDefault(effectiveInputs.get("path"), "");
        String memberName = keywordString(effectiveKwargs, "member_name", "unknown");
        String displayName = keywordString(effectiveKwargs, "display_name", memberName);

        if (ACTION_LOCK.equals(action)) {
            if (path.isEmpty()) {
                return completedFailure("'path' is required for lock action");
            }
            return workspace.acquireLock(path, memberName, displayName)
                    .thenApply(acquired -> acquired ? ToolOutput.success(Map.of("locked", path)) : lockFailure(path));
        }

        if (ACTION_UNLOCK.equals(action)) {
            if (path.isEmpty()) {
                return completedFailure("'path' is required for unlock action");
            }
            return workspace.releaseLock(path, memberName)
                    .thenApply(released -> ToolOutput.success(Map.of("released", released)));
        }

        if (ACTION_LOCKS.equals(action)) {
            return workspace.listLocks()
                    .thenApply(locks -> ToolOutput.success(Map.of("locks", dumpLocks(locks))));
        }

        if (ACTION_HISTORY.equals(action)) {
            if (path.isEmpty()) {
                return completedFailure("'path' is required for history action");
            }
            return workspace.getHistory(path)
                    .thenApply(history -> ToolOutput.success(Map.of("history", history)));
        }

        return completedFailure("Unknown action '" + pythonString(action) + "'");
    }

    private ToolOutput lockFailure(String path) {
        WorkspaceFileLock lock = workspace.getLock(path);
        if (lock == null) {
            return ToolOutput.failure("Lock failed");
        }
        return ToolOutput.failure("Locked by " + lock.getHolderName());
    }

    private static CompletionStage<ToolOutput> completedFailure(String error) {
        return CompletableFuture.completedFuture(ToolOutput.failure(error));
    }

    private static List<Map<String, Object>> dumpLocks(List<WorkspaceFileLock> locks) {
        if (locks == null || locks.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> dumped = new ArrayList<>(locks.size());
        for (WorkspaceFileLock lock : locks) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("file_path", lock.getFilePath());
            item.put("holder_id", lock.getHolderId());
            item.put("holder_name", lock.getHolderName());
            item.put("acquired_at", lock.getAcquiredAt());
            item.put("timeout_seconds", lock.getTimeoutSeconds());
            dumped.add(Collections.unmodifiableMap(item));
        }
        return List.copyOf(dumped);
    }

    private static Map<String, Object> buildInputParams(WorkspaceMetaTranslator translator) {
        Map<String, Object> actionProperty = new LinkedHashMap<>();
        actionProperty.put("type", "string");
        actionProperty.put("enum", List.of(ACTION_LOCK, ACTION_UNLOCK, ACTION_LOCKS, ACTION_HISTORY));
        actionProperty.put("description", translator.translate(TOOL_NAME, "action"));

        Map<String, Object> pathProperty = new LinkedHashMap<>();
        pathProperty.put("type", "string");
        pathProperty.put("description", translator.translate(TOOL_NAME, "path"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Collections.unmodifiableMap(actionProperty));
        properties.put("path", Collections.unmodifiableMap(pathProperty));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", Collections.unmodifiableMap(properties));
        params.put("required", List.of("action"));
        return Collections.unmodifiableMap(params);
    }

    private static String keywordString(Map<String, ?> values, String key, String fallback) {
        if (!values.containsKey(key)) {
            return fallback;
        }
        return stringOrNull(values.get(key));
    }

    private static String stringOrDefault(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String pythonString(String value) {
        return value == null ? "None" : value;
    }

    /**
     * Language-bound text resolver used by the workspace metadata tool.
     *
     * <p>Mirrors Python's {@code Translator} callable in
     * {@code openjiuwen/agent_teams/team_workspace/tools.py}.</p>
     */
    @FunctionalInterface
    public interface WorkspaceMetaTranslator {
        String translate(String tool, String key);

        static WorkspaceMetaTranslator forLanguage(String language) {
            return (tool, key) -> {
                if ("_desc".equals(key)) {
                    return DEFAULT_DESCRIPTION.strip();
                }
                String lookupKey = tool + "." + key;
                String value = "en".equals(language)
                        ? EnLocaleStrings.get(lookupKey)
                        : CnLocaleStrings.get(lookupKey);
                return value == null ? lookupKey : value;
            };
        }
    }

    /**
     * Mutable card metadata and JSON input schema for {@link WorkspaceMetaTool}.
     *
     * <p>Mirrors Python's {@code ToolCard} instance in
     * {@code openjiuwen/agent_teams/team_workspace/tools.py}.</p>
     */
    public static final class WorkspaceMetaToolCard implements TeamToolRail.ToolCardView {
        private String id;
        private final String name;
        private final String description;
        private final Map<String, Object> inputParams;

        private WorkspaceMetaToolCard(
                String id,
                String name,
                String description,
                Map<String, Object> inputParams
        ) {
            this.id = id;
            this.name = name;
            this.description = description == null ? "" : description;
            this.inputParams = inputParams == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputParams));
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void setId(String id) {
            this.id = id;
        }

        @Override
        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, Object> getInputParams() {
            return inputParams;
        }
    }
}
