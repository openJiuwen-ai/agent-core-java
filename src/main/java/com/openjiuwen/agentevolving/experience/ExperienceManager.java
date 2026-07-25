/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agent_evolving.UpdateExecution;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.agentevolving.checkpointing.EvolutionLog;
import com.openjiuwen.agentevolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.trajectory.UpdateKey;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.skill_call.SkillExperienceOperator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Orchestrates the online lifecycle for skill and team-skill evolution.
 *
 * <p>Mirrors Python's {@code ExperienceManager} in
 * {@code openjiuwen/agent_evolving/experience/skill_experience_manager.py}.</p>
 */
public class ExperienceManager implements OnlineEvolutionOrchestrator.ExperienceManagerPort {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final Set<String> SUPPORTED_KINDS = Set.of("skill", "team-skill");

    private final ExperienceStore store;
    private final ExperienceScorer scorer;
    private final String kind;
    private final String language;
    private final Map<String, SkillExperienceOperator> skillOps;
    private Map<String, PendingChange> pendingApprovalSnapshots;
    private final Map<String, Map<String, Object>> pendingGovernance;

    public ExperienceManager(ExperienceStore store, ExperienceScorer scorer) {
        this(store, scorer, "skill", "cn", null, null, null);
    }

    public ExperienceManager(
            ExperienceStore store,
            ExperienceScorer scorer,
            String kind,
            String language,
            Map<String, SkillExperienceOperator> skillOps,
            Map<String, PendingChange> pendingApprovalSnapshots,
            Map<String, Map<String, Object>> pendingGovernance
    ) {
        if (!SUPPORTED_KINDS.contains(kind)) {
            throw new IllegalArgumentException("unsupported experience manager kind: " + kind);
        }
        this.store = store;
        this.scorer = scorer;
        this.kind = kind;
        this.language = language == null ? "cn" : language;
        this.skillOps = skillOps == null ? new LinkedHashMap<>() : new LinkedHashMap<>(skillOps);
        this.pendingGovernance = pendingGovernance == null ? new LinkedHashMap<>() : pendingGovernance;
        bindPendingApprovalSnapshots(pendingApprovalSnapshots);
    }

    public Map<String, PendingChange> getPendingApprovalSnapshots() {
        return pendingApprovalSnapshots;
    }

    public Map<String, Map<String, Object>> getPendingGovernance() {
        return pendingGovernance;
    }

    public Map<String, SkillExperienceOperator> getSkillOps() {
        return skillOps;
    }

    public void bindPendingApprovalSnapshots(Map<String, PendingChange> pendingApprovalSnapshots) {
        this.pendingApprovalSnapshots = pendingApprovalSnapshots == null ? new LinkedHashMap<>() : pendingApprovalSnapshots;
    }

    public ExperienceApprovalRequest stageRecords(String skillName, List<EvolutionRecord> records) {
        return stageRecords(
                skillName,
                records,
                true,
                "experience_optimizer",
                "",
                null,
                null,
                Protocols.SKILL_EXPERIENCE_ENTRY,
                null,
                null,
                null,
                false
        );
    }

    public ExperienceApprovalRequest stageRecords(
            String skillName,
            List<EvolutionRecord> records,
            boolean requiresApproval,
            String source,
            String userQuery,
            String signalType,
            String signalSource,
            String changeType,
            String requestIdPrefix,
            Object trajectory,
            List<Map<String, Object>> messages,
            boolean sharedRecords
    ) {
        ExperienceProposal proposal = new ExperienceProposal(
                skillName,
                records,
                requiresApproval,
                source,
                userQuery,
                signalType,
                signalSource
        );
        return stageRecords(
                proposal,
                records,
                changeType == null ? Protocols.SKILL_EXPERIENCE_ENTRY : changeType,
                requestIdPrefix,
                trajectory,
                messages,
                sharedRecords
        );
    }

    public ExperienceApprovalRequest stageApplyResults(String skillName, List<ApplyResult> applyResults) {
        return stageApplyResults(
                skillName,
                applyResults,
                true,
                "experience_updater",
                null,
                "",
                null,
                null,
                null
        );
    }

