/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.stages;

import com.openjiuwen.autoharness.contexts.BaseExecutionContext;
import com.openjiuwen.autoharness.contexts.SessionContext;
import com.openjiuwen.autoharness.experience.ExperienceStore;
import com.openjiuwen.autoharness.factory.AutoHarnessFactory;
import com.openjiuwen.autoharness.infra.Parsers;
import com.openjiuwen.autoharness.schema.CycleResult;
import com.openjiuwen.autoharness.schema.Experience;
import com.openjiuwen.autoharness.schema.ExperienceType;
import com.openjiuwen.autoharness.schema.SessionResultsArtifact;
import com.openjiuwen.autoharness.schema.StageResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Public class LearningsStage used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class LearningsStage extends SessionStage {
    private static final Logger LOG = LoggerFactory.getLogger(LearningsStage.class);

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "learnings";
    }

    /**
     * description.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String description() {
        return "Record learnings after a session.";
    }

    /**
     * consumes.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> consumes() {
        return List.of("session_results");
    }

    /**
     * produces.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> produces() {
        return List.of("session_results");
    }

    /**
     * run.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public StageResult run(BaseExecutionContext ctx) {
        List<Object> events = stream(ctx);
        for (int index = events.size() - 1; index >= 0; index--) {
            if (events.get(index) instanceof StageResult result) {
                return result;
            }
        }
        return StageResult.builder().status("failed").error("learnings stage did not return StageResult").build();
    }

    /**
     * stream.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Object> stream(BaseExecutionContext ctx) {
        List<CycleResult> results = sessionResults(ctx);
        List<Object> events = new ArrayList<>();
        if (ctx instanceof SessionContext sessionContext) {
            events.addAll(runLearningsInternal(sessionContext, results));
        }
        events.add(StageResult.builder()
                .artifacts(Map.of("session_results", SessionResultsArtifact.builder().results(results).build()))
                .build());
        return events;
    }

    /**
     * buildResultsText.
     * 
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    public static String buildResultsText(List<CycleResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (CycleResult result : results) {
            String marker = firstText(result.getPrUrl(), result.getError(), "completed");
            lines.add("- " + marker + " (success=" + result.isSuccess() + ", isReverted=" + result.isReverted() + ")");
        }
        return String.join("\n", lines);
    }

    /**
     * buildExistingMemoriesText.
     * 
     * @param recent recent
     * @return the result
     * @since 0.1.7
     */
    public static String buildExistingMemoriesText(List<Experience> recent) {
        if (recent == null || recent.isEmpty()) {
            return "无";
        }
        List<String> lines = new ArrayList<>();
        for (Experience memory : recent) {
            lines.add("- [" + typeValue(memory.getType()) + "] " + value(memory.getTopic()) + ": "
                    + value(memory.getSummary()));
        }
        return String.join("\n", lines);
    }

    /**
     * buildQuery.
     * 
     * @param resultsText resultsText
     * @param existingText existingText
     * @return the result
     * @since 0.1.7
     */
    public static String buildQuery(String resultsText, String existingText) {
        return "本次 session 结果:\n" + value(resultsText) + "\n\n" + "已有经验:\n" + value(existingText) + "\n";
    }

    /**
     * recordLearnings.
     * 
     * @param output output
     * @param store store
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static int recordLearnings(String output, ExperienceStore store) throws IOException {
        int count = 0;
        for (Map<String, Object> learning : Parsers.parseLearnings(output)) {
            String type = String.valueOf(learning.getOrDefault("type", "insight"));
            store.record(Experience.builder().type(parseExperienceType(type))
                    .topic(String.valueOf(learning.getOrDefault("topic", "")))
                    .summary(String.valueOf(learning.getOrDefault("summary", "")))
                    .details(String.valueOf(learning.getOrDefault("details", ""))).build());
            count++;
        }
        return count;
    }

    /**
     * runLearnings.
     * 
     * @param ctx ctx
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    public static List<Object> runLearnings(SessionContext ctx, List<CycleResult> results) {
        if (ctx == null || results == null || results.isEmpty()) {
            return List.of();
        }
        LearningsStage stage = new LearningsStage();
        return stage.runLearningsInternal(ctx, results);
    }

    /**
     * runLearningsInternal.
     * 
     * @param ctx ctx
     * @param results results
     * @return the result
     * @since 0.1.7
     */
    private List<Object> runLearningsInternal(SessionContext ctx, List<CycleResult> results) {
        try {
            String resultsText = buildResultsText(results);
            String existingText = buildExistingMemoriesText(ctx.getOrchestrator().getExperienceStore().listRecent(10));
            Object agent =
                AutoHarnessFactory.createLearningsAgent(ctx.getOrchestrator().getConfig(), resultsText, existingText);
            String query = buildQuery(resultsText, existingText);
            List<Object> events = new ArrayList<>();
            String output = "";
            for (Object chunk : streamAgent(agent, query)) {
                events.add(chunk);
                output += Parsers.extractText(chunk);
            }
            int recorded = recordLearnings(output, ctx.getOrchestrator().getExperienceStore());
            LOG.info("Learnings recorded: {}", recorded);
            return events;
        } catch (Exception ex) {
            LOG.warn("Learnings phase failed", ex);
            return List.of();
        }
    }

    /**
     * sessionResults.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    private static List<CycleResult> sessionResults(BaseExecutionContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        Object artifact = ctx.getArtifact("session_results", null);
        if (artifact instanceof SessionResultsArtifact resultsArtifact) {
            return resultsArtifact.getResults();
        }
        if (ctx instanceof SessionContext sessionContext) {
            return sessionContext.getOrchestrator().getResults();
        }
        return List.of();
    }

    /**
     * parseExperienceType.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static ExperienceType parseExperienceType(String type) {
        if (type == null || type.isBlank()) {
            return ExperienceType.INSIGHT;
        }
        try {
            return ExperienceType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ExperienceType.INSIGHT;
        }
    }

    /**
     * typeValue.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private static String typeValue(ExperienceType type) {
        return type == null ? "insight" : type.name().toLowerCase(Locale.ROOT);
    }

    /**
     * firstText.
     * 
     * @param first first
     * @param second second
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static String firstText(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    /**
     * value.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * streamAgent.
     * 
     * @param agent agent
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    private static List<Object> streamAgent(Object agent, String query) {
        if (agent == null) {
            return List.of();
        }
        try {
            Object stream = agent.getClass().getMethod("stream", Map.class).invoke(agent, Map.of("query", query));
            if (stream instanceof Iterator<?> iterator) {
                List<Object> events = new ArrayList<>();
                while (iterator.hasNext()) {
                    events.add(iterator.next());
                }
                return events;
            }
            if (stream instanceof Iterable<?> iterable) {
                List<Object> events = new ArrayList<>();
                for (Object event : iterable) {
                    events.add(event);
                }
                return events;
            }
        } catch (ReflectiveOperationException ignored) {
            return List.of();
        }
        return List.of();
    }
}
