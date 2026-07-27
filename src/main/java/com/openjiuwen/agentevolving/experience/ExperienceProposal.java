/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;

import java.util.List;

/**
 * Mirrors Python's {@code ExperienceProposal} in
 * {@code openjiuwen/agent_evolving/experience/types.py}.
 */
public class ExperienceProposal {

    private String skillName;
    private List<EvolutionRecord> records = List.of();
    private boolean requiresApproval;
    private String source = "experience_optimizer";
    private String userQuery = "";
    private String signalType;
    private String signalSource;

    public ExperienceProposal() {
    }

    public ExperienceProposal(
            String skillName,
            List<EvolutionRecord> records,
            boolean requiresApproval,
            String source,
            String userQuery,
            String signalType,
            String signalSource
    ) {
        this.skillName = skillName;
        setRecords(records);
        this.requiresApproval = requiresApproval;
        this.source = source == null ? "experience_optimizer" : source;
        this.userQuery = userQuery == null ? "" : userQuery;
        this.signalType = signalType;
        this.signalSource = signalSource;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public List<EvolutionRecord> getRecords() {
        return ExperienceTypeUtils.copyList(records);
    }

    public void setRecords(List<EvolutionRecord> records) {
        this.records = ExperienceTypeUtils.copyList(records);
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null ? "experience_optimizer" : source;
    }

    public String getUserQuery() {
        return userQuery;
    }

    public void setUserQuery(String userQuery) {
        this.userQuery = userQuery == null ? "" : userQuery;
    }

    public String getSignalType() {
        return signalType;
    }

    public void setSignalType(String signalType) {
        this.signalType = signalType;
    }

    public String getSignalSource() {
        return signalSource;
    }

    public void setSignalSource(String signalSource) {
        this.signalSource = signalSource;
    }

    public int getRecordCount() {
        return records.size();
    }
}
