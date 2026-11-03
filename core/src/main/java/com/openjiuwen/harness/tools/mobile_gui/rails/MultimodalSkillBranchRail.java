/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.mobile_gui.rails;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.tools.mobile_gui.MobileGuiRuntimeSettings;
import com.openjiuwen.harness.tools.mobile_gui.SkillConsultMode;
import com.openjiuwen.harness.tools.mobile_gui.skill_branch.SkillBranchFormat;
import com.openjiuwen.harness.tools.mobile_gui.skill_branch.SkillBranchManifest;
import com.openjiuwen.harness.tools.mobile_gui.skill_branch.SkillImageEntry;

import java.io.IOException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Branches skill consultation through a dedicated multimodal planner.
 *
 * <p>Mirrors Python's {@code MultimodalSkillBranchRail} in
 * {@code openjiuwen/harness/tools/mobile_gui/rails/multimodal_skill_branch_rail.py}.</p>
 */
public class MultimodalSkillBranchRail extends DeepAgentRail {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static final String BRANCH_STATE_KEY = "_multimodal_skill_branch";
    public static final String CONSULT_COUNTS_KEY = "skill_branch_consult_counts";

    private final MobileGuiRuntimeSettings settings;
    private final BranchConsultant branchConsultant;

    public MultimodalSkillBranchRail(MobileGuiRuntimeSettings settings) {
        this(settings, request -> null);
    }

    public MultimodalSkillBranchRail(MobileGuiRuntimeSettings settings, BranchConsultant branchConsultant) {
        this.settings = settings == null ? MobileGuiRuntimeSettings.fromEnv() : settings;
        this.branchConsultant = branchConsultant;
    }

