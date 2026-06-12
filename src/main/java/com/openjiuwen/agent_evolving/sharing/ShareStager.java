/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.sharing;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.checkpointing.UsageStats;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Screens, wraps, and stages experiences for cross-user sharing.
 *
 * <p>Mirrors Python's {@code ShareStager} in
 * {@code openjiuwen/agent_evolving/sharing/share_stager.py}.</p>
 */
public class ShareStager {

    private static final Pattern FAILURE_KEYWORDS = Pattern.compile(
            "error(?!\\s*=\\s*None)|exception|traceback|failed|failure|timeout|timed out"
                    + "|errno|connectionerror|oserror|valueerror|typeerror"
                    + "|\\u9519\\u8bef|\\u5f02\\u5e38|\\u5931\\u8d25|\\u8d85\\u65f6"
                    + "|no such file|permission denied|access denied"
                    + "|command not found|not recognized|module not found"
                    + "|econnrefused|econnreset|enoent|enotfound|npm err!",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final KeywordExtractor keywordExtractor;
    private final ExperienceSharer sharer;
    private final double qcScoreThreshold;
    private final String sourceUserId;

    public ShareStager(KeywordExtractor keywordExtractor, ExperienceSharer sharer) {
        this(keywordExtractor, sharer, 0.6d, null);
    }

    public ShareStager(
            KeywordExtractor keywordExtractor,
            ExperienceSharer sharer,
            double qcScoreThreshold,
            String sourceUserId
    ) {
        this.keywordExtractor = keywordExtractor;
        this.sharer = sharer;
        this.qcScoreThreshold = qcScoreThreshold;
        this.sourceUserId = sourceUserId;
    }

    public double getQcScoreThreshold() {
        return qcScoreThreshold;
    }

    public CompletionStage<StagingResult> screenAndStage(String skillName, List<EvolutionRecord> records) {
        return screenAndStage(skillName, records, null);
    }

    public CompletionStage<StagingResult> screenAndStage(
            String skillName,
            List<EvolutionRecord> records,
            List<Map<String, Object>> messages
    ) {
        if (records == null || records.isEmpty()) {
            return CompletableFuture.completedFuture(StagingResult.empty());
        }

        List<SharedExperience> staged = new ArrayList<>();
        List<StagingResult.DroppedExperience> dropped = new ArrayList<>();

        for (EvolutionRecord record : records) {
            KeywordExtractor.KeywordSummary keywordSummary =
                    KeywordExtractor.parseFromOptimizerOutput(record != null ? record.getChange() : null);
            Optional<String> dropReason = qc(record, messages);
            if (dropReason.isPresent()) {
                dropped.add(new StagingResult.DroppedExperience(record, dropReason.get()));
                Loggers.AGENT.info(
                        "[ShareStager] share-QC dropped record={} reason={} skill={}",
                        record != null ? record.getId() : null,
                        dropReason.get(),
                        skillName);
                continue;
            }

            SharedExperience wrapped = wrap(
                    record,
                    keywordSummary.keywords(),
                    keywordSummary.summary(),
                    skillName);
            sharer.stageForUpload(skillName, wrapped);
            staged.add(wrapped);
        }

        StagingResult result = new StagingResult();
        result.setStagedForShare(staged);
        result.setDroppedForShare(dropped);
        return CompletableFuture.completedFuture(result);
    }

    public static boolean messagesHasSuccessfulTool(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        for (Map<String, Object> message : messages) {
            if (message == null) {
                continue;
            }
            Object rawRole = message.get("role");
            String role = rawRole == null ? "" : String.valueOf(rawRole);
            if (!"tool".equals(role) && !"function".equals(role)) {
                continue;
            }
            Object rawContent = message.get("content");
            String content = rawContent == null ? "" : String.valueOf(rawContent);
            if (content.strip().isEmpty()) {
                continue;
            }
            if (FAILURE_KEYWORDS.matcher(content).find()) {
                continue;
            }
            return true;
        }
        return false;
    }

    private Optional<String> qc(EvolutionRecord record, List<Map<String, Object>> messages) {
        if (record == null) {
            return Optional.of("record is null");
        }
        if ("execution_failure".equals(record.getSource()) && !messagesHasSuccessfulTool(messages)) {
            return Optional.of("execution failure without successful follow-up tool call");
        }

        double score = record.getScore();
        if (score < qcScoreThreshold) {
            return Optional.of(String.format(Locale.ROOT, "score %.2f below threshold %.2f", score, qcScoreThreshold));
        }
        return Optional.empty();
    }

    private SharedExperience wrap(
            EvolutionRecord record,
            List<String> keywords,
            String summary,
            String skillName
    ) {
        SharingMeta meta = new SharingMeta();
        meta.setSkillName(skillName);
        meta.setSkillVersion(record.getSkillVersion() != null ? record.getSkillVersion() : "");
        meta.setUploadTrigger("user_approval");
        meta.setSourceUserId(sourceUserId);
        meta.setConfidence(record.getScore());

        SharedExperience experience = new SharedExperience();
        experience.setRecord(deepCopy(record));
        experience.setKeywords(keywords);
        experience.setSummary(summary);
        experience.setSharingMeta(meta);
        return experience;
    }

    private static EvolutionRecord deepCopy(EvolutionRecord record) {
        return record == null ? null : new EvolutionRecord(
                record.getId(),
                record.getSource(),
                record.getTimestamp(),
                record.getContext(),
                copyPatch(record.getChange()),
                record.isApplied(),
                record.getScore(),
                copyUsageStats(record.getUsageStats()),
                record.getSkillVersion(),
                record.getSummary());
    }

    private static EvolutionPatch copyPatch(EvolutionPatch patch) {
        return patch == null ? null : new EvolutionPatch(
                patch.getSection(),
                patch.getAction(),
                patch.getContent(),
                patch.getTarget(),
                patch.getSkipReason(),
                patch.getMergeTarget(),
                patch.getScriptFilename(),
                patch.getScriptLanguage(),
                patch.getScriptPurpose(),
                patch.getKeywords(),
                patch.getSummary());
    }

    private static UsageStats copyUsageStats(UsageStats stats) {
        return stats == null ? null : UsageStats.fromDict(stats.toDict());
    }
}
