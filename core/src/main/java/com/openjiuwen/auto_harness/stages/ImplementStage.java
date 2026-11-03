/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.infra.EditScope;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Experience;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageSlot;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Abstract base for all implement-slot stages and module helpers.
 *
 * <p>Mirrors Python's {@code ImplementStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/implement.py}.</p>
 */
public abstract class ImplementStage extends TaskStage {

    private static final Logger LOGGER = Logger.getLogger(ImplementStage.class.getName());

    @Override
    public String name() {
        return "implement";
    }

    @Override
    public String slot() {
        return StageSlot.IMPLEMENT.value();
    }

    @Override
    public String displayName() {
        return "执行代码修改";
    }

    @Override
    public String description() {
        return "Implement code changes.";
    }

    @Override
    public List<String> produces() {
        return List.of("code_change");
    }

    /**
     * Mirrors Python's {@code _build_implement_prompt} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static String buildImplementPrompt(OptimizationTask task, List<Experience> related) {
        List<String> contextParts = new ArrayList<>();
        for (Experience exp : related == null ? List.<Experience>of() : related) {
            String type = exp.getType() == null ? "" : exp.getType().value();
            contextParts.add("- [" + type + "] " + nullToEmpty(exp.getTopic())
                    + ": " + nullToEmpty(exp.getSummary()));
        }
        String context = contextParts.isEmpty() ? "无" : String.join("\n", contextParts);
        String editScope = EditScope.renderEditScope("本轮实现阶段允许改动的路径");
        return "任务: " + nullToEmpty(task == null ? "" : task.getTopic()) + "\n"
                + "描述: " + nullToEmpty(task == null ? "" : task.getDescription()) + "\n"
                + "目标文件: " + joinOrDefault(task == null ? List.of() : task.getFiles(), "自行判断") + "\n"
                + "\n相关经验:\n" + context + "\n"
                + "\n" + editScope + "\n"
                + "\n本阶段只允许完成代码修改与局部验证。"
                + "\n默认直接开始实施修改，不要等待人工确认。"
                + "\n禁止输出“是否需要我开始实现”“如果需要请指示”“是否继续”之类的回问；"
                + "除非存在明确范围冲突、缺少关键输入或必须越界编辑，否则必须直接动手修改代码。"
                + "\n如果 `task.files` 包含范围外路径，或你判断必须修改范围外文件才能完成任务，"
                + "立即停止并明确报告，不要尝试越界编辑。"
                + "\n严禁执行 git add、git commit 或其他提交动作；"
                + "提交只允许在后续独立 commit phase 中进行。";
    }

    /**
     * Mirrors Python's {@code _build_prompt_debug_stats} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static Map<String, Integer> buildPromptDebugStats(String prompt) {
        String value = prompt == null ? "" : prompt;
        return Map.of(
                "chars", value.length(),
                "lines", countLines(value),
                "bytes", value.getBytes(StandardCharsets.UTF_8).length
        );
    }

    /**
     * Mirrors Python's {@code _extract_repo_edit_candidates} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static List<String> extractRepoEditCandidates(
            String statusText,
            List<String> diffFiles,
            List<String> preexistingDirtyFiles
    ) {
        List<String> files = new ArrayList<>();
        Set<String> preexisting = new LinkedHashSet<>();
        for (String path : preexistingDirtyFiles == null ? List.<String>of() : preexistingDirtyFiles) {
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isEmpty()) {
                preexisting.add(normalized);
            }
        }
        for (String line : (statusText == null ? "" : statusText).split("\\R")) {
            String raw = line.stripTrailing();
            if (raw.length() < 3) {
                continue;
            }
            String path = "";
            if (raw.startsWith("?? ")) {
                path = raw.substring(3).trim();
            } else if (raw.length() >= 4 && raw.charAt(2) == ' ') {
                path = raw.substring(3).trim();
            } else if (raw.length() >= 3 && raw.charAt(1) == ' ') {
                path = raw.substring(2).trim();
            }
            if (path.isEmpty()) {
                continue;
            }
            if (path.contains(" -> ")) {
                path = path.split(" -> ", 2)[1].trim();
            }
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isEmpty()) {
                files.add(normalized);
            }
        }
        for (String path : diffFiles == null ? List.<String>of() : diffFiles) {
            String normalized = EditScope.normalizeRepoPath(path);
            if (!normalized.isEmpty()) {
                files.add(normalized);
            }
        }
        List<String> filtered = new ArrayList<>();
        for (String path : new LinkedHashSet<>(files)) {
            if (!EditScope.isAllowedRepoEditPath(path)) {
                continue;
            }
            if (preexisting.contains(path)) {
                continue;
            }
            filtered.add(path);
        }
        return filtered;
    }

    /**
     * Mirrors Python's {@code _summarize_text} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static String summarizeText(String text) {
        return summarizeText(text, 6, 400);
    }

    public static String summarizeText(String text, int maxLines, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                lines.add(stripped);
            }
        }
        String summary = String.join("\n", lines.subList(0, Math.min(maxLines, lines.size()))).strip();
        if (summary.length() > maxChars) {
            return summary.substring(0, Math.max(0, maxChars - 3)).stripTrailing() + "...";
        }
        if (lines.size() > maxLines) {
            return summary + "\n...";
        }
        return summary;
    }

    /**
     * Mirrors Python's {@code _extract_controller_task_failed_error} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static String extractControllerTaskFailedError(Object chunk) {
        Object type = readFieldOrGetter(chunk, "type");
        if (!"controller_output".equals(String.valueOf(type))) {
            return "";
        }
        Object payload = readFieldOrGetter(chunk, "payload");
        if (payload == null) {
            return "";
        }
        Object payloadType;
        Object payloadData;
        if (payload instanceof Map<?, ?> map) {
            Object rawType = map.get("type");
            Object rawData = map.get("data");
            payloadType = rawType == null ? "" : rawType;
            payloadData = rawData == null ? List.of() : rawData;
        } else {
            payloadType = readFieldOrGetter(payload, "type");
            payloadData = readFieldOrGetter(payload, "data");
        }
        if (!"task_failed".equals(String.valueOf(payloadType).toLowerCase())) {
            return "";
        }
        List<String> texts = new ArrayList<>();
        if (payloadData instanceof List<?> list) {
            for (Object item : list) {
                Object text = item instanceof Map<?, ?> map ? map.get("text") : readFieldOrGetter(item, "text");
                String value = String.valueOf(text == null ? "" : text).strip();
                if (!value.isEmpty()) {
                    texts.add(value);
                }
            }
        }
        if (!texts.isEmpty()) {
            return String.join("\n", texts);
        }
        return String.valueOf(payload).strip();
    }

    /**
     * Mirrors Python's {@code _format_ci_status_for_evaluator} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    @SuppressWarnings("unchecked")
    public static String formatCiStatusForEvaluator(Map<String, Object> ciResult) {
        Map<String, Object> result = ciResult == null ? Map.of() : ciResult;
        Object gatesRaw = result.getOrDefault("gates", List.of());
        List<?> gates = gatesRaw instanceof List<?> list ? list : List.of();
        if (gates.isEmpty()) {
            String errors = summarizeText(String.valueOf(result.getOrDefault("errors", "")));
            return "结论: blocking failure\n详情: " + (errors.isBlank() ? "未执行任何门禁" : errors);
        }
        List<String> lines = new ArrayList<>();
        lines.add(Boolean.TRUE.equals(result.get("passed")) ? "结论: pass" : "结论: blocking failure");
        for (Object item : gates) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> gate = (Map<String, Object>) rawMap;
            boolean passed = Boolean.TRUE.equals(gate.get("passed"));
            String line = "- " + String.valueOf(gate.getOrDefault("name", "unknown"))
                    + ": " + (passed ? "PASS" : "FAIL");
            String detail = summarizeText(String.valueOf(gate.getOrDefault("output", "")));
            if (!detail.isBlank() && !passed) {
                line = line + " | " + detail;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    /**
     * Mirrors Python's {@code run_implement_stream} in
     * {@code openjiuwen/auto_harness/stages/implement.py}.
     */
    public static List<Object> runImplementStream(
            DeepAgent agent,
            OptimizationTask task,
            List<Experience> related,
            Object session,
            String prompt
    ) {
        if (agent == null) {
            LOGGER.warning("No agent, skipping implement");
            return List.of();
        }
        String effectivePrompt = prompt == null || prompt.isBlank() ? buildImplementPrompt(task, related) : prompt;
        List<Object> events = new ArrayList<>();
        Iterator<?> iterator = agent.stream(Map.of("query", effectivePrompt));
        while (iterator.hasNext()) {
            events.add(restoreOutputSchemaIfPresent(iterator.next()));
        }
        return events;
    }

