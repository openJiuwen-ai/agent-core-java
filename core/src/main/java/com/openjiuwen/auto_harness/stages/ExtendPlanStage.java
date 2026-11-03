/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.agents.AutoHarnessAgentFactory;
import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;
import com.openjiuwen.auto_harness.contexts.SessionContext;
import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.infra.SkillSourceManager;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.AutoHarnessConfig;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesignArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.StageResult;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Convert gaps into concrete extension designs.
 *
 * <p>Mirrors Python's {@code ExtendPlanStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
 */
public class ExtendPlanStage extends PlanStage {

    private static final Logger LOGGER = Logger.getLogger(ExtendPlanStage.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern UNSAFE_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");

    public static final DesignAgentFactory DEFAULT_AGENT_FACTORY = (config, extraRails) -> {
        DeepAgent agent = AutoHarnessAgentFactory.createDesignExtAgent(
                config,
                MetaPlanStage.deepAgentRails(extraRails)
        );
        return agent::stream;
    };

    private final DesignAgentFactory agentFactory;

    public ExtendPlanStage() {
        this(DEFAULT_AGENT_FACTORY);
    }

    public ExtendPlanStage(DesignAgentFactory agentFactory) {
        this.agentFactory = agentFactory == null ? DEFAULT_AGENT_FACTORY : agentFactory;
    }

    @Override
    public String name() {
        return "plan_ext";
    }

    @Override
    public String displayName() {
        return "设计扩展方案";
    }

    @Override
    public String description() {
        return "Design runtime extensions from analyzed gaps.";
    }

    @Override
    public List<String> consumes() {
        return List.of("gap_analysis");
    }

    @Override
    public List<String> produces() {
        return List.of("extension_design");
    }

    @Override
    public Iterator<Object> stream(BaseExecutionContext ctx) {
        if (!(ctx instanceof SessionContext sessionContext)) {
            throw new IllegalArgumentException("ExtendPlanStage requires a SessionContext");
        }
        Object artifactObject = sessionContext.requireArtifact("gap_analysis");
        if (!(artifactObject instanceof GapAnalysisArtifact analysis)) {
            throw new IllegalArgumentException("gap_analysis artifact must be GapAnalysisArtifact");
        }

        List<Object> events = new ArrayList<>();
        List<ExtensionDesign> designs = new ArrayList<>();
        String packageNameRaw = null;
        AutoHarnessConfig config = sessionContext.getOrchestrator().getConfig();

        if (analysis.getGaps() != null && !analysis.getGaps().isEmpty()) {
            StringBuilder output = new StringBuilder();
            try {
                String communitySkillList = SkillSourceManager.formatCommunitySkillList(config);
                DesignAgent agent = agentFactory.create(config, sessionContext.getOrchestrator().getStreamRails());
                String query = buildDesignQuery(
                        analysis,
                        config.getMaxTasksPerSession(),
                        communitySkillList
                );
                Iterator<?> stream = agent.stream(Map.of("query", query));
                while (stream.hasNext()) {
                    Object chunk = stream.next();
                    String text = Parsers.extractText(chunk);
                    if (!text.isEmpty()) {
                        output.append(text);
                    }
                    events.add(chunk);
                }
                Parsers.ExtensionDesignParseResult parsed = Parsers.parseExtensionDesigns(output.toString());
                packageNameRaw = parsed.packageName();
                designs = new ArrayList<>(parsed.designs());
            } catch (RuntimeException exception) {
                LOGGER.log(Level.WARNING, "Agent extension design failed", exception);
            }
        }

        if (designs.isEmpty()) {
            LOGGER.warning("Agent design returned no results, falling back to heuristic");
            designs = buildFallbackDesigns(
                    analysis.getGaps() == null ? List.of() : analysis.getGaps(),
                    config.getMaxTasksPerSession()
            );
        }

        designs = capExtensionDesigns(designs, config.getMaxTasksPerSession());
        String packageName = "";
        if (!isBlank(packageNameRaw)) {
            packageName = packageNameRaw.strip() + "_" + Instant.now().getEpochSecond();
            LOGGER.info("[ExtendPlanStage] using parsed package_name: " + packageName);
        }

        ExtensionDesignArtifact artifact = ExtensionDesignArtifact.builder()
                .designs(designs)
                .packageName(packageName)
                .build();
        List<String> messages = new ArrayList<>();
        messages.add("Extension design complete: " + designs.size() + " design(s)");
        if (!designs.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ExtensionDesign design : designs) {
                names.add(design.getExtensionName());
            }
            messages.add("Designs: " + String.join(", ", names));
            Path path = persistExtensionDesigns(
                    sessionContext.getOrchestrator().getPaths().getRunsDir(),
                    artifact
            );
            messages.add("扩展设计已保存: " + path);
        }

        Map<String, Object> artifacts = new LinkedHashMap<>();
        artifacts.put("extension_design", artifact);
        events.add(StageResult.builder()
                .artifacts(artifacts)
                .messages(messages)
                .build());
        return events.iterator();
    }

