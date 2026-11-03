/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.stages;

import com.openjiuwen.auto_harness.infra.Parsers;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.GapAnalysisArtifact;
import com.openjiuwen.auto_harness.stages.ExtendPlanStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.auto_harness.stages.test_plan_ext} in
 * {@code tests/unit_tests/auto_harness/stages/test_plan_ext.py}.
 */
class PlanExtPythonParityTest {

    @Test
    void designQuerySelectsComponentsByGapSemantics() {
        String query = ExtendPlanStage.buildDesignQuery(
                GapAnalysisArtifact.builder()
                        .gaps(List.of(Gap.builder()
                                .id("gap_1")
                                .feature("conversation_budget_report")
                                .gapDescription("每 5 次工具执行自动报告进度和 token 花销")
                                .impact(0.9)
                                .feasibility(0.8)
                                .build()))
                        .build(),
                10,
                ""
        );

        assertThat(query).contains("按用户目标选择最轻组件组合");
        assertThat(query).contains("周期触发");
        assertThat(query).contains("不要强制包含 rail");
        assertThat(query).contains("最多输出 10 个 ExtensionDesign");
    }

    @Test
    void designQueryUsesConfiguredExtensionLimit() {
        String query = ExtendPlanStage.buildDesignQuery(
                GapAnalysisArtifact.builder().gaps(List.of()).build(),
                3,
                ""
        );

        assertThat(query).contains("最多输出 3 个 ExtensionDesign");
    }

    @Test
    void designQueryPreservesDomainArtifactsForPptExtensions() {
        String query = ExtendPlanStage.buildDesignQuery(
                GapAnalysisArtifact.builder()
                        .gaps(List.of(Gap.builder()
                                .id("gap_1")
                                .competitor("用户需求")
                                .feature("huawei_ppt_generator")
                                .gapDescription("生成华为风格 PPT，包含模板规范和文件生成能力")
                                .impact(0.9)
                                .feasibility(0.8)
                                .build()))
                        .build(),
                10,
                ""
        );

        assertThat(query).contains("huawei_ppt_generator");
        assertThat(query).contains("`office_ppt_generator`");
        assertThat(query).contains("Tool");
        assertThat(query).contains("Skill");
        assertThat(query).contains("skill-creator");
        assertThat(query).contains("assets/");
        assertThat(query).contains("references/");
        assertThat(query).contains("真实产物契约");
        assertThat(query).contains("PPTX/DOCX");
        assertThat(query).contains("JSON/Markdown/纯文本");
        assertThat(query).contains("ppt/presentation.xml");
        assertThat(query).contains("不要使用 `user_demand_*`");
        assertThat(query).contains("需求收集");
    }

    @Test
    void fallbackDesignInfersToolSkillForPptGeneration() {
        ExtensionDesign design = ExtendPlanStage.buildDesign(Gap.builder()
                .id("gap_1")
                .competitor("用户需求")
                .feature("huawei_ppt_generator")
                .gapDescription("生成华为风格 PPT，包含模板规范和文件生成能力")
                .suggestedApproach("创建 PPT 生成 Tool 和华为风格 Skill")
                .build());

        assertThat(design.getExtensionName()).isEqualTo("huawei_ppt_generator");
        assertThat(design.getKind()).isEqualTo("capability");
        assertThat(design.getComponents()).containsExactly("tool", "skill");
        Map<?, ?> resources = (Map<?, ?>) design.getHarnessConfigPatch().get("resources");
        assertThat(resources.containsKey("tools")).isTrue();
        assertThat(resources.containsKey("skills")).isTrue();
        assertThat(resources.containsKey("rails")).isFalse();
    }

