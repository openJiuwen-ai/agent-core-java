/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentCustomizer;
import com.openjiuwen.agent_teams.agent.MemberRuntime;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.sys_operation.Cwd;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Sole adapter between TeamAgent and the underlying DeepAgent runtime.
 *
 * <p>Mirrors Python's {@code TeamHarness} in
 * {@code openjiuwen/agent_teams/harness.py}.</p>
 */
public class TeamHarness implements MemberRuntime {

    public static final String INTERRUPTION_KEY = "__react_agent_interruption__";

    private static final Object MISSING = new Object();

    private final Object deepAgent;
    private final MountedRails rails;
    private final TeamRole role;
    private final String memberName;
    private final boolean initialPlanMode;
    private final StreamingRunner streamingRunner;
    private boolean initialPlanModeSeeded;
    private Object activeAgentSession;

    public TeamHarness(Object deepAgent, MountedRails rails, TeamRole role, String memberName) {
        this(deepAgent, rails, role, memberName, false, StreamingRunner.empty());
    }

    public TeamHarness(
            Object deepAgent,
            MountedRails rails,
            TeamRole role,
            String memberName,
            boolean initialPlanMode
    ) {
        this(deepAgent, rails, role, memberName, initialPlanMode, StreamingRunner.empty());
    }

    public TeamHarness(
            Object deepAgent,
            MountedRails rails,
            TeamRole role,
            String memberName,
            boolean initialPlanMode,
            StreamingRunner streamingRunner
    ) {
        this.deepAgent = Objects.requireNonNull(deepAgent, "deepAgent");
        this.rails = rails == null ? new MountedRails(null, null) : rails;
        this.role = role;
        this.memberName = memberName;
        this.initialPlanMode = initialPlanMode;
        this.streamingRunner = streamingRunner == null ? StreamingRunner.empty() : streamingRunner;
    }

    public static TeamHarness build(
            Object agentSpec,
            TeamRole role,
            String memberName,
            Object teamToolRail,
            Object teamPolicyRail
    ) {
        return build(
                agentSpec,
                role,
                memberName,
                teamToolRail,
                teamPolicyRail,
                null,
                null,
                null,
                null,
                false
        );
    }

    public static TeamHarness build(
            Object agentSpec,
            TeamRole role,
            String memberName,
            Object teamToolRail,
            Object teamPolicyRail,
            Object firstIterGate,
            Object teamWorkspaceRail,
            Object toolApprovalRail,
            Object teamPlanModeRail,
            boolean initialPlanMode
    ) {
        Object deepAgent = invokeRequired(agentSpec, List.of("build"));

        invokeIfPresent(deepAgent, List.of("addRail", "add_rail"), teamToolRail);
        Object deepConfig = readProperty(deepAgent, "deepConfig", "deep_config");
        invokeIfPresent(teamToolRail, List.of("setSysOperation", "set_sys_operation"),
                readProperty(deepConfig, "sysOperation", "sys_operation"));
        invokeIfPresent(teamToolRail, List.of("setWorkspace", "set_workspace"),
                readProperty(deepConfig, "workspace"));
        invokeIfPresent(teamToolRail, List.of("init"), deepAgent);

        invokeIfPresent(deepAgent, List.of("addRail", "add_rail"), teamPolicyRail);
        addOptionalRail(deepAgent, firstIterGate);
        addOptionalRail(deepAgent, teamWorkspaceRail);
        addOptionalRail(deepAgent, toolApprovalRail);
        addOptionalRail(deepAgent, teamPlanModeRail);

        MountedRails rails = new MountedRails(
                teamToolRail,
                teamPolicyRail,
                firstIterGate,
                teamWorkspaceRail,
                toolApprovalRail,
                teamPlanModeRail
        );
        return new TeamHarness(deepAgent, rails, role, memberName, initialPlanMode);
    }

    private static void addOptionalRail(Object deepAgent, Object rail) {
        if (rail != null) {
            invokeIfPresent(deepAgent, List.of("addRail", "add_rail"), rail);
        }
    }

