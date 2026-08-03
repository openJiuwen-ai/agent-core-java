/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code EvolutionContext} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class EvolutionContext {

    private String skillName;
    private List<EvolutionSignal> signals = new ArrayList<>();
    private String skillContent;
    private List<Map<String, Object>> messages = new ArrayList<>();
    private List<EvolutionRecord> existingDescRecords = new ArrayList<>();
    private List<EvolutionRecord> existingBodyRecords = new ArrayList<>();
    private String userQuery = "";
    private Object trajectory;
    private List<EvolutionRecord> existingScriptRecords = new ArrayList<>();
    private Map<String, Object> metadata = Map.of();

    public EvolutionContext() {
    }

    public EvolutionContext(
            String skillName,
            List<EvolutionSignal> signals,
            String skillContent,
            List<Map<String, Object>> messages,
            List<EvolutionRecord> existingDescRecords,
            List<EvolutionRecord> existingBodyRecords,
            String userQuery,
            Object trajectory,
            List<EvolutionRecord> existingScriptRecords,
            Map<String, Object> metadata
    ) {
        this.skillName = skillName;
        setSignals(signals);
        this.skillContent = skillContent;
        setMessages(messages);
        setExistingDescRecords(existingDescRecords);
        setExistingBodyRecords(existingBodyRecords);
        this.userQuery = userQuery == null ? "" : userQuery;
        this.trajectory = trajectory;
        setExistingScriptRecords(existingScriptRecords);
        setMetadata(metadata);
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public List<EvolutionSignal> getSignals() {
        return ExperienceTypeUtils.copyList(signals);
    }

    public void setSignals(List<EvolutionSignal> signals) {
        this.signals = ExperienceTypeUtils.copyList(signals);
    }

    public String getSkillContent() {
        return skillContent;
    }

    public void setSkillContent(String skillContent) {
        this.skillContent = skillContent;
    }

    public List<Map<String, Object>> getMessages() {
        List<Map<String, Object>> copied = ExperienceTypeUtils.copyMessageList(messages);
        return copied == null ? new ArrayList<>() : copied;
    }

    public void setMessages(List<Map<String, Object>> messages) {
        List<Map<String, Object>> copied = ExperienceTypeUtils.copyMessageList(messages);
        this.messages = copied == null ? new ArrayList<>() : copied;
    }

    public List<EvolutionRecord> getExistingDescRecords() {
        return ExperienceTypeUtils.copyList(existingDescRecords);
    }

    public void setExistingDescRecords(List<EvolutionRecord> existingDescRecords) {
        this.existingDescRecords = ExperienceTypeUtils.copyList(existingDescRecords);
    }

    public List<EvolutionRecord> getExistingBodyRecords() {
        return ExperienceTypeUtils.copyList(existingBodyRecords);
    }

    public void setExistingBodyRecords(List<EvolutionRecord> existingBodyRecords) {
        this.existingBodyRecords = ExperienceTypeUtils.copyList(existingBodyRecords);
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery == null ? "" : userQuery;
    }

    public Object getTrajectory() {
        return trajectory;
    }

    public void setTrajectory(Object trajectory) {
        this.trajectory = trajectory;
    }

    public List<EvolutionRecord> getExistingScriptRecords() {
        return ExperienceTypeUtils.copyList(existingScriptRecords);
    }

    public void setExistingScriptRecords(List<EvolutionRecord> existingScriptRecords) {
        this.existingScriptRecords = ExperienceTypeUtils.copyList(existingScriptRecords);
    }

    public Map<String, Object> getMetadata() {
        return ExperienceTypeUtils.copyMap(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = ExperienceTypeUtils.copyMap(metadata);
    }
}
