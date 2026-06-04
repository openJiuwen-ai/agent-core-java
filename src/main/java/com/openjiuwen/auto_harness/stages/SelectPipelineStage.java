package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.infra.InfraParsers;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.PipelineSelectionArtifact;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.harness.DeepAgent;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Session-level pipeline selection helpers.
 *
 * <p>Mirrors Python's {@code openjiuwen.auto_harness.stages.select_pipeline}.</p>
 */
public final class SelectPipelineStage {
    private static final String META_EVOLVE_PIPELINE = AutoHarnessPipelineNames.META_EVOLVE_PIPELINE;
    private static final String EXTENDED_EVOLVE_PIPELINE = AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE;
    private static final SelectPipelineAgentFactory DEFAULT_AGENT_FACTORY = config -> {
        DeepAgent agent = AutoHarnessAgentFactory.createSelectPipelineAgent(config);
        return inputs -> agent.stream(inputs, null, List.of(StreamMode.OUTPUT));
    };

    private SelectPipelineStage() {
    }

    public interface SelectPipelineAgent {
        Iterator<Object> stream(Map<String, Object> inputs);
    }

    public interface SelectPipelineAgentFactory {
        SelectPipelineAgent create(AutoHarnessConfig config);
    }

    public static PipelineSelectionArtifact runSelectPipeline(
            AutoHarnessConfig config,
            OptimizationTask task) {
        return runSelectPipeline(config, task, "", null, DEFAULT_AGENT_FACTORY);
    }

    public static PipelineSelectionArtifact runSelectPipeline(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines,
            SelectPipelineAgentFactory agentFactory) {
        if (task.getPipelineName() != null && !task.getPipelineName().isBlank()) {
            String pipelineName = AutoHarnessPipelineNames.normalizePipelineName(task.getPipelineName());
            return new PipelineSelectionArtifact(
                    pipelineName,
                    "task requested explicit pipeline",
                    1.0,
                    pipelineName,
                    List.of()
            );
        }
        if (config.getModel() == null) {
            return new PipelineSelectionArtifact(
                    META_EVOLVE_PIPELINE,
                    "no model configured, fallback to " + META_EVOLVE_PIPELINE,
                    0.0,
                    META_EVOLVE_PIPELINE,
                    List.of(EXTENDED_EVOLVE_PIPELINE)
            );
        }

        StringBuilder output = new StringBuilder();
        Iterator<Object> chunks = runSelectPipelineStream(config, task, assessment, availablePipelines, agentFactory);
        while (chunks.hasNext()) {
            output.append(InfraParsers.extractText(chunks.next()));
        }

        PipelineSelectionArtifact parsed = InfraParsers.parsePipelineSelection(output.toString());
        if (parsed != null) {
            return parsed;
        }
        return new PipelineSelectionArtifact(
                META_EVOLVE_PIPELINE,
                "selector fallback to default pipeline",
                0.0,
                META_EVOLVE_PIPELINE,
                List.of(EXTENDED_EVOLVE_PIPELINE)
        );
    }

    public static Iterator<Object> runSelectPipelineStream(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines) {
        return runSelectPipelineStream(config, task, assessment, availablePipelines, DEFAULT_AGENT_FACTORY);
    }

    public static Iterator<Object> runSelectPipelineStream(
            AutoHarnessConfig config,
            OptimizationTask task,
            String assessment,
            List<String> availablePipelines,
            SelectPipelineAgentFactory agentFactory) {
        SelectPipelineAgent agent = agentFactory.create(config);
        return agent.stream(Map.of(
                "query",
                buildQuery(task, assessment, availablePipelines != null ? availablePipelines : List.of(META_EVOLVE_PIPELINE))
        ));
    }

    public static String buildQuery(OptimizationTask task, String assessment, List<String> availablePipelines) {
        String summary = assessment != null ? assessment.strip() : "";
        if (summary.length() > 4000) {
            summary = summary.substring(0, 3997).stripTrailing() + "...";
        }
        List<String> pipelines = availablePipelines != null ? availablePipelines : List.of(META_EVOLVE_PIPELINE);
        return "任务主题: " + task.getTopic() + "\n"
                + "任务描述: " + defaultIfBlank(task.getDescription(), "无") + "\n"
                + "目标文件: " + defaultIfBlank(String.join(", ", task.getFiles()), "未指定") + "\n"
                + "评估摘要:\n" + defaultIfBlank(summary, "无") + "\n\n"
                + "可选 pipeline:\n"
                + String.join("\n", pipelines.stream().map(name -> "- " + name).toList());
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
