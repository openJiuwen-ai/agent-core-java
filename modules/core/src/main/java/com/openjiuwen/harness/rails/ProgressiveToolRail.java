/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.LoadToolsTool;
import com.openjiuwen.harness.tools.SearchToolsTool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public class ProgressiveToolRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ProgressiveToolRail extends DeepAgentRail {
    /**
     * VISIBLE_TOOLS_KEY.
     * 
     * @since 0.1.7
     */
    public static final String VISIBLE_TOOLS_KEY = "__progressive_visible_tool_names__";

    /**
     * DISCOVERY_TRACE_KEY.
     * 
     * @since 0.1.7
     */
    public static final String DISCOVERY_TRACE_KEY = "__progressive_tool_discovery_trace__";

    /**
     * TOOL_NAVIGATION_SECTION.
     * 
     * @since 0.1.7
     */
    public static final String TOOL_NAVIGATION_SECTION = "tool_navigation";

    /**
     * PROGRESSIVE_TOOL_RULES_SECTION.
     * 
     * @since 0.1.7
     */
    public static final String PROGRESSIVE_TOOL_RULES_SECTION = "progressive_tool_rules";

    /**
     * TOOL_NAVIGATION_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int TOOL_NAVIGATION_PRIORITY = 70;

    /**
     * PROGRESSIVE_TOOL_RULES_PRIORITY.
     * 
     * @since 0.1.7
     */
    public static final int PROGRESSIVE_TOOL_RULES_PRIORITY = 75;

    private final Set<String> defaultVisibleTools;
    private final Set<String> alwaysVisibleTools;
    private final int maxLoadedTools;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Tool> metaTools = new ArrayList<>();

    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private final Set<String> metaToolNames = new LinkedHashSet<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<ToolInfo> cachedAllToolInfos = new ArrayList<>();
    private DeepAgent owner;

    /**
     * ProgressiveToolRail.
     * 
     * @since 0.1.7
     */
    public ProgressiveToolRail() {
        this(List.of(), List.of(), 20);
    }

    /**
     * ProgressiveToolRail.
     * 
     * @param defaultVisibleTools defaultVisibleTools
     * @param alwaysVisibleTools alwaysVisibleTools
     * @param maxLoadedTools maxLoadedTools
     * @since 0.1.7
     */
    public ProgressiveToolRail(List<String> defaultVisibleTools, List<String> alwaysVisibleTools, int maxLoadedTools) {
        this.defaultVisibleTools = new LinkedHashSet<>(defaultVisibleTools == null ? List.of() : defaultVisibleTools);
        this.alwaysVisibleTools = new LinkedHashSet<>(alwaysVisibleTools == null ? List.of() : alwaysVisibleTools);
        this.maxLoadedTools = maxLoadedTools;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 90;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        owner = deepAgent;
        if (!metaTools.isEmpty()) {
            return;
        }
        String language = deepAgent.getWorkspace().getLanguage();
        SearchToolsTool searchToolsTool = new SearchToolsTool(this::searchTools);
        LoadToolsTool loadToolsTool = new LoadToolsTool((toolNames, isReplace) -> loadTools(
                com.openjiuwen.core.session.SessionContextHolder.getCurrentSession(), toolNames, isReplace));
        metaTools.add(new LocalFunction(card("search_tools", deepAgent, language),
                inputs -> searchToolsTool.invoke(stringValue(inputs.get("query")), integerValue(inputs.get("limit")),
                        integerValue(inputs.get("detail_level")))));
        metaTools.add(new LocalFunction(card("load_tools", deepAgent, language), (inputs, kwargs) -> loadToolsTool
                .invoke(stringList(inputs.get("tool_names")), Boolean.TRUE.equals(inputs.get("isReplace")))));
        for (Tool tool : metaTools) {
            metaToolNames.add(tool.getCard().getName());
            deepAgent.registerHarnessTool(tool);
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : metaTools) {
                deepAgent.unregisterHarnessTool(tool);
            }
            deepAgent.getAgent().getPromptBuilder().removeSection(TOOL_NAVIGATION_SECTION);
            deepAgent.getAgent().getPromptBuilder().removeSection(PROGRESSIVE_TOOL_RULES_SECTION);
        }
        metaTools.clear();
        metaToolNames.clear();
        cachedAllToolInfos = new ArrayList<>();
        owner = null;
    }

    /**
     * beforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        cachedAllToolInfos =
            owner != null ? new ArrayList<>(owner.getAgent().getAbilityManager().listToolInfo()) : new ArrayList<>();
        initVisibleTools(ctx.getSession());
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        if (owner == null) {
            return;
        }
        if (cachedAllToolInfos.isEmpty()) {
            cachedAllToolInfos = new ArrayList<>(owner.getAgent().getAbilityManager().listToolInfo());
        }
        String navigation = buildNavigationPrompt(ctx.getSession());
        String rules = progressiveToolRulesPrompt();
        owner.getAgent().addPromptBuilderSection(TOOL_NAVIGATION_SECTION, navigation, TOOL_NAVIGATION_PRIORITY);
        owner.getAgent().addPromptBuilderSection(PROGRESSIVE_TOOL_RULES_SECTION, rules,
                PROGRESSIVE_TOOL_RULES_PRIORITY);
        if (ctx.getInputs() instanceof ModelCallInputs inputs) {
            injectProgressiveToolMessages(inputs, navigation, rules);
            filterCallableTools(inputs, ctx.getSession());
        }
    }

    /**
     * getDefaultVisibleTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getDefaultVisibleTools() {
        return Set.copyOf(defaultVisibleTools);
    }

    /**
     * getAlwaysVisibleTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getAlwaysVisibleTools() {
        return Set.copyOf(alwaysVisibleTools);
    }

    /**
     * getMaxLoadedTools.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxLoadedTools() {
        return maxLoadedTools;
    }

    /**
     * metaToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> metaToolNames() {
        return List.of("search_tools", "load_tools");
    }

    /**
     * getMetaToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> getMetaToolNames() {
        return Set.copyOf(metaToolNames);
    }

    /**
     * getVisibleTools.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    public List<String> getVisibleTools(Session session) {
        return readVisibleTools(session);
    }

    /**
     * searchTools.
     * 
     * @param query query
     * @param limit limit
     * @param detailLevel detailLevel
     * @return the result
     * @since 0.1.7
     */
    public List<Map<String, Object>> searchTools(String query, int limit, int detailLevel) {
        String normalizedQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Integer, ToolInfo>> scored = new ArrayList<>();
        for (ToolInfo tool : realToolInfos()) {
            String name = stringValue(tool.getName());
            String description = stringValue(tool.getDescription());
            String haystack =
                (name + " " + description + " " + parametersToText(tool.getParameters())).toLowerCase(Locale.ROOT);
            int score = 0;
            if (normalizedQuery.equals(name.toLowerCase(Locale.ROOT))) {
                score += 100;
            }
            if (name.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                score += 40;
            }
            if (description.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                score += 25;
            }
            if (haystack.contains(normalizedQuery)) {
                score += 10;
            }
            for (String token : normalizedQuery.split("\\s+")) {
                if (!token.isBlank() && haystack.contains(token)) {
                    score += 3;
                }
            }
            if (score > 0) {
                scored.add(Map.entry(score, tool));
            }
        }
        scored.sort(Comparator.<Map.Entry<Integer, ToolInfo>>comparingInt(Map.Entry::getKey).reversed()
                .thenComparing(entry -> stringValue(entry.getValue().getName())));
        return scored.stream().limit(Math.max(1, limit)).map(entry -> buildToolSummary(entry.getValue(), detailLevel))
                .toList();
    }

    /**
     * loadTools.
     * 
     * @param session session
     * @param toolNames toolNames
     * @param isReplace isReplace
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> loadTools(Session session, List<String> toolNames, boolean isReplace) {
        if (session == null) {
            return Map.of("loaded_tools", List.of(), "visible_tools", List.of(), "skipped_tools",
                    toolNames != null ? toolNames : List.of(), "message", "session is required for load_tools");
        }
        Set<String> available = new LinkedHashSet<>();
        for (ToolInfo tool : realToolInfos()) {
            if (tool.getName() != null && !tool.getName().isBlank()) {
                available.add(tool.getName());
            }
        }
        List<String> requested = normalizeNames(toolNames);
        List<String> valid = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String name : requested) {
            if (alwaysVisibleTools.contains(name) || available.contains(name)) {
                valid.add(name);
            } else {
                skipped.add(name);
            }
        }
        List<String> current = readVisibleTools(session);
        List<String> next = isReplace ? unique(valid) : unique(join(current, valid));
        if (next.size() > maxLoadedTools) {
            skipped.addAll(next.subList(maxLoadedTools, next.size()));
            next = new ArrayList<>(next.subList(0, maxLoadedTools));
        }
        writeVisibleTools(session, next);
        appendTrace(session, Map.of("action", "load_tools", "requested", requested, "loaded", valid, "visible_before",
                current, "visible_after", next, "skipped", skipped, "isReplace", isReplace));
        return Map.of("loaded_tools", valid, "visible_tools", next, "skipped_tools", skipped, "message", "loaded "
                + valid.size() + " tool(s), visible now: " + (next.isEmpty() ? "(none)" : String.join(", ", next)));
    }

    /**
     * hasProgressivePromptSections.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasProgressivePromptSections() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(TOOL_NAVIGATION_SECTION)
                && owner.getAgent().getPromptBuilder().hasSection(PROGRESSIVE_TOOL_RULES_SECTION);
    }

    /**
     * initVisibleTools.
     * 
     * @param session session
     * @since 0.1.7
     */
    private void initVisibleTools(Session session) {
        if (session == null || session.getState(VISIBLE_TOOLS_KEY) instanceof List<?>) {
            return;
        }
        List<String> initial = unique(join(new ArrayList<>(alwaysVisibleTools), new ArrayList<>(defaultVisibleTools)));
        session.updateState(Map.of(VISIBLE_TOOLS_KEY, initial, DISCOVERY_TRACE_KEY, List.of()));
    }

    /**
     * readVisibleTools.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<String> readVisibleTools(Session session) {
        if (session == null) {
            return List.of();
        }
        Object state = session.getState(VISIBLE_TOOLS_KEY);
        if (state instanceof List<?> list) {
            return normalizeNames(list);
        }
        return List.of();
    }

    /**
     * writeVisibleTools.
     * 
     * @param session session
     * @param names names
     * @since 0.1.7
     */
    private void writeVisibleTools(Session session, List<String> names) {
        if (session != null) {
            session.updateState(Map.of(VISIBLE_TOOLS_KEY, unique(names)));
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * appendTrace.
     * 
     * @param session session
     * @param event event
     * @since 0.1.7
     */
    private void appendTrace(Session session, Map<String, Object> event) {
        if (session == null) {
            return;
        }
        List<Object> trace = new ArrayList<>();
        Object existing = session.getState(DISCOVERY_TRACE_KEY);
        if (existing instanceof List<?> list) {
            trace.addAll(list);
        }
        trace.add(event);
        session.updateState(Map.of(DISCOVERY_TRACE_KEY, trace));
    }

    /**
     * filterCallableTools.
     * 
     * @param inputs inputs
     * @param session session
     * @since 0.1.7
     */
    private void filterCallableTools(ModelCallInputs inputs, Session session) {
        if (inputs.getTools() == null || inputs.getTools().isEmpty()) {
            return;
        }
        Set<String> callable = new LinkedHashSet<>();
        callable.addAll(metaToolNames);
        callable.addAll(alwaysVisibleTools);
        callable.addAll(readVisibleTools(session));
        List<ToolInfo> filtered = new ArrayList<>();
        for (ToolInfo tool : inputs.getTools()) {
            if (tool != null && callable.contains(tool.getName())) {
                filtered.add(tool);
            }
        }
        inputs.setTools(filtered);
    }

    /**
     * buildNavigationPrompt.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private String buildNavigationPrompt(Session session) {
        List<String> entries = buildNavigationEntries(session);
        return "## Tool Navigation\n"
                + "The entries below help you understand the tool ecosystem available in the current session.\n"
                + "Treat this section as a tool map, not as a full list of immediately callable tools.\n"
                + "A tool becomes callable only after `load_tools` has been explicitly called for it in the "
                + "current session.\n\n"
                + (entries.isEmpty() ? "- (no navigation entries available)" : String.join("\n", entries));
    }

    /**
     * buildNavigationEntries.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<String> buildNavigationEntries(Session session) {
        Set<String> loaded = new LinkedHashSet<>(readVisibleTools(session));
        Set<String> baseline = new LinkedHashSet<>(alwaysVisibleTools);
        baseline.addAll(defaultVisibleTools);
        Set<String> seen = new LinkedHashSet<>();
        List<String> entries = new ArrayList<>();
        realToolInfos().stream()
                .sorted(Comparator.comparingInt(this::toolGroupRank).thenComparing(tool -> stringValue(tool.getName())))
                .forEach(tool -> {
                    String name = stringValue(tool.getName());
                    if (name.isBlank() || seen.contains(name) || !isNavigationTool(name, baseline, loaded)) {
                        return;
                    }
                    seen.add(name);
                    String status =
                        loaded.contains(name) || alwaysVisibleTools.contains(name) ? "callable" : "navigation-only";
                    entries.add("- " + name + " [" + toolGroup(tool) + ", " + status + "]: " + navigationSummary(tool));
                });
        return entries;
    }

    /**
     * isNavigationTool.
     * 
     * @param name name
     * @param baseline baseline
     * @param loaded loaded
     * @return the result
     * @since 0.1.7
     */
    private boolean isNavigationTool(String name, Set<String> baseline, Set<String> loaded) {
        return baseline.contains(name) || loaded.contains(name)
                || Set.of("code", "read_file", "bash", "list_skill", "pdf", "xlsx").contains(name);
    }

    /**
     * realToolInfos.
     * 
     * @return the result
     * @since 0.1.7
     */
    private List<ToolInfo> realToolInfos() {
        return cachedAllToolInfos.stream().filter(tool -> tool != null && !metaToolNames.contains(tool.getName()))
                .toList();
    }

    /**
     * buildToolSummary.
     * 
     * @param tool tool
     * @param detailLevel detailLevel
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildToolSummary(ToolInfo tool, int detailLevel) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("name", stringValue(tool.getName()));
        result.put("description", stringValue(tool.getDescription()));
        if (detailLevel >= 2) {
            result.put("parameter_summary", parametersSummary(tool.getParameters()));
        }
        if (detailLevel >= 3) {
            result.put("parameters", tool.getParameters() != null ? tool.getParameters() : Map.of());
        }
        return result;
    }

    /**
     * progressiveToolRulesPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    private String progressiveToolRulesPrompt() {
        return "## Progressive Tool Usage Rules\n" + "You are operating in a progressive tool environment.\n"
                + "1. If you are unsure which tool to use, call `search_tools` first.\n"
                + "2. Seeing a tool in navigation or search results does NOT make it callable.\n"
                + "3. A real tool becomes callable only after `load_tools` has been explicitly called for it "
                + "in the current session.\n"
                + "4. Once `search_tools` has identified the tools you want, call `load_tools` immediately, "
                + "then use the real tools.";
    }

    /**
     * injectProgressiveToolMessages.
     * 
     * @param inputs inputs
     * @param navigation navigation
     * @param rules rules
     * @since 0.1.7
     */
    private void injectProgressiveToolMessages(ModelCallInputs inputs, String navigation, String rules) {
        List<Object> messages =
            inputs.getMessages() != null ? new ArrayList<>(inputs.getMessages()) : new ArrayList<>();
        injectSystemMessage(messages, "## Tool Navigation", navigation);
        injectSystemMessage(messages, "## Progressive Tool Usage Rules", rules);
        inputs.setMessages(messages);
    }

    /**
     * injectSystemMessage.
     * 
     * @param messages messages
     * @param marker marker
     * @param content content
     * @since 0.1.7
     */
    private void injectSystemMessage(List<Object> messages, String marker, String content) {
        for (Object message : messages) {
            if (message instanceof BaseMessage baseMessage && "system".equalsIgnoreCase(baseMessage.getRole())
                    && String.valueOf(baseMessage.getContent()).contains(marker)) {
                return;
            }
        }
        messages.add(0, new SystemMessage(content));
    }

    /**
     * card.
     * 
     * @param name name
     * @param agent agent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    /**
     * navigationSummary.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private static String navigationSummary(ToolInfo tool) {
        String description = stringValue(tool.getDescription()).trim();
        if (description.isEmpty()) {
            return "No summary available.";
        }
        String firstLine = description.split("\\R", 2)[0].trim();
        return firstLine.length() > 160 ? firstLine.substring(0, 160) : firstLine;
    }

    /**
     * toolGroupRank.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private int toolGroupRank(ToolInfo tool) {
        return switch (toolGroup(tool)) {
            case "skill" -> 0;
            case "runtime" -> 1;
            case "document" -> 2;
            case "spreadsheet" -> 3;
            default -> 9;
        };
    }

    /**
     * toolGroup.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private static String toolGroup(ToolInfo tool) {
        String name = stringValue(tool.getName()).toLowerCase(Locale.ROOT);
        String description = stringValue(tool.getDescription()).toLowerCase(Locale.ROOT);
        if (containsAny(name, "read", "write", "edit", "file", "bash", "code")) {
            return "runtime";
        }
        if (containsAny(name, "pdf", "invoice", "document") || containsAny(description, "pdf", "invoice", "document")) {
            return "document";
        }
        if (containsAny(name, "xlsx", "excel", "sheet", "spreadsheet")
                || containsAny(description, "xlsx", "excel", "spreadsheet")) {
            return "spreadsheet";
        }
        if (name.contains("skill")) {
            return "skill";
        }
        return "general";
    }

    /**
     * containsAny.
     * 
     * @param value value
     * @param tokens tokens
     * @return the result
     * @since 0.1.7
     */
    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * parametersSummary.
     * 
     * @param parameters parameters
     * @return the result
     * @since 0.1.7
     */
    private static String parametersSummary(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "empty schema";
        }
        Object props = parameters.get("properties");
        if (props instanceof Map<?, ?> map && !map.isEmpty()) {
            return "fields: " + String.join(", ", map.keySet().stream().map(String::valueOf).toList());
        }
        return "schema keys: " + String.join(", ", parameters.keySet());
    }

    /**
     * parametersToText.
     * 
     * @param parameters parameters
     * @return the result
     * @since 0.1.7
     */
    private static String parametersToText(Map<String, Object> parameters) {
        return parametersSummary(parameters) + " " + (parameters != null ? parameters : Map.of());
    }

    /**
     * join.
     * 
     * @param first first
     * @param second second
     * @return the result
     * @since 0.1.7
     */
    private static List<String> join(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    /**
     * unique.
     * 
     * @param names names
     * @return the result
     * @since 0.1.7
     */
    private static List<String> unique(List<String> names) {
        return List.copyOf(new LinkedHashSet<>(normalizeNames(names)));
    }

    /**
     * normalizeNames.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<String> normalizeNames(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(ProgressiveToolRail::stringValue).map(String::trim).filter(item -> !item.isBlank())
                .distinct().toList();
    }

    /**
     * stringList.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(Object value) {
        return normalizeNames(value);
    }

    /**
     * integerValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