    public ExperienceApprovalRequest stageApplyResults(
            String skillName,
            List<ApplyResult> applyResults,
            boolean requiresApproval,
            String source,
            String requestIdPrefix,
            String userQuery,
            String signalType,
            String signalSource,
            List<Map<String, Object>> messages
    ) {
        LocalApplyPreview preview = buildLocalApplyPreview(skillName, applyResults);
        ExperienceProposal proposal = new ExperienceProposal(
                skillName,
                preview.getRecords(),
                requiresApproval,
                source,
                userQuery,
                signalType,
                signalSource
        );
        return stagePendingRequest(proposal, preview, requestIdPrefix, null, messages, false);
    }

    public CompletionStage<ExperienceApplyResult> approveRequest(String requestId) {
        return approveRequest(requestId, null);
    }

    public CompletionStage<ExperienceApplyResult> approveRequest(String requestId, List<String> approvedRecordIds) {
        return applyRequest(requestId, Protocols.APPROVE_ACTION, approvedRecordIds);
    }

    public CompletionStage<ExperienceApplyResult> rejectRequest(String requestId) {
        return applyRequest(requestId, Protocols.REJECT_ACTION, null);
    }

    public CompletionStage<ExperienceApplyResult> retryRequest(String requestId) {
        return applyRequest(requestId, Protocols.RETRY_ACTION, null);
    }

    public CompletionStage<ExperienceApplyResult> commitProposal(ExperienceProposal proposal) {
        ExperienceApprovalRequest request = stageRecords(
                proposal.getSkillName(),
                proposal.getRecords(),
                proposal.isRequiresApproval(),
                proposal.getSource(),
                proposal.getUserQuery(),
                proposal.getSignalType(),
                proposal.getSignalSource(),
                Protocols.SKILL_EXPERIENCE_ENTRY,
                null,
                null,
                null,
                false
        );
        return commitStagedRequest(request);
    }

    public List<ApplyResult> previewApplyResults(
            String skillName,
            SkillExperienceOperator operator,
            UpdateValue update
    ) {
        return applyUpdates(
                Map.of(operator.getOperatorId(), operator),
                Map.of(UpdateKey.of(operator.getOperatorId(), Protocols.EXPERIENCES_TARGET), update)
        );
    }

    public static List<ApplyResult> applyUpdates(
            Map<String, ? extends Operator> operators,
            Map<UpdateKey, ?> updates
    ) {
        List<com.openjiuwen.agent_evolving.ApplyResult> rawResults = UpdateExecution.executeUpdates(operators, updates);
        List<ApplyResult> results = new ArrayList<>();
        for (com.openjiuwen.agent_evolving.ApplyResult r : rawResults) {
            results.add(ApplyResult.builder()
                    .operatorId(r.getOperatorId())
                    .target(r.getTarget())
                    .applied(r.isApplied())
                    .mode(r.getMode())
                    .effect(r.getEffect())
                    .value(r.getValue())
                    .records(r.getRecords())
                    .changeType(r.getChangeType())
                    .lifecycleStage(r.getLifecycleStage())
                    .pendingChangeId(r.getPendingChangeId())
                    .errors(r.getErrors())
                    .metadata(r.getMetadata())
                    .build());
        }
        return results;
    }

    @Override
    public LocalApplyPreview buildLocalApplyPreview(String skillName, List<ApplyResult> applyResults) {
        List<EvolutionRecord> records = new ArrayList<>();
        String changeType = Protocols.SKILL_EXPERIENCE_ENTRY;
        for (ApplyResult result : applyResults == null ? List.<ApplyResult>of() : applyResults) {
            if (!result.isApplied()) {
                continue;
            }
            String lifecycleStage = result.getLifecycleStage();
            if (lifecycleStage != null && !Protocols.LOCAL_APPLY_COMPLETED.equals(lifecycleStage)) {
                throw new IllegalArgumentException(
                        "unsupported apply lifecycle stage for " + skillName + ": " + lifecycleStage
                );
            }
            for (Object item : result.getRecords()) {
                if (!(item instanceof EvolutionRecord record)) {
                    throw new IllegalArgumentException("apply result record is not an EvolutionRecord: " + item);
                }
                records.add(record);
            }
            if (result.getChangeType() != null) {
                changeType = result.getChangeType();
            }
        }
        return new LocalApplyPreview(skillName, records, applyResults == null ? List.of() : applyResults, changeType,
                Protocols.LOCAL_APPLY_COMPLETED);
    }

