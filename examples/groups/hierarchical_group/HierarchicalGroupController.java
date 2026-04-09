  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package examples.groups.hierarchical_group;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.legacy.BaseGroupController;
import com.openjiuwen.core.multiagent.legacy.GroupEvent;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Example-local group controller that mirrors the Python hierarchical group routing rules.
 */
@SuppressWarnings("deprecation")
final class HierarchicalGroupController extends BaseGroupController {

    private final String leaderAgentId;

    HierarchicalGroupController(String leaderAgentId) {
        this.leaderAgentId = leaderAgentId;
    }

    @Override
    protected Object handleEvent(GroupEvent event, AgentGroupSessionApi session) {
        if (event.getReceiverId() != null && !event.getReceiverId().isBlank()) {
            if (leaderAgentId.equals(event.getReceiverId())) {
                return routeToLeader(event, session);
            }
            Loggers.MULTI_AGENT.info(
                    "HierarchicalGroupController: Routing to explicit receiver_id={}",
                    event.getReceiverId()
            );
            return sendToAgent(event, event.getReceiverId(), session);
        }

        if (event.getCustomEventType() != null && !event.getCustomEventType().isBlank()) {
            List<String> subscribers = getSubscribers(event.getCustomEventType());
            if (!subscribers.isEmpty()) {
                Loggers.MULTI_AGENT.info(
                        "HierarchicalGroupController: Publishing to {} subscribers for custom_event_type={}",
                        subscribers.size(),
                        event.getCustomEventType()
                );
                List<Object> results = publish(event, session);
                return results.size() == 1 ? results.get(0) : results;
            }
        }

        return routeToLeader(event, session);
    }

    private Object routeToLeader(GroupEvent event, AgentGroupSessionApi session) {
        BaseAgent leader = getAgentGroup().getAgents().get(leaderAgentId);
        if (leader == null) {
            throw new IllegalStateException(
                    "Leader agent '" + leaderAgentId + "' not found in group. Available agents: "
                            + getAgentGroup().getAgents().keySet()
            );
        }

        Loggers.MULTI_AGENT.info(
                "HierarchicalGroupController: Routing to leader (default), leader_agent_id={}",
                leaderAgentId
        );

        Map<String, Object> inputs = new LinkedHashMap<>();
        Object queryPayload = event.getQueryPayload() != null ? event.getQueryPayload() : event.getQuery();
        inputs.put("query", queryPayload);
        inputs.put("conversation_id", event.getConversationId());
        if (event.getUserId() != null && !event.getUserId().isBlank()) {
            inputs.put("user_id", event.getUserId());
        }
        if (event.getCustomEventType() != null && !event.getCustomEventType().isBlank()) {
            inputs.put("custom_event_type", event.getCustomEventType());
        }
        return leader.invoke(inputs, session);
    }
}