    @Test
    void parseExtensionDesignsPreservesExecutionFields() {
        Parsers.ExtensionDesignParseResult result = Parsers.parseExtensionDesigns("""
                ```json
                [
                  {
                    "gap_id": "gap_guard",
                    "extension_name": "huawei_filename_guard",
                    "kind": "constraint",
                    "depends_on": [],
                    "applies_to": ["huawei_ppt_generator"],
                    "components": ["rail"]
                  },
                  {
                    "gap_id": "gap_ppt",
                    "extension_name": "huawei_ppt_generator",
                    "depends_on": ["huawei_filename_guard"],
                    "components": ["tool", "skill"]
                  }
                ]
                ```
                """);

        assertThat(result.packageName()).isNull();
        assertThat(result.designs()).extracting(ExtensionDesign::getExtensionName)
                .containsExactly("huawei_filename_guard", "huawei_ppt_generator");
        assertThat(result.designs().get(0).getKind()).isEqualTo("constraint");
        assertThat(result.designs().get(0).getAppliesTo()).containsExactly("huawei_ppt_generator");
        assertThat(result.designs().get(1).getKind()).isEqualTo("capability");
        assertThat(result.designs().get(1).getDependsOn()).containsExactly("huawei_filename_guard");
    }

    @Test
    void parseExtensionDesignsNewFormatWithPackageName() {
        Parsers.ExtensionDesignParseResult result = Parsers.parseExtensionDesigns("""
                ```json
                {
                  "package_name": "huawei_office_generator",
                  "designs": [
                    {
                      "gap_id": "gap_guard",
                      "extension_name": "huawei_filename_guard",
                      "kind": "constraint",
                      "depends_on": [],
                      "applies_to": ["huawei_ppt_generator"],
                      "components": ["rail"]
                    },
                    {
                      "gap_id": "gap_ppt",
                      "extension_name": "huawei_ppt_generator",
                      "depends_on": ["huawei_filename_guard"],
                      "components": ["tool", "skill"]
                    }
                  ]
                }
                ```
                """);

        assertThat(result.packageName()).isEqualTo("huawei_office_generator");
        assertThat(result.designs()).extracting(ExtensionDesign::getExtensionName)
                .containsExactly("huawei_filename_guard", "huawei_ppt_generator");
        assertThat(result.designs().get(0).getKind()).isEqualTo("constraint");
        assertThat(result.designs().get(1).getKind()).isEqualTo("capability");
    }

    @Test
    void fallbackDesignsKeepConstraintsOutsideCapabilityCap() {
        List<ExtensionDesign> designs = ExtendPlanStage.buildFallbackDesigns(
                List.of(
                        Gap.builder()
                                .id("guard")
                                .feature("huawei_filename_guard")
                                .gapDescription("所有文件写入前必须强制检查文件名后缀")
                                .impact(0.4)
                                .feasibility(0.4)
                                .build(),
                        Gap.builder()
                                .id("ppt")
                                .feature("huawei_ppt_generator")
                                .gapDescription("生成华为风格 PPT")
                                .impact(0.9)
                                .feasibility(0.9)
                                .build(),
                        Gap.builder()
                                .id("excel")
                                .feature("finance_excel_processor")
                                .gapDescription("处理财务 Excel")
                                .impact(0.8)
                                .feasibility(0.8)
                                .build()),
                1
        );

        assertThat(designs).extracting(ExtensionDesign::getExtensionName)
                .containsExactly("huawei_filename_guard", "huawei_ppt_generator");
        assertThat(designs.get(0).getKind()).isEqualTo("constraint");
        assertThat(designs.get(0).getComponents()).contains("rail");
    }

    @Test
    void capExtensionDesignsLimitsTotalDesignsWithConstraintsFirst() {
        List<ExtensionDesign> capped = ExtendPlanStage.capExtensionDesigns(
                List.of(
                        ExtendPlanStage.buildDesign(Gap.builder()
                                .id("ppt")
                                .feature("huawei_ppt_generator")
                                .gapDescription("生成华为风格 PPT")
                                .impact(0.9)
                                .feasibility(0.9)
                                .build()),
                        ExtendPlanStage.buildDesign(Gap.builder()
                                .id("guard")
                                .feature("huawei_filename_guard")
                                .gapDescription("所有文件写入前必须强制检查文件名后缀")
                                .impact(0.4)
                                .feasibility(0.4)
                                .build()),
                        ExtendPlanStage.buildDesign(Gap.builder()
                                .id("excel")
                                .feature("finance_excel_processor")
                                .gapDescription("处理财务 Excel")
                                .impact(0.8)
                                .feasibility(0.8)
                                .build())),
                2
        );

        assertThat(capped).extracting(ExtensionDesign::getExtensionName)
                .containsExactly("huawei_filename_guard", "huawei_ppt_generator");
    }
}