    static Object restoreOutputSchemaIfPresent(Object rawChunk) {
        if (!(rawChunk instanceof Map<?, ?> map)) {
            return rawChunk;
        }
        Map<String, Object> chunk = new LinkedHashMap<>();
        map.forEach((k, v) -> chunk.put(String.valueOf(k), v));
        Object output = chunk.get("_output");
        if (output instanceof OutputSchema) {
            return output;
        }
        return chunk;
    }

    public static String artifactContractHint(String name) {
        String lowered = name == null ? "" : name.toLowerCase();
        String common = "文件/产物生成类 Tool 必须在返回 success=true 前完成自校验："
                + "输出路径存在、size_bytes > 0、format 与文件后缀一致，并返回 "
                + "success/path 或 absolute_path/format/exists/size_bytes 等结构化字段。"
                + "如果依赖缺失、写入失败或格式校验失败，必须返回 success=false "
                + "和明确错误；不得用成功文本掩盖失败。";
        if (lowered.contains("ppt") || lowered.contains("pptx") || lowered.contains("powerpoint")) {
            return common + " PPT/PPTX 生成必须产出真实 .pptx 文件；不得用 JSON、Markdown、"
                    + "纯文本或“待下游转换”的中间结构冒充 PPTX。成功前必须用 zipfile 校验文件是合法 zip 包。";
        }
        if (lowered.contains("docx") || lowered.contains("word")) {
            return common + " DOCX 生成必须产出真实 .docx 文件，并用 zipfile 校验。";
        }
        if (lowered.contains("pdf")) {
            return common + " PDF 生成必须产出真实 .pdf 文件，并校验文件头以 `%PDF` 开始。";
        }
        if (lowered.contains("json")) {
            return common + " JSON 生成必须写出可被 json parser 解析的文件，并校验关键字段存在。";
        }
        return common;
    }

