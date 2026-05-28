/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pydantic-style schema models for harness_config.yaml.
 * <p>
 * Mirrors Python's {@code openjiuwen.harness.harness_config.schema}.
 */

// ---- MetaSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class MetaSchema {
    /** Governance metadata — display and permission management, not used at runtime. */
    @Builder.Default
    private String owner = "";
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    @Builder.Default
    private String visibility = "internal";
}

// ---- SectionSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SectionSchema {
    /** A single prompt section entry. */
    private String name;
    private Integer priority;
    private String file;
    /** Content can be a String or a Map<String,String>. */
    private Object content;
}

// ---- ToolResourceSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ToolResourceSchema {
    /** Tool resource specification: builtin | package | entry_point. */
    private String type;
    private List<String> names;
    private String name;
    private String packageName;
    private String module;
    private String className;
}

// ---- RailResourceSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class RailResourceSchema {
    /** Rail resource specification: builtin | package | entry_point. */
    private String type;
    private String name;
    private String packageName;
    private String module;
    private String className;
}

// ---- SkillsSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SkillsSchema {
    /** Skills configuration. */
    @Builder.Default
    private List<String> dirs = new ArrayList<>();
    @Builder.Default
    private String mode = "all"; // all | auto_list
}

// ---- McpResourceSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class McpResourceSchema {
    /** MCP server specification. */
    @Builder.Default
    private String type = "stdio"; // stdio | sse | streamable_http
    @Builder.Default
    private String command = "";
    @Builder.Default
    private List<String> args = new ArrayList<>();
    @Builder.Default
    private Map<String, String> env = new HashMap<>();
}

// ---- ResourcesSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ResourcesSchema {
    /** All runtime resources: tools, rails, skills, MCPs. */
    @Builder.Default
    private List<ToolResourceSchema> tools = new ArrayList<>();
    @Builder.Default
    private List<RailResourceSchema> rails = new ArrayList<>();
    private SkillsSchema skills;
    @Builder.Default
    private List<McpResourceSchema> mcps = new ArrayList<>();
}

// ---- PromptsSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class PromptsSchema {
    /** Prompt section declarations. */
    @Builder.Default
    private List<SectionSchema> sections = new ArrayList<>();
}

// ---- WorkspaceSchema ----

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class WorkspaceSchema {
    /** Workspace (file operation root directory). */
    @Builder.Default
    private String rootPath = "./";
}

// ---- HarnessConfig (top-level) ----

/**
 * Top-level harness_config.yaml schema.
 * <p>
 * Mirrors Python's {@code HarnessConfig} model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class HarnessConfig {
    @Builder.Default
    private String schemaVersion = "harness_config.v0.1";
    private MetaSchema meta;

    // Agent identity -> AgentCard.id / .name / .description
    private String id;
    private String name;
    private String description;

    private WorkspaceSchema workspace;
    private PromptsSchema prompts;
    private ResourcesSchema resources;

    // Execution control -> DeepAgentConfig fields
    @Builder.Default
    private String language = "cn";
    private Integer maxIterations;
    private Double completionTimeout;

    /** Extra fields not modeled explicitly (extra = allow). */
    @Builder.Default
    private Map<String, Object> extra = new HashMap<>();
}
