/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Full task detail returned by get actions.
 *
 * <p>Mirrors Python's {@code TaskDetail} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDetail extends TaskSummary {

    private String content;

    @JsonProperty("blocks")
    private List<String> blocks = new ArrayList<>();
}