    public static String buildImplementExtPrompt(ExtensionDesign design, java.nio.file.Path extensionRoot,
                                                 java.nio.file.Path configPath) {
        ExtensionDesign effective = design == null ? new ExtensionDesign() : design;
        List<String> components = effective.getComponents() == null || effective.getComponents().isEmpty()
                ? List.of("tool", "skill")
                : effective.getComponents();
        List<String> filePlanLines = new ArrayList<>();
        for (Map.Entry<String, String> entry : effective.getFilePlan().entrySet()) {
            filePlanLines.add("  - " + entry.getKey() + ": " + entry.getValue());
        }
        String filePlanText = filePlanLines.isEmpty() ? "  (无)" : String.join("\n", filePlanLines);
        List<String> requirements = new ArrayList<>();
        requirements.add("1. 在扩展根目录下创建完整的 Python 包结构");
        requirements.add("2. 严格按 ExtensionDesign.components 实现组件；不要为了完整性自动补充未声明的 Rail、Tool 或 Skill。");
        requirements.add("3. 实现必须贴合 extension_name 和 gap 语义，保留用户目标中的关键实体与产物类型。");
        requirements.add("4. 真实产物契约：" + artifactContractHint(effective.getExtensionName()));
        requirements.add("Do not generalize PPT/document/office generation requests into requirements collection or structured requirement reports.");
        requirements.add("For PPT/PPTX artifacts, validate a real .pptx zip with zipfile, [Content_Types].xml, ppt/presentation.xml, and ppt/slides/slide*.xml.");
        requirements.add("Import checks must read the actual module and class values from harness_config.yaml; do not guess module paths.");
        requirements.add("All rail/tool modules must start with openjiuwen.extensions.harness.<extension_name>. and point at real Python files.");
        int step = 5;
        if (components.contains("rail")) {
            requirements.add(step++ + ". 实现 rail 组件 (继承 DeepAgentRail)。");
        }
        if (components.contains("tool")) {
            if (components.contains("skill")) {
                requirements.add("Tool + Skill cooperation: ToolCard.description must state it should be used with the corresponding Skill.");
            }
            requirements.add(step++ + ". 实现 tool 组件 (继承 Tool，包含 ToolCard)。");
        }
        if (components.contains("skill")) {
            if (effective.getSkillSource() == null || effective.getSkillSource().isBlank()) {
                requirements.add("Follow skill-creator guidance; keep domain rules, brand style, generation flow, examples, and acceptance criteria in SKILL.md.");
                requirements.add("Place reusable templates, brand assets, or detailed references under assets/ or references/ and describe when to use them in SKILL.md.");
            }
            if (effective.getSkillSource() != null && !effective.getSkillSource().isBlank()) {
                requirements.add(step++ + ". Skill 部分已从社区 skill 复用；不要重新创建或修改 SKILL.md。");
            } else {
                requirements.add(step++ + ". 创建 skills/<skill_name>/SKILL.md，包含 frontmatter 和可操作正文。");
            }
        }
        requirements.add(step++ + ". 生成 harness_config.yaml manifest（只声明实际包含的组件类型）");
        requirements.add(step + ". 确保所有模块可正常 import，并从 harness_config.yaml 读取实际声明。");
        return "实现运行时扩展: " + nullToEmpty(effective.getExtensionName()) + "\n\n"
                + "Gap ID: " + nullToEmpty(effective.getGapId()) + "\n"
                + "组件: " + String.join(", ", components) + "\n"
                + "文件规划:\n" + filePlanText + "\n\n"
                + "扩展根目录: " + extensionRoot + "\n"
                + "Manifest 路径: " + configPath + "\n\n"
                + "要求:\n" + String.join("\n", requirements) + "\n\n"
                + "直接开始实现，不要等待确认。\n"
                + "严禁执行 git add、git commit 或其他提交动作。";
    }

    static Object readFieldOrGetter(Object target, String name) {
        if (target == null) {
            return "";
        }
        if (target instanceof Map<?, ?> map) {
            Object value = map.get(name);
            return value == null ? "" : value;
        }
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            // Try bean getter below.
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String joinOrDefault(List<String> values, String defaultValue) {
        return values == null || values.isEmpty() ? defaultValue : String.join(", ", values);
    }

    private static int countLines(String value) {
        if (value.isEmpty()) {
            return 1;
        }
        return value.split("\n", -1).length;
    }
}