    @Override
    public void beforeToolCall(CallbackContext ctx) {
        if (ctx == null) {
            return;
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("max_images", settings.getSkillBranchMaxImages());
        state.put("max_consults_per_skill", settings.getSkillBranchMaxConsultsPerSkill());
        state.put("previous_steps_turns", settings.getSkillBranchPreviousStepsTurns());
        ctx.put(BRANCH_STATE_KEY, state);
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        if (ctx == null || settings.getSkillConsultMode() != SkillConsultMode.BRANCH) {
            return;
        }
        if (!"skill_tool".equals(stringValue(ctx.get("tool_name")))) {
            return;
        }

        Object toolResult = ctx.get("tool_result");
        if (toolResult instanceof ToolOutput output && !output.isSuccess()) {
            return;
        }
        if (toolResult instanceof Map<?, ?> raw && Boolean.FALSE.equals(raw.get("success"))) {
            return;
        }

        SkillPayload payload = extractSkillPayload(toolResult, ctx.get("tool_args"));
        if (payload.skillName() == null || payload.skillText() == null || payload.skillDirectory() == null) {
            return;
        }

        List<SkillImageEntry> manifest = SkillBranchManifest.buildSkillImageManifest(
                payload.skillText(),
                payload.skillDirectory()
        );
        if (manifest.isEmpty()) {
            return;
        }

        if (isExhausted(ctx, payload.skillName())) {
            rewriteToolMessage(
                    ctx,
                    "Skill consult: " + payload.skillName() + "\n"
                            + "Consult limit reached (" + settings.getSkillBranchMaxConsultsPerSkill()
                            + " per skill). Act from the current screenshot and prior planner memos."
            );
            return;
        }

        if (branchConsultant == null) {
            return;
        }
        BranchDecision decision = branchConsultant.consult(new BranchRequest(
                payload.skillName(),
                payload.skillText(),
                payload.skillDirectory(),
                stringValue(extraValue(ctx, "pinned_user_goal")),
                stringValue(extraValue(ctx, "vlm_grounding_base64")),
                manifest,
                settings.getSkillBranchMaxImages()
        ));
        if (decision == null) {
            return;
        }

        recordConsult(ctx, payload.skillName());
        if (decision.success() && decision.planner() != null && !decision.planner().isEmpty()) {
            String stage1Note = "";
            if (decision.stage1Decision() != null) {
                stage1Note = stringValue(decision.stage1Decision().get("why_not_text_only"));
            }
            rewriteToolMessage(ctx, SkillBranchFormat.formatPlannerToolMessage(
                    payload.skillName(),
                    decision.planner(),
                    stage1Note
            ));
            return;
        }

        rewriteToolMessage(ctx, SkillBranchFormat.formatBranchFailureToolMessage(
                payload.skillName(),
                decision.error() == null || decision.error().isBlank() ? "Skill branch failed." : decision.error(),
                payload.skillText(),
                800
        ));
    }

    @FunctionalInterface
    public interface BranchConsultant {
        BranchDecision consult(BranchRequest request);
    }

    public record BranchRequest(
            String skillName,
            String skillText,
            String skillDirectory,
            String instruction,
            String liveScreenshotBase64,
            List<SkillImageEntry> manifest,
            int maxImages
    ) {
    }

    public record BranchDecision(
            boolean success,
            Map<String, String> planner,
            String error,
            Map<String, Object> stage1Decision,
            List<String> selectedImageIds
    ) {
        public static BranchDecision success(Map<String, String> planner, List<String> selectedImageIds) {
            return new BranchDecision(true, planner, null, Map.of(), selectedImageIds == null
                    ? List.of()
                    : List.copyOf(selectedImageIds));
        }

        public static BranchDecision failure(String error) {
            return new BranchDecision(false, Map.of(), error, Map.of(), List.of());
        }
    }

    private static SkillPayload extractSkillPayload(Object toolResult, Object toolArgs) {
        String skillName = stringValue(parseToolArgs(toolArgs).get("skill_name"));
        Map<?, ?> data = null;
        if (toolResult instanceof ToolOutput output && output.getData() instanceof Map<?, ?> outputData) {
            data = outputData;
        } else if (toolResult instanceof Map<?, ?> raw && raw.get("data") instanceof Map<?, ?> rawData) {
            data = rawData;
        }
        if (data == null) {
            return new SkillPayload(blankToNull(skillName), null, null);
        }
        return new SkillPayload(
                blankToNull(skillName),
                objectToString(data.get("skill_content")),
                objectToString(data.get("skill_directory"))
        );
    }

    private static Map<String, Object> parseToolArgs(Object toolArgs) {
        if (toolArgs instanceof Map<?, ?> raw) {
            Map<String, Object> parsed = new LinkedHashMap<>();
            raw.forEach((key, value) -> parsed.put(String.valueOf(key), value));
            return parsed;
        }
        if (toolArgs instanceof String raw && !raw.isBlank()) {
            try {
                return OBJECT_MAPPER.readValue(raw, MAP_TYPE);
            } catch (IOException ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extra(CallbackContext ctx) {
        Object raw = ctx.get("extra");
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return ctx.getValues();
    }

    private static Object extraValue(CallbackContext ctx, String key) {
        return extra(ctx).get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> consultCounts(CallbackContext ctx) {
        Map<String, Object> extra = extra(ctx);
        Object raw = extra.get(CONSULT_COUNTS_KEY);
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Integer>) map;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        extra.put(CONSULT_COUNTS_KEY, counts);
        return counts;
    }

    private boolean isExhausted(CallbackContext ctx, String skillName) {
        return consultCounts(ctx).getOrDefault(skillName, 0) >= settings.getSkillBranchMaxConsultsPerSkill();
    }

    private void recordConsult(CallbackContext ctx, String skillName) {
        Map<String, Integer> counts = consultCounts(ctx);
        counts.put(skillName, counts.getOrDefault(skillName, 0) + 1);
    }

    private static void rewriteToolMessage(CallbackContext ctx, String content) {
        Object rawMessage = ctx.get("tool_msg");
        if (rawMessage instanceof ToolMessage toolMessage) {
            toolMessage.setContent(content);
            ctx.put("tool_msg", toolMessage);
        } else if (rawMessage == null) {
            ctx.put("tool_msg", new ToolMessage(content, stringValue(ctx.get("tool_call_id")), "skill_tool"));
        }
        ctx.put("tool_result", ToolOutput.success(Map.of("planner_memo", content)));
    }

    private static String objectToString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record SkillPayload(String skillName, String skillText, String skillDirectory) {
    }
}
