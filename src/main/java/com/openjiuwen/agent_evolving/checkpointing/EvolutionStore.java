/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import java.util.List;

/**
 * Interface for evolution record storage.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.EvolutionStore}.
 */
public interface EvolutionStore {

    /**
     * Read SKILL.md content for a skill.
     *
     * @param skillName Skill name
     * @return Skill content or null if not found
     */
    String readSkillContent(String skillName);

    /**
     * Write SKILL.md content for a skill.
     *
     * @param skillName Skill name
     * @param content New content
     * @return True if successful
     */
    boolean writeSkillContent(String skillName, String content);

    /**
     * Load evolution log for a skill.
     *
     * @param skillName Skill name
     * @return EvolutionLog or null if not found
     */
    EvolutionLog loadEvolutionLog(String skillName);

    /**
     * Save evolution log for a skill.
     *
     * @param skillName Skill name
     * @param log EvolutionLog to save
     * @return True if successful
     */
    boolean saveEvolutionLog(String skillName, EvolutionLog log);

    /**
     * Delete specific evolution records.
     *
     * @param skillName Skill name
     * @param recordIds Record IDs to delete
     * @return Number of records deleted
     */
    int deleteRecords(String skillName, List<String> recordIds);

    /**
     * Load all evolution records for a skill.
     *
     * @param skillName Skill name
     * @return List of EvolutionRecord
     */
    List<EvolutionRecord> loadRecords(String skillName);

    /**
     * Save an evolution record.
     *
     * @param skillName Skill name
     * @param record Record to save
     * @return True if successful
     */
    boolean saveRecord(String skillName, EvolutionRecord record);

    /**
     * Merge multiple records into one.
     *
     * @param skillName Skill name
     * @param primaryId ID of the primary record to keep
     * @param removeIds IDs of records to remove and merge
     * @param newContent New merged content
     * @return True if successful
     */
    boolean mergeRecords(String skillName, String primaryId, List<String> removeIds, String newContent);

    /**
     * Update content of a specific record.
     *
     * @param skillName Skill name
     * @param recordId Record ID to update
     * @param newContent New content
     * @return True if successful
     */
    boolean updateRecordContent(String skillName, String recordId, String newContent);
}