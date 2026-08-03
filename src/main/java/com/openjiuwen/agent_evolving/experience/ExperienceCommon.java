/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionLog;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionPatch;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.experience.common} in
 * {@code openjiuwen/agent_evolving/experience/common.py}.
 */
public final class ExperienceCommon {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;

    private ExperienceCommon() {
    }

    public static PendingChange makePendingChange(
            String skillName,
            List<EvolutionRecord> records,
            String requestIdPrefix,
            Object trajectory,
            List<Map<String, Object>> messages,
            boolean sharedRecords
    ) {
        PendingChange pending = PendingChange.make(skillName, records, trajectory, messages);
        pending.setSharedRecords(sharedRecords);
        if (requestIdPrefix != null && !requestIdPrefix.isBlank()) {
            pending.setChangeId(requestIdPrefix + "_" + ExperienceTypeUtils.newPendingChangeId().substring("skill_evolve_".length()));
        }
        return pending;
    }

    public static ExperienceApplyResult rejectPendingChange(PendingChange pending) {
        return new ExperienceApplyResult(
                pending.getSkillName(),
                0,
                pending.getPayload().size(),
                0,
                List.of(),
                Map.of()
        );
    }

    public static CompletionStage<PendingCommitResult> commitPendingChange(
            Map<String, PendingChange> pendingById,
            String changeId,
            ExperienceStore store,
            List<String> approvedRecordIds
    ) {
        PendingChange pending = pendingById.get(changeId);
        if (pending == null) {
            return CompletableFuture.failedFuture(new NoSuchElementException(changeId));
        }
        String changeType = pending.getChangeType();
        if (!Protocols.SKILL_EXPERIENCE_ENTRY.equals(changeType) && !Protocols.EXPERIENCE_ENTRY.equals(changeType)) {
            return CompletableFuture.failedFuture(new NoSuchElementException(changeType));
        }

        List<EvolutionRecord> allRecords = pending.getPayload();
        List<EvolutionRecord> approvedRecords;
        List<EvolutionRecord> rejectedRecords;
        if (approvedRecordIds == null) {
            approvedRecords = new ArrayList<>(allRecords);
            rejectedRecords = new ArrayList<>();
        } else {
            Set<String> approvedIds = new LinkedHashSet<>(approvedRecordIds);
            approvedRecords = new ArrayList<>();
            rejectedRecords = new ArrayList<>();
            for (EvolutionRecord record : allRecords) {
                if (record != null && approvedIds.contains(record.getId())) {
                    approvedRecords.add(record);
                } else {
                    rejectedRecords.add(record);
                }
            }
        }

        if (approvedRecords.isEmpty()) {
            pending.setPayload(List.of());
            pendingById.remove(changeId);
            return CompletableFuture.completedFuture(
                    new PendingCommitResult(0, 0, rejectedRecords.size(), List.of())
            );
        }

        return appendApprovedRecords(
                pendingById,
                changeId,
                store,
                pending,
                approvedRecords,
                rejectedRecords.size(),
                0
        );
    }

    public static CompletionStage<Map<String, Integer>> executeSimplifyActions(
            ExperienceStore store,
            String skillName,
            List<Map<String, Object>> actions
    ) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("deleted", 0);
        counts.put("merged", 0);
        counts.put("refined", 0);
        counts.put("kept", 0);
        counts.put("errors", 0);