    @Override
    public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
        return runStreaming(inputs, sessionId, null);
    }

    public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId, Object teamSession) {
        Map<String, Object> safeInputs = inputs == null ? Map.of() : new LinkedHashMap<>(inputs);
        if (teamSession == null && !initialPlanMode) {
            return streamingRunner.run(deepAgent, safeInputs, sessionId);
        }
        Object agentSession = prepareAgentSession(safeInputs, sessionId, teamSession);
        return streamingRunner.run(deepAgent, safeInputs, agentSession);
    }

    private Object prepareAgentSession(Map<String, Object> inputs, String sessionId, Object teamSession) {
        Object card = readProperty(deepAgent, "card");
        Object agentSession = MISSING;
        if (teamSession != null) {
            agentSession = invokeIfPresent(teamSession, List.of("createAgentSession", "create_agent_session"),
                    card, false);
            if (agentSession == MISSING) {
                agentSession = invokeIfPresent(teamSession, List.of("createAgentSession", "create_agent_session"),
                        card);
            }
            if (agentSession == MISSING) {
                agentSession = invokeIfPresent(teamSession, List.of("createAgentSession", "create_agent_session"));
            }
        }
        if (agentSession == MISSING) {
            agentSession = new SimpleAgentSession(sessionId, card);
        }
        joinVoid(invokeIfPresent(agentSession, List.of("preRun", "pre_run"), inputs));
        activeAgentSession = agentSession;
        ensureInitialPlanMode(agentSession);
        return agentSession;
    }

    private void ensureInitialPlanMode(Object session) {
        if (!isInitialTeamPlanLeader() || initialPlanModeSeeded) {
            return;
        }
        Object state = invokeIfPresent(deepAgent, List.of("loadState", "load_state"), session);
        if (state == MISSING) {
            return;
        }
        Object planMode = readProperty(state, "planMode", "plan_mode");
        Object currentMode = readProperty(planMode, "mode");
        if (!"plan".equals(currentMode)) {
            joinVoid(invokeIfPresent(deepAgent, List.of("switchMode", "switch_mode"), session, "plan"));
        }
        initialPlanModeSeeded = true;
    }

    private boolean isInitialTeamPlanLeader() {
        return initialPlanMode && role != null && "leader".equals(role.value());
    }

    @Override
    public CompletionStage<Void> steer(String content) {
        return toVoidStage(invokeIfPresent(deepAgent, List.of("steer"), content));
    }

    @Override
    public CompletionStage<Void> followUp(String content) {
        return toVoidStage(invokeIfPresent(deepAgent, List.of("followUp", "follow_up"), content));
    }

    @Override
    public CompletionStage<Void> abort() {
        return toVoidStage(invokeIfPresent(deepAgent, List.of("abort")));
    }

    @Override
    public void initCwdForRound() {
        Object workspace = workspace();
        if (workspace == null || workspace == MISSING) {
            return;
        }
        Object root = workspace instanceof String ? workspace : readProperty(workspace, "rootPath", "root_path");
        if (root != null && root != MISSING) {
            Cwd.initCwd(String.valueOf(root), null, String.valueOf(root), null);
        }
    }

    @Override
    public boolean hasPendingInterrupt() {
        Object session = interruptSession();
        if (session == null) {
            return false;
        }
        return stateFor(session) != null;
    }

    @Override
    public boolean isPendingInterruptResumeValid(Object userInput) {
        if (!(userInput instanceof InteractiveInput interactiveInput)) {
            return false;
        }
        Object session = interruptSession();
        if (session == null) {
            return false;
        }
        Object state = stateFor(session);
        if (state == null) {
            return false;
        }
        Set<String> pendingIds = pendingInterruptIds(state);
        if (pendingIds.isEmpty()) {
            return false;
        }
        Set<String> resumeIds = interactiveInput.getUserInputs().keySet();
        return !resumeIds.isEmpty() && pendingIds.containsAll(resumeIds);
    }

    private Object interruptSession() {
        Object loopSession = readProperty(deepAgent, "loopSession", "loop_session");
        if (loopSession != null && loopSession != MISSING) {
            return loopSession;
        }
        return activeAgentSession;
    }

    private Object stateFor(Object session) {
        Object state = invokeIfPresent(session, List.of("getState", "get_state"), INTERRUPTION_KEY);
        return state == MISSING ? null : state;
    }

    private Set<String> pendingInterruptIds(Object state) {
        Object interruptedTools = readProperty(state, "interruptedTools", "interrupted_tools");
        Map<?, ?> interruptedMap = asMap(interruptedTools);
        Set<String> pendingIds = new LinkedHashSet<>();
        for (Object entry : interruptedMap.values()) {
            Map<?, ?> requests = asMap(readProperty(entry, "interruptRequests", "interrupt_requests"));
            for (Object requestId : requests.keySet()) {
                if (requestId != null) {
                    pendingIds.add(String.valueOf(requestId));
                }
            }
        }
        return pendingIds;
    }

    @Override
    public List<Object> findRails(Class<?> railType) {
        Object found = invokeIfPresent(deepAgent, List.of("findRailsByType", "find_rails_by_type"),
                (Object) new Class<?>[] {railType});
        if (found == MISSING) {
            found = invokeIfPresent(deepAgent, List.of("findRailsByType", "find_rails_by_type"), railType);
        }
        if (found instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (found instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        return List.of();
    }

    @Override
    public CompletionStage<Void> registerRail(Object rail) {
        return toVoidStage(invokeIfPresent(deepAgent, List.of("registerRail", "register_rail"), rail));
    }

    @Override
    public CompletionStage<Void> unregisterRail(Object rail) {
        return toVoidStage(invokeIfPresent(deepAgent, List.of("unregisterRail", "unregister_rail"), rail));
    }

    @Override
    public void registerMemberTools(Object memoryManager) {
        invokeIfPresent(memoryManager, List.of("registerTools", "register_tools"), deepAgent);
    }

    @Override
    public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
        return toVoidStage(invokeIfPresent(memoryManager, List.of("loadAndInject", "load_and_inject"),
                deepAgent, query));
    }

    @Override
    public void runAgentCustomizer(AgentCustomizer customizer) {
        if (customizer == null) {
            return;
        }
        try {
            customizer.customize(deepAgent, memberName, role == null ? null : role.value());
        } catch (RuntimeException ignored) {
            // Python logs and swallows broken user hooks; Java preserves the setup isolation.
        }
    }

    @Override
    public Object workspace() {
        Object deepConfig = deepConfig();
        return deepConfig == null ? null : readProperty(deepConfig, "workspace");
    }

    @Override
    public Object sysOperation() {
        Object deepConfig = deepConfig();
        return deepConfig == null ? null : readProperty(deepConfig, "sysOperation", "sys_operation");
    }

    public Object model() {
        Object deepConfig = deepConfig();
        return deepConfig == null ? null : readProperty(deepConfig, "model");
    }

    public Object deepConfig() {
        Object deepConfig = readProperty(deepAgent, "deepConfig", "deep_config");
        return deepConfig == MISSING ? null : deepConfig;
    }

    public Object innerAgent() {
        return deepAgent;
    }

    public Object getInnerAgent() {
        return deepAgent;
    }

    public MountedRails rails() {
        return rails;
    }

    public MountedRails getRails() {
        return rails;
    }

    public Object activeAgentSession() {
        return activeAgentSession;
    }

    private static CompletionStage<Void> toVoidStage(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return stage.thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static void joinVoid(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            stage.toCompletableFuture().join();
        }
    }

    private static Object invokeRequired(Object target, List<String> names, Object... args) {
        Object value = invokeIfPresent(target, names, args);
        if (value == MISSING) {
            throw new IllegalArgumentException("Missing required method " + names + " on " + target);
        }
        return value;
    }

    private static Object invokeIfPresent(Object target, List<String> names, Object... args) {
        if (target == null || target == MISSING) {
            return MISSING;
        }
        for (String name : names) {
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to invoke " + name, exception);
            }
        }
        return MISSING;
    }

    private static Method findMethod(Class<?> type, String name, Object[] args) {
        for (Method method : allMethods(type)) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                if (!matches(parameterTypes[index], args[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }

    private static List<Method> allMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
        Class<?> current = type;
        while (current != null) {
            methods.addAll(Arrays.asList(current.getDeclaredMethods()));
            current = current.getSuperclass();
        }
        return methods;
    }

    private static boolean matches(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        Class<?> boxed = box(parameterType);
        return boxed.isAssignableFrom(arg.getClass());
    }

    private static Class<?> box(Class<?> type) {
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
        if (type == char.class) {
            return Character.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return Void.class;
    }

    private static Object readProperty(Object target, String... names) {
        if (target == null || target == MISSING) {
            return MISSING;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
        }
        for (String name : names) {
            Object value = invokeIfPresent(target, List.of(getterName(name), name));
            if (value != MISSING) {
                return value;
            }
            Field field = findField(target.getClass(), name);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("Failed to read " + name, exception);
                }
            }
        }
        return MISSING;
    }

    private static String getterName(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return Collections.emptyMap();
    }

    /**
     * Handles to the team-side rails mounted onto DeepAgent.
     *
     * <p>Mirrors Python's {@code _MountedRails} in
     * {@code openjiuwen/agent_teams/harness.py}.</p>
     */
    public static final class MountedRails {
        private final Object teamTool;
        private final Object teamPolicy;
        private final Object firstIterGate;
        private final Object teamWorkspace;
        private final Object toolApproval;
        private final Object teamPlanMode;

        public MountedRails(Object teamTool, Object teamPolicy) {
            this(teamTool, teamPolicy, null, null, null, null);
        }

        public MountedRails(
                Object teamTool,
                Object teamPolicy,
                Object firstIterGate,
                Object teamWorkspace,
                Object toolApproval,
                Object teamPlanMode
        ) {
            this.teamTool = teamTool;
            this.teamPolicy = teamPolicy;
            this.firstIterGate = firstIterGate;
            this.teamWorkspace = teamWorkspace;
            this.toolApproval = toolApproval;
            this.teamPlanMode = teamPlanMode;
        }

        public Object getTeamTool() {
            return teamTool;
        }

        public Object getTeamPolicy() {
            return teamPolicy;
        }

        public Object getFirstIterGate() {
            return firstIterGate;
        }

        public Object getTeamWorkspace() {
            return teamWorkspace;
        }

        public Object getToolApproval() {
            return toolApproval;
        }

        public Object getTeamPlanMode() {
            return teamPlanMode;
        }
    }

    /**
     * Java seam for the Python {@code Runner.run_agent_streaming} call.
     *
     * <p>Mirrors Python's runner delegation in
     * {@code openjiuwen/agent_teams/harness.py}.</p>
     */
    @FunctionalInterface
    public interface StreamingRunner {
        Iterator<Object> run(Object agent, Map<String, Object> inputs, Object session);

        static StreamingRunner empty() {
            return (agent, inputs, session) -> Collections.emptyIterator();
        }
    }

    /**
     * Minimal child session used when a team session is not supplied.
     *
     * <p>Mirrors Python's fallback {@code create_agent_session(...)} path in
     * {@code openjiuwen/agent_teams/harness.py}.</p>
     */
    public static final class SimpleAgentSession {
        private final String sessionId;
        private final Object card;
        private Map<String, Object> inputs = Map.of();
        private final Map<String, Object> state = new LinkedHashMap<>();

        public SimpleAgentSession(String sessionId, Object card) {
            this.sessionId = sessionId;
            this.card = card;
        }

        public CompletionStage<Void> preRun(Map<String, Object> inputs) {
            this.inputs = inputs == null ? Map.of() : new LinkedHashMap<>(inputs);
            return CompletableFuture.completedFuture(null);
        }

        public Object getState(String key) {
            return state.get(key);
        }

        public void setState(String key, Object value) {
            state.put(key, value);
        }

        public String getSessionId() {
            return sessionId;
        }

        public Object getCard() {
            return card;
        }

        public Map<String, Object> getInputs() {
            return inputs;
        }
    }
}
