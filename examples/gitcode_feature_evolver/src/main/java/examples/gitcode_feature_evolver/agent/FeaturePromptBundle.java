/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import examples.gitcode_feature_evolver.job.FeatureStage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Loads the exact trusted Skill files required for one feature stage into the Agent prompt.
 *
 * @since 0.1.12
 */
public final class FeaturePromptBundle {
    private static final int MAX_PROMPT_CHARS = 240_000;
    private static final Map<FeatureStage, List<String>> STAGE_FILES = Map.ofEntries(
            Map.entry(FeatureStage.SPECIFY, List.of(
                    "references/issue-contract.md", "references/workflow-state-machine.md",
                    "references/specification.md", "references/role-contracts.md",
                    "assets/spec-template.md", "assets/traceability-template.md", "assets/plan-template.md")),
            Map.entry(FeatureStage.REVIEW_R1, List.of(
                    "references/workflow-state-machine.md", "references/review-and-ship.md",
                    "references/role-contracts.md", "assets/review-template.md")),
            Map.entry(FeatureStage.DESIGN, List.of(
                    "references/workflow-state-machine.md", "references/design-and-plan.md",
                    "references/role-contracts.md", "assets/design-template.md", "assets/plan-template.md")),
            Map.entry(FeatureStage.REVIEW_R2, List.of(
                    "references/workflow-state-machine.md", "references/review-and-ship.md",
                    "references/role-contracts.md", "assets/review-template.md")),
            Map.entry(FeatureStage.IMPLEMENT_RED, List.of(
                    "references/workflow-state-machine.md", "references/tdd-and-quality.md",
                    "references/role-contracts.md", "assets/plan-template.md")),
            Map.entry(FeatureStage.IMPLEMENT_GREEN, List.of(
                    "references/workflow-state-machine.md", "references/tdd-and-quality.md",
                    "references/role-contracts.md", "assets/plan-template.md")),
            Map.entry(FeatureStage.IMPLEMENT_REFACTOR, List.of(
                    "references/workflow-state-machine.md", "references/tdd-and-quality.md",
                    "references/role-contracts.md", "assets/plan-template.md")),
            Map.entry(FeatureStage.REVIEW_R3, List.of(
                    "references/workflow-state-machine.md", "references/review-and-ship.md",
                    "references/role-contracts.md", "assets/review-template.md")),
            Map.entry(FeatureStage.SHIP, List.of(
                    "references/workflow-state-machine.md", "references/review-and-ship.md",
                    "references/role-contracts.md", "assets/closeout-template.md")),
            Map.entry(FeatureStage.SYSTEM_TEST, List.of(
                    "references/workflow-state-machine.md", "references/system-test.md",
                    "references/role-contracts.md", "assets/system-test-template.md")),
            Map.entry(FeatureStage.REVIEW_SYSTEM_TEST, List.of(
                    "references/workflow-state-machine.md", "references/system-test.md",
                    "references/review-and-ship.md", "references/role-contracts.md",
                    "assets/review-template.md")));
    private final Path featureRoot;
    private final Path codingStandard;

    /**
     * Bind to a staged trusted Skill root.
     *
     * @param stagedSkillsRoot directory containing both staged Skills
     */
    public FeaturePromptBundle(Path stagedSkillsRoot) {
        Path root = Objects.requireNonNull(stagedSkillsRoot, "stagedSkillsRoot must not be null")
                .toAbsolutePath().normalize();
        this.featureRoot = root.resolve("gitcode-feature-devflow").normalize();
        this.codingStandard = root.resolve("coding-standard/SKILL.md").normalize();
    }

    /**
     * Load the trusted primary prompt and stage references.
     *
     * @param stage assigned controller stage
     * @return complete immutable system prompt text
     */
    public String load(FeatureStage stage) {
        FeatureStage required = Objects.requireNonNull(stage, "stage must not be null");
        List<String> relativeFiles = STAGE_FILES.get(required);
        if (relativeFiles == null) {
            throw new IllegalArgumentException("Stage does not invoke an Agent: " + required);
        }
        List<Path> files = new ArrayList<>();
        files.add(featureRoot.resolve("SKILL.md"));
        relativeFiles.forEach(path -> files.add(featureRoot.resolve(path)));
        if (requiresCodingStandard(required)) {
            files.add(codingStandard);
        }
        StringBuilder prompt = new StringBuilder();
        for (Path file : files) {
            append(prompt, file);
        }
        if (prompt.length() > MAX_PROMPT_CHARS) {
            throw new IllegalStateException("Trusted feature prompt bundle exceeds the configured limit");
        }
        return prompt.toString();
    }

    private static boolean requiresCodingStandard(FeatureStage stage) {
        return stage == FeatureStage.IMPLEMENT_RED || stage == FeatureStage.IMPLEMENT_GREEN
                || stage == FeatureStage.IMPLEMENT_REFACTOR || stage == FeatureStage.REVIEW_R3
                || stage == FeatureStage.SYSTEM_TEST || stage == FeatureStage.REVIEW_SYSTEM_TEST;
    }

    private void append(StringBuilder prompt, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if ((!normalized.startsWith(featureRoot) && !normalized.equals(codingStandard))
                || !Files.isRegularFile(normalized)) {
            throw new IllegalStateException("Trusted prompt file is unavailable");
        }
        try {
            prompt.append("\n\n--- TRUSTED SKILL FILE: ")
                    .append(normalized.getFileName()).append(" ---\n")
                    .append(Files.readString(normalized, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load trusted feature prompt", ex);
        }
    }
}
