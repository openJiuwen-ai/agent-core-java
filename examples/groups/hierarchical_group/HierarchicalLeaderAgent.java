package examples.groups.hierarchical_group;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.legacy.GroupEvent;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Example-local leader agent that dispatches to worker agents through the group controller.
 */
@SuppressWarnings("deprecation")
final class HierarchicalLeaderAgent extends BaseAgent {

    private static final String STATE_KEY = "hierarchical_group_example";
    private static final String PENDING_WORKER_ID = "pending_worker_id";
    private static final String NO_PENDING_REPLY = "当前没有等待补充的问题，请先输入新的业务请求。";

    private final Map<String, Object> config;
    private final Map<String, List<String>> workerKeywords;
    private final String defaultResponse;
    private HierarchicalGroupController groupController;

    HierarchicalLeaderAgent(
            String agentId,
            String description,
            Map<String, List<String>> workerKeywords,
            String defaultResponse
    ) {
        super(AgentCard.builder()
                .id(agentId)
                .name(agentId)
                .description(description)
                .build());
        this.workerKeywords = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : workerKeywords.entrySet()) {
            this.workerKeywords.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.defaultResponse = defaultResponse;

        Map<String, Object> initialConfig = new LinkedHashMap<>();
        initialConfig.put("defaultResponse", defaultResponse);
        initialConfig.put("workerKeywords", new LinkedHashMap<>(this.workerKeywords));
        this.config = initialConfig;
    }

    @Override
    public BaseAgent configure(Object config) {
        if (config instanceof Map<?, ?> configMap) {
            for (Map.Entry<?, ?> entry : configMap.entrySet()) {
                this.config.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return this;
    }

    @Override
    public Object getConfig() {
        return config;
    }

    void setGroupController(HierarchicalGroupController groupController) {
        this.groupController = groupController;
    }

    @Override
    public Object invoke(Object inputs, Session session) {
        if (groupController == null) {
            throw new IllegalStateException("HierarchicalLeaderAgent is not attached to a group controller");
        }

        AgentGroupSessionApi groupSession = toGroupSession(session, inputs);
        Map<String, Object> normalizedInputs = normalizeInputs(inputs, groupSession);
        Object queryPayload = normalizedInputs.get("query");

        String workerId = resolveWorkerId(queryPayload, groupSession);
        if (workerId == null) {
            clearPendingWorker(groupSession);
            if (queryPayload instanceof InteractiveInput) {
                return Map.of("answer", NO_PENDING_REPLY);
            }
            return Map.of("answer", defaultResponse);
        }

        Loggers.MULTI_AGENT.info("HierarchicalLeaderAgent: Dispatching to worker {}", workerId);
        GroupEvent event = GroupEvent.fromMap(normalizedInputs);
        Object result = groupController.sendToAgent(event, workerId, groupSession);
        updatePendingWorker(workerId, result, groupSession);
        return result;
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        Object result = invoke(inputs, session);
        if (result instanceof List<?> list) {
            return new ArrayList<Object>(list).iterator();
        }
        return List.of(result).iterator();
    }

    private AgentGroupSessionApi toGroupSession(Session session, Object inputs) {
        if (session instanceof AgentGroupSessionApi groupSessionApi) {
            return groupSessionApi;
        }
        String sessionId = session != null && session.getSessionId() != null
                ? session.getSessionId()
                : String.valueOf(normalizeInputs(inputs, null).getOrDefault("conversation_id", "default_session"));
        return AgentGroupSessionApi.create(sessionId, null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInputs(Object inputs, AgentGroupSessionApi session) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (inputs instanceof Map<?, ?> map) {
            normalized.putAll((Map<String, Object>) map);
        } else {
            normalized.put("query", inputs);
        }

        if (!normalized.containsKey("conversation_id")) {
            normalized.put("conversation_id", session != null ? session.getSessionId() : "default_session");
        }
        return normalized;
    }

    private String resolveWorkerId(Object queryPayload, AgentGroupSessionApi session) {
        if (queryPayload instanceof InteractiveInput) {
            return getPendingWorker(session);
        }

        String normalizedText = extractQueryText(queryPayload).toLowerCase(Locale.ROOT);
        if (normalizedText.isBlank()) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : workerKeywords.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalizedText.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    private void updatePendingWorker(String workerId, Object result, AgentGroupSessionApi session) {
        if (containsInteraction(result)) {
            Map<String, Object> state = readState(session);
            state.put(PENDING_WORKER_ID, workerId);
            session.updateState(Map.of(STATE_KEY, state));
            return;
        }
        clearPendingWorker(session);
    }

    private void clearPendingWorker(AgentGroupSessionApi session) {
        Map<String, Object> state = readState(session);
        state.remove(PENDING_WORKER_ID);
        session.updateState(Map.of(STATE_KEY, state));
    }

    private String getPendingWorker(AgentGroupSessionApi session) {
        Object workerId = readState(session).get(PENDING_WORKER_ID);
        return workerId == null ? null : String.valueOf(workerId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readState(AgentGroupSessionApi session) {
        Object value = session.getState(STATE_KEY);
        if (value instanceof Map<?, ?> stateMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : stateMap.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private boolean containsInteraction(Object result) {
        if (result instanceof OutputSchema outputSchema) {
            return isInteractionType(outputSchema.getType());
        }
        if (result instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof OutputSchema outputSchema && isInteractionType(outputSchema.getType())) {
                    return true;
                }
                if (item instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    if (type != null && isInteractionType(String.valueOf(type))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInteractionType(String type) {
        return Constant.INTERACTION.equals(type) || "interaction".equals(type);
    }

    private String extractQueryText(Object payload) {
        if (payload == null) {
            return "";
        }
        if (payload instanceof String text) {
            return text;
        }
        if (payload instanceof InteractiveInput interactiveInput) {
            if (interactiveInput.getRawInputs() != null) {
                return String.valueOf(interactiveInput.getRawInputs());
            }
            if (interactiveInput.getUserInputs() != null && !interactiveInput.getUserInputs().isEmpty()) {
                return String.valueOf(interactiveInput.getUserInputs().values().iterator().next());
            }
            return "";
        }
        return String.valueOf(payload);
    }
}