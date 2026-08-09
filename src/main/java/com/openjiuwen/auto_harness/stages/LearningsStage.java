/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.experience.ExperienceStore;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.CycleResult;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExperienceType;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.SessionResultsArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Record learnings after a session completes.
 *
 * <p>Mirrors Python's {@code LearningsStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/learnings.py}.</p>
 */
public class LearningsStage extends SessionStage {

    private static final Logger LOGGER = Logger.getLogger(LearningsStage.class.getName());
    private static final int RECENT_MEMORY_LIMIT = 10;

    public static final LearningsAgentFactory DEFAULT_AGENT_FACTORY = (config, results, memories, extraRails) -> {
        DeepAgent agent = AutoHarnessAgentFactory.createLearningsAgent(config, results, memories, extraRails);
        return agent::stream;
    };

    private final LearningsAgentFactory agentFactory;

    public LearningsStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public LearningsStage(LearningsAgentFactory agentFactory) {
        this.agentFactory = agentFactory == null ? DEFAULT_AGENT_FACTORY : agentFactory;
    }

    @Override
    public String name() {
        return "learnings";
    }

    @Override
    public String slot() {
        return "learnings";
    }

    @Override
    public String displayName() {
        return "总结经验";
    }

    @Override
    public String description() {
        return "Record learnings after a session.";
    }

    @Override
    public List<String> consumes() {
        return List.of("session_results");
    }

    @Override
    public List<String> produces() {
        return List.of("session_results");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("LearningsStage requires SessionContext");
        }
        List<CycleResult> results = resolveSessionResults(sessionContext);
        List<Object> events = new ArrayList<>();
        runLearnings(
                sessionContext.getOrchestrator().getConfig(),
                results,
                sessionContext.getOrchestrator().getExperienceStore(),
                sessionContext.getOrchestrator().getStreamRails(),
                agentFactory
        ).forEachRemaining(events::add);
        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("session_results", SessionResultsArtifact.builder()
                .results(new ArrayList<>(results))
                .build());
        events.add(StageResult.builder().artifacts(artifacts).build());
        return events.iterator();
    }

    public static Iterator<Object> runLearnings(
            AutoHarnessConfig config,
            List<CycleResult> results,
            ExperienceStore experienceStore,
            List<?> extraRails,
            LearningsAgentFactory agentFactory
    ) {
        List<CycleResult> safeResults = results == null ? List.of() : results;
        if (safeResults.isEmpty()) {
            return List.of().iterator();
        }
        String resultsText = formatResults(safeResults);
        String existingText = formatExistingMemories(listRecent(experienceStore, RECENT_MEMORY_LIMIT));
        LearningsAgent agent = safeFactory(agentFactory).create(
                config == null ? new AutoHarnessConfig() : config,
                resultsText,
                existingText,
                deepAgentRails(extraRails)
        );
        String query = "本次 session 结果:\n" + resultsText + "\n\n"
                + "已有经验:\n" + existingText + "\n";
        List<Object> events = new ArrayList<>();
        try {
            StringBuilder output = new StringBuilder();
            Iterator<?> stream = agent.stream(Map.of("query", query));
            while (stream.hasNext()) {
                Object chunk = stream.next();
                events.add(chunk);
                output.append(Parsers.extractText(chunk));
            }
            for (Map<String, Object> learning : Parsers.parseLearnings(output.toString())) {
                experienceStore.record(Experience.builder()
                        .type(parseExperienceType(learning.getOrDefault("type", "insight")))
                        .topic(stringValue(learning.get("topic")))
                        .summary(stringValue(learning.get("summary")))
                        .details(stringValue(learning.get("details")))
                        .build()).join();
            }
            LOGGER.info(() -> "Learnings recorded: " + Parsers.parseLearnings(output.toString()).size());
        } catch (RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Learnings phase failed", exception);
        }
        return events.iterator();
    }

    static String formatResults(List<CycleResult> results) {
        List<String> lines = new ArrayList<>();
        for (CycleResult result : results == null ? List.<CycleResult>of() : results) {
            String summary = firstNonBlank(result.getSummary(), result.getPrUrl(), result.getError(), "completed");
            lines.add("- " + summary + " (success=" + pythonBool(result.isSuccess())
                    + ", reverted=" + pythonBool(result.isReverted()) + ")");
        }
        return String.join("\n", lines);
    }

    static String formatExistingMemories(List<Experience> memories) {
        if (memories == null || memories.isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        for (Experience memory : memories) {
            ExperienceType type = memory.getType();
            String typeValue = type == null ? "" : type.value();
            lines.add("- [" + typeValue + "] " + stringValue(memory.getTopic()) + ": "
                    + stringValue(memory.getSummary()));
        }
        return String.join("\n", lines);
    }

    private static List<CycleResult> resolveSessionResults(SessionContext ctx) {
        Object artifact = ctx.getArtifact("session_results");
        if (artifact instanceof SessionResultsArtifact sessionResults && sessionResults.getResults() != null) {
            return new ArrayList<>(sessionResults.getResults());
        }
        return ctx.getOrchestrator().getResults();
    }

    private static List<Experience> listRecent(ExperienceStore experienceStore, int limit) {
        return experienceStore == null ? List.of() : experienceStore.listRecent(limit).join();
    }

    private static List<DeepAgentRail> deepAgentRails(List<?> extraRails) {
        List<DeepAgentRail> rails = new ArrayList<>();
        for (Object rail : extraRails == null ? List.of() : extraRails) {
            if (rail instanceof DeepAgentRail deepAgentRail) {
                rails.add(deepAgentRail);
            }
        }
        return rails;
    }

    private static LearningsAgentFactory safeFactory(LearningsAgentFactory factory) {
        return factory == null ? DEFAULT_AGENT_FACTORY : factory;
    }

    private static ExperienceType parseExperienceType(Object value) {
        String text = stringValue(value);
        for (ExperienceType type : ExperienceType.values()) {
            if (type.value().equals(text)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown learning type: " + text);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String pythonBool(boolean value) {
        return value ? "True" : "False";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Streaming surface used by the learnings agent.
     *
     * <p>Mirrors Python's learnings agent stream contract in
     * {@code openjiuwen/auto_harness/stages/learnings.py}.</p>
     */
    @FunctionalInterface
    public interface LearningsAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for the learnings agent.
     *
     * <p>Mirrors Python's late import of {@code create_learnings_agent} in
     * {@code openjiuwen/auto_harness/stages/learnings.py}.</p>
     */
    @FunctionalInterface
    public interface LearningsAgentFactory {
        LearningsAgent create(
                AutoHarnessConfig config,
                String sessionResults,
                String existingMemories,
                List<DeepAgentRail> extraRails
        );
    }
}
