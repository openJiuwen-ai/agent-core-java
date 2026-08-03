/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.memory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.harness.workspace.Workspace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Construction parameters for {@link TeamMemoryManager}.
 *
 * <p>Mirrors Python's {@code TeamMemoryManagerParams} in
 * {@code openjiuwen/agent_teams/memory/manager_params.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamMemoryManagerParams implements TeamMemoryManager.Parameters {

    @JsonProperty("member_name")
    private String memberName;

    @JsonProperty("team_name")
    private String teamName;

    private TeamRole role;

    private TeamLifecycle lifecycle;

    private TeamScenario scenario;

    @JsonIgnore
    private EmbeddingConfig embeddingConfig;

    @JsonIgnore
    private Workspace workspace;

    @JsonIgnore
    private TeamMemoryExtractor.FileSystemView sysOperation;

    @JsonProperty("team_memory_dir")
    private String teamMemoryDir;

    private TeamLanguage language;

    @JsonProperty("prompt_mode")
    private PromptMode promptMode;

    @JsonProperty("enable_auto_extract")
    private boolean enableAutoExtract;

    @JsonProperty("read_only_source_workspace")
    private String readOnlySourceWorkspace;

    @JsonIgnore
    private TeamMemoryExtractor.TeamDatabaseView database;

    @JsonIgnore
    private TeamMemoryExtractor.TeamTaskManagerView taskManager;

    @JsonIgnore
    private TeamMemoryExtractor.ModelView extractionModel;

    @Builder.Default
    @JsonProperty("timezone_offset_hours")
    private double timezoneOffsetHours = 8.0d;

    @Override
    public String memberName() {
        return memberName;
    }

    @Override
    public String teamName() {
        return teamName;
    }

    @Override
    public String role() {
        return role == null ? null : role.getValue();
    }

    @Override
    public String lifecycle() {
        return lifecycle == null ? null : lifecycle.getValue();
    }

    @Override
    public String scenario() {
        return scenario == null ? null : scenario.getValue();
    }

    @Override
    public EmbeddingConfig embeddingConfig() {
        return embeddingConfig;
    }

    @Override
    public Workspace workspace() {
        return workspace;
    }

    @Override
    public TeamMemoryExtractor.FileSystemView sysOperation() {
        return sysOperation;
    }

    @Override
    public String teamMemoryDir() {
        return teamMemoryDir;
    }

    @Override
    public String language() {
        return language == null ? null : language.getValue();
    }

    @Override
    public String promptMode() {
        return promptMode == null ? null : promptMode.getValue();
    }

    @Override
    public boolean enableAutoExtract() {
        return enableAutoExtract;
    }

    @Override
    public String readOnlySourceWorkspace() {
        return readOnlySourceWorkspace;
    }

    @Override
    public TeamMemoryExtractor.TeamDatabaseView database() {
        return database;
    }

    @Override
    public TeamMemoryExtractor.TeamTaskManagerView taskManager() {
        return taskManager;
    }

    @Override
    public TeamMemoryExtractor.ModelView extractionModel() {
        return extractionModel;
    }

    @Override
    public double timezoneOffsetHours() {
        return timezoneOffsetHours;
    }
}