    public PendingChange makePendingChangeFromPreview(
            LocalApplyPreview preview,
            String requestIdPrefix,
            Object trajectory,
            List<Map<String, Object>> messages,
            boolean sharedRecords
    ) {
        PendingChange pending = ExperienceCommon.makePendingChange(
                preview.getSkillName(),
                preview.getRecords(),
                requestIdPrefix,
                trajectory,
                messages,
                sharedRecords
        );
        pending.setChangeType(preview.getChangeType());
        return pending;
    }

    protected PendingChange stagePendingChange(PendingChange pending) {
        pendingApprovalSnapshots.put(pending.getChangeId(), pending);
        return pending;
    }

    protected PendingChange rejectPendingChange(String changeId) {
        PendingChange pending = pendingApprovalSnapshots.remove(changeId);
        if (pending == null) {
            throw new NoSuchElementException(changeId);
        }
        return pending;
    }

    public CompletionStage<String> requestSimplify(String skillName) {
        return requestSimplify(skillName, null);
    }

    public CompletionStage<String> requestSimplify(String skillName, String userIntent) {
        long startedAt = System.nanoTime();
        if (!store.skillExists(skillName)) {
            LOGGER.info("[ExperienceManager] request_simplify skipped: kind={} skill={} reason=skill_not_found",
                    kind, skillName);
            return CompletableFuture.completedFuture(null);
        }
        if (!store.skillDefinitionExists(skillName)) {
            LOGGER.info("[ExperienceManager] request_simplify skipped: kind={} skill={} reason=skill_definition_not_found",
                    kind, skillName);
            return CompletableFuture.completedFuture(null);
        }
        return store.loadFullEvolutionLog(skillName).thenCompose(evolutionLog -> {
            List<EvolutionRecord> records = evolutionLog == null ? List.of() : evolutionLog.getEntries();
            if (records.isEmpty()) {
                LOGGER.info("[ExperienceManager] request_simplify skipped: kind={} skill={} reason=no_records",
                        kind, skillName);
                return CompletableFuture.completedFuture(null);
            }
            return store.readSkillContent(skillName, true).thenCompose(content -> {
                String summary = store.extractDescriptionFromSkillMd(content);
                LOGGER.info("[ExperienceManager] request_simplify loaded records: kind={} skill={} records={}",
                        kind, skillName, records.size());
                return scorer.simplify(skillName, summary, records, userIntent).thenApply(actions -> {
                    if (actions == null || actions.isEmpty()) {
                        LOGGER.info(
                                "[ExperienceManager] request_simplify finished without actions: kind={} skill={} elapsed={}s",
                                kind,
                                skillName,
                                elapsedSeconds(startedAt)
                        );
                        return null;
                    }
                    String requestId = "evolve_simplify_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    Map<String, Object> governance = new LinkedHashMap<>();
                    governance.put("kind", "simplify");
                    governance.put("skill_name", skillName);
                    governance.put("actions", List.copyOf(actions));
                    pendingGovernance.put(requestId, governance);
                    LOGGER.info(
                            "[ExperienceManager] request_simplify staged: kind={} skill={} request={} actions={} elapsed={}s",
                            kind,
                            skillName,
                            requestId,
                            actions.size(),
                            elapsedSeconds(startedAt)
                    );
                    return requestId;
                });
            });
        });
    }

