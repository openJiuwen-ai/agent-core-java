/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent.builtin_teams;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.examples.multi_agent.ExampleCommunicableAgent;

import java.util.Map;

/**
 * Hierarchical tools research team example.
 *
 * <p>Mirrors Python's {@code hierarchical_tools_research_team} in
 * {@code examples.multi_agent.builtin_teams}.</p>
 */
public final class HierarchicalToolsResearchTeamExample {

    public static final String TEAM_ID = "research_team_tools";
    public static final String STATISTICS_EXPERT = "statistics_expert";
    public static final String LITERATURE_RESEARCHER = "literature_researcher";
    public static final String DATA_ANALYST = "data_analyst";
    public static final String RESEARCH_DIRECTOR = "research_director";

    public static final AgentCard STATISTICS_EXPERT_CARD = HierarchicalMsgbusResearchTeamExample.card(
            STATISTICS_EXPERT, "Statistics expert");
    public static final AgentCard LITERATURE_RESEARCHER_CARD = HierarchicalMsgbusResearchTeamExample.card(
            LITERATURE_RESEARCHER, "Literature researcher");
    public static final AgentCard DATA_ANALYST_CARD = HierarchicalMsgbusResearchTeamExample.card(
            DATA_ANALYST, "Data analyst");
    public static final AgentCard RESEARCH_DIRECTOR_CARD = HierarchicalMsgbusResearchTeamExample.card(
            RESEARCH_DIRECTOR, "Research director");

    private HierarchicalToolsResearchTeamExample() {
        // Utility class
    }

    public static Map<String, Object> runResearchTask(String topic) {
        LiteratureResearcher literatureResearcher = new LiteratureResearcher(LITERATURE_RESEARCHER_CARD);
        StatisticsExpert statisticsExpert = new StatisticsExpert(STATISTICS_EXPERT_CARD);
        Object literature = literatureResearcher.invoke(Map.of("topic", topic), null);
        Object analysis = statisticsExpert.invoke(Map.of("task", topic), null);
        return Map.of(
                "team_id", TEAM_ID,
                "root_agent", RESEARCH_DIRECTOR,
                "tool_mode", true,
                "literature", literature,
                "analysis", analysis
        );
    }

    public static final class StatisticsExpert extends ExampleCommunicableAgent {
        public StatisticsExpert(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String task = HierarchicalMsgbusResearchTeamExample.value(inputs, "task");
            return Map.of(
                    "analysis", "Statistical analysis result for '" + task
                            + "': descriptive statistics, hypothesis tests, and regression analysis",
                    "confidence", 0.95
            );
        }
    }

    public static final class LiteratureResearcher extends ExampleCommunicableAgent {
        public LiteratureResearcher(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String topic = HierarchicalMsgbusResearchTeamExample.value(inputs, "topic");
            return Map.of(
                    "literature_summary", "Literature research: found 15 high-quality papers about '" + topic + "'",
                    "paper_count", 15
            );
        }
    }
}