        CompletionStage<Map<String, Integer>> stage = CompletableFuture.completedFuture(counts);
        for (Map<String, Object> action : actions) {
            stage = stage.thenCompose(currentCounts -> processSimplifyAction(store, skillName, action, currentCounts));
        }
        return stage.thenApply(currentCounts -> {
            LOGGER.info("[experience.common] executed simplify actions for skill=" + skillName + ": " + currentCounts);
            return currentCounts;
        });
    }

    public static CompletionStage<RebuildContextPayload> requestRebuildContext(
            ExperienceStore store,
            RebuildRequest request,
            Function<List<EvolutionRecord>, String> formatRecords,
            String defaultIntent,
            String template,
            boolean archiveEvolutionsOnSuccess
    ) {
        if (!store.skillExists(request.getSkillName())) {
            return CompletableFuture.completedFuture(null);
        }

        return swallowArchiveFailure(
                store.archiveSkillBody(request.getSkillName()),
                "[experience.common] skill body archive failed for '",
                request.getSkillName()
        ).thenCompose(ignored ->
                swallowArchiveFailure(
                        store.archiveEvolutions(request.getSkillName()),
                        "[experience.common] evolutions archive failed for '",
                        request.getSkillName()
                ).thenCompose(archiveOutcome ->
                        store.loadFullEvolutionLog(request.getSkillName()).thenCompose(recordsLog -> {
                            List<EvolutionRecord> filteredRecords = new ArrayList<>();
                            for (EvolutionRecord record : recordsLog.getEntries()) {
                                if (record == null || record.getScore() < request.getMinScore()) {
                                    continue;
                                }
                                EvolutionPatch change = record.getChange();
                                if (change != null && change.getSkipReason() != null) {
                                    continue;
                                }
                                filteredRecords.add(record);
                            }

                            String prompt = formatTemplate(
                                    template,
                                    formatRecords.apply(filteredRecords),
                                    isBlank(request.getUserIntent()) ? defaultIntent : request.getUserIntent(),
                                    request.getMinScore()
                            );
                            RebuildContextPayload payload = new RebuildContextPayload(
                                    request.getSkillName(),
                                    recordsLog,
                                    filteredRecords,
                                    prompt,
                                    archiveOutcome.archivePath(),
                                    archiveOutcome.error()
                            );
                            if (archiveEvolutionsOnSuccess && archiveOutcome.archivePath() != null) {
                                return store.clearEvolutions(request.getSkillName()).thenApply(ignoredClear -> payload);
                            }
                            return CompletableFuture.completedFuture(payload);
                        })
                )
        );
    }

    private static CompletionStage<PendingCommitResult> appendApprovedRecords(
            Map<String, PendingChange> pendingById,
            String changeId,
            ExperienceStore store,
            PendingChange pending,
            List<EvolutionRecord> approvedRecords,
            int rejectedCount,
            int index
    ) {
        if (index >= approvedRecords.size()) {
            pending.setPayload(List.of());
            pendingById.remove(changeId);
            return CompletableFuture.completedFuture(
                    new PendingCommitResult(approvedRecords.size(), 0, rejectedCount, List.of())
            );
        }

        EvolutionRecord record = approvedRecords.get(index);
        return store.appendRecord(pending.getSkillName(), record)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        List<EvolutionRecord> remainingRecords = new ArrayList<>(approvedRecords.subList(index, approvedRecords.size()));
                        pending.setPayload(remainingRecords);
                        return CompletableFuture.completedFuture(
                                new PendingCommitResult(
                                        index,
                                        remainingRecords.size(),
                                        rejectedCount,
                                        List.of(errorMessage(throwable))
                                )
                        );
                    }
                    return appendApprovedRecords(
                            pendingById,
                            changeId,
                            store,
                            pending,
                            approvedRecords,
                            rejectedCount,
                            index + 1
                    );
                })
                .thenCompose(Function.identity());
    }

    private static CompletionStage<Map<String, Integer>> processSimplifyAction(
            ExperienceStore store,
            String skillName,
            Map<String, Object> action,
            Map<String, Integer> counts
    ) {
        String actionType = stringValue(action.get("action"), "KEEP");
        String recordId = stringValue(action.get("record_id"), "");

        try {
            return switch (actionType) {
                case "DELETE" -> store.deleteRecords(skillName, List.of(recordId))
                        .handle((deleted, throwable) -> {
                            if (throwable != null) {
                                logSimplifyActionFailure(actionType, recordId, throwable, counts);
                            } else if (deleted != null && deleted > 0) {
                                increment(counts, "deleted");
                            } else {
                                increment(counts, "errors");
                            }
                            return counts;
                        });
                case "MERGE" -> store.mergeRecords(
                                skillName,
                                recordId,
                                toStringList(action.get("merge_remove_ids")),
                                stringValue(action.get("new_content"), "")
                        )
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                logSimplifyActionFailure(actionType, recordId, throwable, counts);
                            } else if (result != null) {
                                increment(counts, "merged");
                            } else {
                                increment(counts, "errors");
                            }
                            return counts;
                        });
                case "REFINE" -> store.updateRecordContent(
                                skillName,
                                recordId,
                                stringValue(action.get("new_content"), "")
                        )
                        .handle((result, throwable) -> {
                            if (throwable != null) {
                                logSimplifyActionFailure(actionType, recordId, throwable, counts);
                            } else if (result != null) {
                                increment(counts, "refined");
                            } else {
                                increment(counts, "errors");
                            }
                            return counts;
                        });
                case "KEEP" -> {
                    increment(counts, "kept");
                    yield CompletableFuture.completedFuture(counts);
                }
                default -> {
                    LOGGER.warning("[experience.common] unknown action type: " + actionType);
                    increment(counts, "errors");
                    yield CompletableFuture.completedFuture(counts);
                }
            };
        } catch (Exception exception) {
            logSimplifyActionFailure(actionType, recordId, exception, counts);
            return CompletableFuture.completedFuture(counts);
        }
    }

    private static CompletionStage<ArchiveOutcome> swallowArchiveFailure(
            CompletionStage<String> stage,
            String messageTemplate,
            String skillName
    ) {
        return stage.handle((archivePath, throwable) -> {
            if (throwable != null) {
                LOGGER.warning(messageTemplate + skillName + "': " + errorMessage(throwable));
                return new ArchiveOutcome(null, rootCause(throwable));
            }
            return new ArchiveOutcome(archivePath, null);
        });
    }

    private static void logSimplifyActionFailure(
            String actionType,
            String recordId,
            Throwable throwable,
            Map<String, Integer> counts
    ) {
        LOGGER.error("[experience.common] execute action " + actionType + " failed for " + recordId + ": "
                + errorMessage(throwable));
        increment(counts, "errors");
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.put(key, counts.getOrDefault(key, 0) + 1);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String errorMessage(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        String message = cause.getMessage();
        return isBlank(message) ? cause.getClass().getSimpleName() : message;
    }

    private static String formatTemplate(
            String template,
            String evolutionRecords,
            String userIntent,
            double minScore
    ) {
        return template
                .replace("{evolution_records}", evolutionRecords)
                .replace("{user_intent}", userIntent)
                .replace("{min_score}", Double.toString(minScore));
    }

    private static List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ArchiveOutcome(String archivePath, Throwable error) {
    }

    /**
     * Mirrors Python's store interaction surface in
     * {@code openjiuwen/agent_evolving/experience/common.py}.
     */
    public interface ExperienceStore {

        boolean skillExists(String skillName);

        CompletionStage<Void> appendRecord(String skillName, EvolutionRecord record);

        CompletionStage<Integer> deleteRecords(String skillName, List<String> recordIds);

        CompletionStage<EvolutionRecord> mergeRecords(
                String skillName,
                String recordId,
                List<String> mergeRemoveIds,
                String newContent
        );

        CompletionStage<EvolutionRecord> updateRecordContent(String skillName, String recordId, String newContent);

        CompletionStage<String> archiveSkillBody(String skillName);

        CompletionStage<String> archiveEvolutions(String skillName);

        CompletionStage<EvolutionLog> loadFullEvolutionLog(String skillName);

        CompletionStage<Void> clearEvolutions(String skillName);
    }

    /**
     * Mirrors Python's rebuild context payload in
     * {@code openjiuwen/agent_evolving/experience/common.py}.
     */
    public static final class RebuildContextPayload {

        private final String skillName;
        private final EvolutionLog recordsLog;
        private final List<EvolutionRecord> filteredRecords;
        private final String prompt;
        private final String archivePath;
        private final Throwable archiveError;

        public RebuildContextPayload(
                String skillName,
                EvolutionLog recordsLog,
                List<EvolutionRecord> filteredRecords,
                String prompt,
                String archivePath,
                Throwable archiveError
        ) {
            this.skillName = skillName;
            this.recordsLog = recordsLog;
            this.filteredRecords = filteredRecords == null ? List.of() : List.copyOf(filteredRecords);
            this.prompt = prompt;
            this.archivePath = archivePath;
            this.archiveError = archiveError;
        }

        public String getSkillName() {
            return skillName;
        }

        public EvolutionLog getRecordsLog() {
            return recordsLog;
        }

        public List<EvolutionRecord> getFilteredRecords() {
            return filteredRecords;
        }

        public String getPrompt() {
            return prompt;
        }

        public String getArchivePath() {
            return archivePath;
        }

        public Throwable getArchiveError() {
            return archiveError;
        }
    }
}