    @SuppressWarnings("unchecked")
    public CompletionStage<Map<String, Integer>> approveSimplify(String requestId) {
        Map<String, Object> governance = pendingGovernance.remove(requestId);
        if (governance == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        Object actions = governance.get("actions");
        return ExperienceCommon.executeSimplifyActions(
                store,
                String.valueOf(governance.get("skill_name")),
                actions instanceof List<?> list ? (List<Map<String, Object>>) list : List.of()
        );
    }

    public CompletionStage<Void> rejectSimplify(String requestId) {
        pendingGovernance.remove(requestId);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<String> requestRebuild(String skillName) {
        return requestRebuild(skillName, null, 0.5d);
    }

    public CompletionStage<String> requestRebuild(String skillName, String userIntent, double minScore) {
        return ExperienceCommon.requestRebuildContext(
                store,
                new RebuildRequest(skillName, userIntent, minScore, Map.of()),
                records -> formatEvolutionRecords(records, language),
                getDefaultRebuildIntent(),
                getRebuildTemplate(),
                true
        ).thenApply(context -> context == null ? null : context.getPrompt());
    }

    public static ExperienceApplyResult toApplyResult(String skillName, PendingCommitResult result) {
        return new ExperienceApplyResult(
                skillName,
                result.getAppliedCount(),
                result.getRejectedCount(),
                result.getPendingCount(),
                result.getErrors(),
                Map.of()
        );
    }

    public static String formatEvolutionRecords(List<EvolutionRecord> records) {
        return formatEvolutionRecords(records, "cn");
    }

    public static String formatEvolutionRecords(List<EvolutionRecord> records, String language) {
        boolean english = "en".equalsIgnoreCase(language);
        String header = english ? "Experience" : "经验";
        String contentLabel = english ? "Content" : "内容";
        String empty = english ? "(no evolution records)" : "（无演进经验）";

        List<String> lines = new ArrayList<>();
        int index = 1;
        for (EvolutionRecord record : records == null ? List.<EvolutionRecord>of() : records) {
            EvolutionPatch change = record.getChange();
            String section = change == null || change.getSection() == null ? "?" : change.getSection();
            String content = change == null || change.getContent() == null ? "" : change.getContent();
            lines.add(String.format(
                    Locale.ROOT,
                    "### %s #%d [%s] - source: %s, score: %.2f%n- Section: %s%n- %s: %s",
                    header,
                    index,
                    nullToEmpty(record.getTimestamp()),
                    nullToDefault(record.getSource(), "unknown"),
                    record.getScore(),
                    section,
                    contentLabel,
                    content
            ));
            index++;
        }
        return lines.isEmpty() ? empty : String.join("\n\n", lines);
    }

    private ExperienceApprovalRequest stageRecords(
            ExperienceProposal proposal,
            List<EvolutionRecord> records,
            String changeType,
            String requestIdPrefix,
            Object trajectory,
            List<Map<String, Object>> messages,
            boolean sharedRecords
    ) {
        SkillExperienceOperator operator = skillOps.get(proposal.getSkillName());
        if (operator == null) {
            operator = new SkillExperienceOperator(proposal.getSkillName());
        }
        List<ApplyResult> applyResults = previewApplyResults(
                proposal.getSkillName(),
                operator,
                UpdateValue.builder()
                        .payload(records == null ? List.of() : records)
                        .mode(Protocols.APPEND_MODE)
                        .effect(Protocols.PENDING_CHANGE_EFFECT)
                        .changeType(changeType)
                        .build()
        );
        return stagePendingRequest(
                proposal,
                buildLocalApplyPreview(proposal.getSkillName(), applyResults),
                requestIdPrefix,
                trajectory,
                messages,
                sharedRecords
        );
    }

    private ExperienceApprovalRequest stagePendingRequest(
            ExperienceProposal proposal,
            LocalApplyPreview preview,
            String requestIdPrefix,
            Object trajectory,
            List<Map<String, Object>> messages,
            boolean sharedRecords
    ) {
        PendingChange pending = makePendingChangeFromPreview(
                preview,
                requestIdPrefix,
                trajectory,
                messages,
                sharedRecords
        );
        PendingChange stagedPending = stagePendingChange(pending);
        LOGGER.info(
                "[ExperienceManager] staged approval request {} with {} record(s) for skill={}",
                stagedPending.getChangeId(),
                stagedPending.getPayload().size(),
                proposal.getSkillName()
        );
        return new ExperienceApprovalRequest(
                proposal.getSkillName(),
                proposal,
                stagedPending,
                stagedPending.getChangeId(),
                preview.getApplyResults()
        );
    }

    private CompletionStage<ExperienceApplyResult> applyRequest(
            String requestId,
            String action,
            List<String> approvedRecordIds
    ) {
        PendingChange pending = pendingApprovalSnapshots.get(requestId);
        if (pending == null) {
            LOGGER.warning("[ExperienceManager] " + action + "_request: unknown request_id=" + requestId);
            return CompletableFuture.completedFuture(new ExperienceApplyResult(
                    "",
                    0,
                    0,
                    0,
                    List.of("unknown request_id: " + requestId),
                    Map.of()
            ));
        }
        if (Protocols.REJECT_ACTION.equals(action)) {
            rejectPendingChange(requestId);
            return CompletableFuture.completedFuture(ExperienceCommon.rejectPendingChange(pending));
        }
        List<String> approvedIds = Protocols.APPROVE_ACTION.equals(action) ? approvedRecordIds : null;
        return ExperienceCommon.commitPendingChange(pendingApprovalSnapshots, requestId, store, approvedIds)
                .thenApply(result -> toApplyResult(pending.getSkillName(), result));
    }

    private CompletionStage<ExperienceApplyResult> commitStagedRequest(ExperienceApprovalRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("staged request missing request_id"));
        }
        return approveRequest(request.getRequestId()).thenCompose(result -> {
            if (result.isOk()) {
                return CompletableFuture.completedFuture(result);
            }
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "auto-commit failed for skill=" + request.getSkillName()
                            + ", request_id=" + request.getRequestId()
                            + ", applied=" + result.getAppliedCount()
                            + ", pending=" + result.getPendingCount()
                            + ", errors=" + result.getErrors()
            ));
        });
    }

    private String getRebuildTemplate() {
        boolean teamSkill = "team-skill".equals(kind);
        if ("en".equalsIgnoreCase(language)) {
            return (teamSkill
                    ? "You received a team skill rebuild request. Old version has been archived. "
                    : "You received a skill rebuild request. Old version has been archived. ")
                    + "Please follow these steps:\n\n"
                    + "## Filtered Historical Evolution Records (score >= {min_score})\n\n"
                    + "{evolution_records}\n\n"
                    + "## User Intent\n\n"
                    + "{user_intent}\n\n"
                    + "## Execution Requirements\n\n"
                    + "Please invoke the " + (teamSkill ? "teamskill-creator" : "skill-creator") + " skill:\n"
                    + "1. Generate new SKILL.md based on the historical records and user intent above\n"
                    + "2. Reset evolutions.json to empty list\n\n"
                    + "Old version has been archived to archive/ directory, you can directly create the new version.";
        }
        return "你收到了一次" + (teamSkill ? "团队技能" : "技能") + "重建请求。旧版本已归档。\n\n"
                + "## 已筛选的历史演进经验（score >= {min_score}）\n\n"
                + "{evolution_records}\n\n"
                + "## 用户意图\n\n"
                + "{user_intent}\n\n"
                + "## 执行要求\n\n"
                + "请调用 " + (teamSkill ? "teamskill-creator" : "skill-creator") + " 技能生成新的 SKILL.md，"
                + "并将 evolutions.json 重置为空列表。";
    }

    private String getDefaultRebuildIntent() {
        if ("en".equalsIgnoreCase(language)) {
            return "team-skill".equals(kind)
                    ? "Based on the evolution records above, perform a comprehensive rebuild of the team skill."
                    : "Based on the evolution records above, perform a comprehensive rebuild of the skill.";
        }
        return "根据以上演进经验，对" + ("team-skill".equals(kind) ? "团队技能" : "技能") + "进行全面优化和重建。";
    }

    private static double elapsedSeconds(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000_000.0d;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    /**
     * Store interaction surface consumed by the experience manager.
     *
     * <p>Mirrors Python's {@code EvolutionStore} usage in
     * {@code openjiuwen/agent_evolving/experience/skill_experience_manager.py}.</p>
     */
    public interface ExperienceStore extends ExperienceCommon.ExperienceStore {

        boolean skillDefinitionExists(String skillName);

        CompletionStage<String> readSkillContent(String skillName, boolean strict);

        String extractDescriptionFromSkillMd(String content);
    }
}
