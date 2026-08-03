/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Objects;

/**
 * Team info table model.
 * <p>
 * Mirrors Python's {@code Team} in
 * {@code openjiuwen/agent_teams/tools/models.py}.
 */
public class Team {

    private String teamName;
    private String displayName;
    private String leaderMemberName;
    private String desc;
    private String prompt;
    private Long created;
    private Long updatedAt;

    public Team() {
    }

    public Team(String teamName, String displayName, String leaderMemberName,
            String desc, String prompt, Long created, Long updatedAt) {
        this.teamName = teamName;
        this.displayName = displayName;
        this.leaderMemberName = leaderMemberName;
        this.desc = desc;
        this.prompt = prompt;
        this.created = created;
        this.updatedAt = updatedAt;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getLeaderMemberName() {
        return leaderMemberName;
    }

    public void setLeaderMemberName(String leaderMemberName) {
        this.leaderMemberName = leaderMemberName;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Team that)) {
            return false;
        }
        return Objects.equals(teamName, that.teamName)
                && Objects.equals(displayName, that.displayName)
                && Objects.equals(leaderMemberName, that.leaderMemberName)
                && Objects.equals(desc, that.desc)
                && Objects.equals(prompt, that.prompt)
                && Objects.equals(created, that.created)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamName, displayName, leaderMemberName, desc, prompt, created, updatedAt);
    }

    @Override
    public String toString() {
        return "Team{"
                + "teamName='" + teamName + '\''
                + ", displayName='" + displayName + '\''
                + ", leaderMemberName='" + leaderMemberName + '\''
                + ", desc='" + desc + '\''
                + ", prompt='" + prompt + '\''
                + ", created=" + created
                + ", updatedAt=" + updatedAt
                + '}';
    }
}