    public static Path persistExtensionDesigns(String runsDir, ExtensionDesignArtifact artifact) {
        Path runsPath = Path.of(runsDir == null || runsDir.isBlank() ? "." : runsDir);
        try {
            Files.createDirectories(runsPath);
            Path latestPath = runsPath.resolve("latest_extension_design.json");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("designs", artifact == null ? List.of() : artifact.getDesigns());
            payload.put("package_name", artifact == null ? "" : artifact.getPackageName());
            String content = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(latestPath, content, StandardCharsets.UTF_8);
            long modifiedMillis = Files.getLastModifiedTime(latestPath).toMillis();
            Path stampedPath = runsPath.resolve("extension_design_" + modifiedMillis + ".json");
            Files.writeString(stampedPath, content, StandardCharsets.UTF_8);
            return latestPath;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize extension designs", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to persist extension designs", e);
        }
    }

    public static String buildDesignQuery(
            GapAnalysisArtifact analysis,
            int maxDesigns,
            String communitySkillList
    ) {
        List<String> gapLines = new ArrayList<>();
        for (Gap gap : analysis == null || analysis.getGaps() == null ? List.<Gap>of() : analysis.getGaps()) {
            String sourceTag = isBlank(gap.getCompetitor())
                    ? ""
                    : " (source/reference: " + gap.getCompetitor() + ")";
            gapLines.add("- [" + nullToEmpty(gap.getId()) + "] " + nullToEmpty(gap.getFeature())
                    + sourceTag + ": " + nullToEmpty(gap.getGapDescription())
                    + " (impact=" + gap.getImpact()
                    + ", feasibility=" + gap.getFeasibility() + ")");
        }
        String gapSummary = String.join("\n", gapLines);
        StringBuilder query = new StringBuilder();
        query.append("根据以下 runtime extension 能力缺口分析结果，")
                .append("为每个独立 gap 设计一个 harness 运行时扩展方案，")
                .append("最多输出 ").append(Math.max(0, maxDesigns)).append(" 个 ExtensionDesign。\n\n")
                .append("组件选择规则：按用户目标选择最轻组件组合。")
                .append("Tool 用于生成文件、调用 API、封装 CLI 或执行明确动作；")
                .append("Skill 用于承载领域规范、模板原则、生成流程和示例；")
                .append("设计 Skill 时必须参考 skill-creator 原则，规划准确的")
                .append("name/description、精简可操作的 SKILL.md，并在需要模板、")
                .append("品牌素材或详细参考资料时规划 assets/ 或 references/；")
                .append("Rail 只在需要拦截会话、后台监听、周期触发、审计或")
                .append("动态注入上下文时使用，不要为了完整性强行添加 Rail。\n")
                .append("办公/PPT/报告/文件生成类扩展通常优先设计为 `[\"tool\", \"skill\"]`，")
                .append("除非 gap 明确需要生命周期拦截或后台触发。\n\n")
                .append("真实产物契约：如果扩展承诺生成 PPT、DOCX、PDF、JSON、图片、报告或其他文件，")
                .append("设计必须明确 Tool 的输入、输出路径、返回字段和成功条件，")
                .append("成功条件至少包含：目标文件存在、文件后缀/格式正确、size_bytes > 0、")
                .append("返回 absolute_path/exists/format/size_bytes 等结构化字段。")
                .append("PPTX/DOCX 必须是合法 zip 包并包含关键内部结构；PPTX 至少包含 ")
                .append("`ppt/presentation.xml` 和 `ppt/slides/slide*.xml`；")
                .append("不得设计 JSON/Markdown/纯文本占位来冒充二进制产物。\n\n")
                .append("设计拆分标准：每个可独立实现和验证的 gap 输出一个 design。")
                .append("全局硬约束必须输出为独立 constraint design，普通新增能力输出为 capability design。")
                .append("必须保留用户目标中的关键实体和产物类型，例如“PPT”“办公拓展”，")
                .append("不要泛化成需求收集、需求报告或普通办公扩展。\n\n")
                .append("差距列表:\n").append(gapSummary).append("\n\n")
                .append("命名规则：extension_name 必须是能表达用户能力的 snake_case 名称。")
                .append("若 gap 来自明确竞品，可使用竞品名前缀；若来源是用户需求或领域范式，")
                .append("应按能力命名，例如 `excel_financial_generator`、`office_ppt_generator`。")
                .append("不要使用 `user_demand_*` 这类丢失具体产物和场景的泛名。\n\n")
                .append("首先输出 session 级别的扩展包名称：\n")
                .append("- package_name: snake_case，表达本轮优化的核心能力\n")
                .append("- 保留用户目标关键实体\n")
                .append("- 不超过 30 字符（不含 timestamp 后缀）\n")
                .append("- 不要泛化名称（如 office_tools、user_demand）\n\n")
                .append("输出 JSON 对象格式：\n")
                .append("{\"package_name\": \"...\", \"designs\": [...]}\n\n")
                .append("designs 数组元素包含:\n")
                .append("- gap_id\n")
                .append("- extension_name\n")
                .append("- kind: capability 或 constraint；默认 capability。")
                .append("全局硬约束、写入前强制检查、所有文件命名约束必须使用 constraint\n")
                .append("- depends_on\n")
                .append("- applies_to\n")
                .append("- components: 组件列表 (按需选择 rail/tool/skill；不要强制包含 rail)\n")
                .append("- skill_source\n")
                .append("- file_plan\n")
                .append("- harness_config_patch\n");
        if (!isBlank(communitySkillList)) {
            query.append("\n\n").append(communitySkillList).append("\n");
        }
        return query.toString();
    }

    public static List<ExtensionDesign> capExtensionDesigns(List<ExtensionDesign> designs, int maxDesigns) {
        int limit = Math.max(0, maxDesigns);
        if (limit == 0) {
            return List.of();
        }
        List<ExtensionDesign> constraints = new ArrayList<>();
        List<ExtensionDesign> capabilities = new ArrayList<>();
        for (ExtensionDesign design : designs == null ? List.<ExtensionDesign>of() : designs) {
            if ("constraint".equals(design.getKind())) {
                constraints.add(design);
            } else {
                capabilities.add(design);
            }
        }
        List<ExtensionDesign> ordered = new ArrayList<>(constraints);
        ordered.addAll(capabilities);
        return new ArrayList<>(ordered.subList(0, Math.min(limit, ordered.size())));
    }

    public static String slugify(String value) {
        String slug = UNSAFE_SLUG_CHARS.matcher(nullToEmpty(value).toLowerCase(Locale.ROOT))
                .replaceAll("_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "runtime_extension" : slug;
    }

    public static List<String> inferExtensionComponents(Gap gap) {
        String text = String.join(
                " ",
                nullToEmpty(gap == null ? "" : gap.getFeature()),
                nullToEmpty(gap == null ? "" : gap.getGapDescription()),
                nullToEmpty(gap == null ? "" : gap.getSuggestedApproach())
        ).toLowerCase(Locale.ROOT);
        List<String> components = new ArrayList<>();
        List<String> actionKeywords = List.of(
                "ppt", "powerpoint", "报告", "文档", "文件", "生成", "导出", "api", "cli", "tool"
        );
        List<String> skillKeywords = List.of(
                "风格", "模板", "规范", "领域", "指南", "skill", "最佳实践"
        );
        List<String> railKeywords = List.of(
                "拦截", "监听", "后台", "周期", "每 n 次", "每n次", "审计", "提醒", "累计", "rail"
        );
        if (containsAny(text, railKeywords)) {
            components.add("rail");
        }
        if (containsAny(text, actionKeywords)) {
            components.add("tool");
        }
        if (containsAny(text, skillKeywords)) {
            components.add("skill");
        }
        if (components.isEmpty()) {
            components.add("tool");
        }
        return components;
    }

    public static String inferExtensionKind(Gap gap) {
        String text = String.join(
                " ",
                nullToEmpty(gap == null ? "" : gap.getFeature()),
                nullToEmpty(gap == null ? "" : gap.getGapDescription()),
                nullToEmpty(gap == null ? "" : gap.getSuggestedApproach())
        ).toLowerCase(Locale.ROOT);
        List<String> constraintSignals = List.of(
                "constraint", "guard", "硬约束", "硬性约束", "强制", "必须", "不得", "禁止", "所有文件", "写入前", "文件名", "后缀"
        );
        List<String> enforcementSignals = List.of(
                "检查", "校验", "拦截", "阻止", "命名", "文件名", "后缀"
        );
        if (containsAny(text, constraintSignals) && containsAny(text, enforcementSignals)) {
            return "constraint";
        }
        return "capability";
    }

    public static String sourceNamePrefix(String source) {
        String normalized = nullToEmpty(source).strip().toLowerCase(Locale.ROOT);
        List<String> genericSources = List.of(
                "用户需求",
                "领域范式",
                "办公自动化",
                "ppt生成工具",
                "报告生成流程",
                "user demand",
                "user_requirement",
                "domain_pattern"
        );
        if (normalized.isEmpty() || containsAny(normalized, genericSources)) {
            return "";
        }
        String slug = slugify(source);
        return "runtime_extension".equals(slug) ? "" : slug;
    }

    public static ExtensionDesign buildDesign(Gap gap) {
        String featureSlug = slugify(gap == null ? "" : gap.getFeature());
        String competitorSlug = sourceNamePrefix(gap == null ? "" : gap.getCompetitor());
        String extensionName;
        if (!competitorSlug.isEmpty() && !featureSlug.isEmpty()) {
            extensionName = competitorSlug + "_" + featureSlug;
        } else if (!featureSlug.isEmpty()) {
            extensionName = featureSlug;
        } else {
            extensionName = slugify(gap == null ? "runtime_extension" : gap.getId());
        }

        String moduleBase = "openjiuwen.extensions.harness." + extensionName;
        String kind = inferExtensionKind(gap);
        List<String> components = new ArrayList<>(inferExtensionComponents(gap));
        if ("constraint".equals(kind) && !components.contains("rail")) {
            components.add(0, "rail");
        }

        Map<String, Object> resources = new LinkedHashMap<>();
        if (components.contains("rail")) {
            resources.put("rails", List.of(Map.of(
                    "type", "package",
                    "module", moduleBase + ".rails.extension_rail",
                    "class", "ExtensionRail"
            )));
        }
        if (components.contains("tool")) {
            resources.put("tools", List.of(Map.of(
                    "type", "package",
                    "module", moduleBase + ".tools.extension_tool",
                    "class", "ExtensionTool"
            )));
        }
        if (components.contains("skill")) {
            resources.put("skills", Map.of("dirs", List.of("skills/")));
        }

        Map<String, String> filePlan = new LinkedHashMap<>();
        filePlan.put("root", "openjiuwen/extensions/harness/" + extensionName);
        filePlan.put("manifest", "openjiuwen/extensions/harness/" + extensionName + "/harness_config.yaml");

        Map<String, Object> harnessConfigPatch = new LinkedHashMap<>();
        harnessConfigPatch.put("resources", resources);

        return ExtensionDesign.builder()
                .gapId(gap == null ? "" : gap.getId())
                .extensionName(extensionName)
                .kind(kind)
                .components(components)
                .filePlan(filePlan)
                .harnessConfigPatch(harnessConfigPatch)
                .build();
    }

    public static List<ExtensionDesign> buildFallbackDesigns(List<Gap> gaps, int maxCapabilities) {
        List<Gap> sortedGaps = new ArrayList<>(gaps == null ? List.of() : gaps);
        sortedGaps.sort(Comparator.comparingDouble(Gap::getPriority).reversed());
        List<ExtensionDesign> designs = new ArrayList<>();
        for (Gap gap : sortedGaps) {
            designs.add(buildDesign(gap));
        }
        List<ExtensionDesign> constraints = new ArrayList<>();
        List<ExtensionDesign> capabilities = new ArrayList<>();
        for (ExtensionDesign design : designs) {
            if ("constraint".equals(design.getKind())) {
                constraints.add(design);
            } else {
                capabilities.add(design);
            }
        }
        int capLimit = Math.max(0, maxCapabilities);
        List<ExtensionDesign> result = new ArrayList<>(constraints);
        result.addAll(capabilities.subList(0, Math.min(capLimit, capabilities.size())));
        return result;
    }

    private static boolean containsAny(String text, List<String> needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * Streaming surface used by the design_ext agent.
     *
     * <p>Mirrors Python's late import of {@code create_design_ext_agent} in
     * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
     */
    @FunctionalInterface
    public interface DesignAgent {
        Iterator<?> stream(Map<String, Object> inputs);
    }

    /**
     * Factory for the design_ext agent.
     *
     * <p>Mirrors Python's late import of {@code create_design_ext_agent} in
     * {@code openjiuwen/auto_harness/stages/plan.py}.</p>
     */
    @FunctionalInterface
    public interface DesignAgentFactory {
        DesignAgent create(AutoHarnessConfig config, List<?> extraRails);
    }
}
