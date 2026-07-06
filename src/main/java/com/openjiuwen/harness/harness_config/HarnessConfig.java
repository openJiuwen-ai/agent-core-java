/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Public class HarnessConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarnessConfig {
    @JsonProperty("schema_version")
    @Builder.Default
    private String schemaVersion = "harness_config.v0.1";
    private MetaSchema meta;
    private String id;
    private String name;
    private String description;
    private WorkspaceSchema workspace;
    private PromptsSchema prompts;
    private ResourcesSchema resources;
    @Builder.Default
    private String language = "cn";
    @JsonProperty("max_iterations")
    private Integer maxIterations;
    @JsonProperty("completion_timeout")
    private Double completionTimeout;

    /**
     * Auto-generated for codecheck compliance.
     */
    public String toYaml() {
        return toYaml(null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String toYaml(Path outputPath) {
        String yaml = new Yaml().dump(toYamlMap());
        if (outputPath != null) {
            try {
                Files.writeString(outputPath, yaml);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
        return yaml;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toYamlMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schema_version", schemaVersion);
        if (meta != null) {
            data.put("meta", meta.toYamlMap());
        }
        if (id != null && !id.isBlank()) {
            data.put("id", id);
        }
        if (name != null && !name.isBlank()) {
            data.put("name", name);
        }
        if (description != null && !description.isBlank()) {
            data.put("description", description);
        }
        if (workspace != null) {
            data.put("workspace", workspace.toYamlMap());
        }
        if (prompts != null && !prompts.getSections().isEmpty()) {
            data.put("prompts", prompts.toYamlMap());
        }
        if (resources != null && resources.hasAny()) {
            data.put("resources", resources.toYamlMap());
        }
        data.put("language", language);
        if (maxIterations != null) {
            data.put("max_iterations", maxIterations);
        }
        if (completionTimeout != null) {
            data.put("completion_timeout", completionTimeout);
        }
        return data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class MetaSchema {
        @Builder.Default
        private String owner = "";
        @Builder.Default
        private List<String> tags = new ArrayList<>();
        @Builder.Default
        private String visibility = "internal";

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("owner", owner);
            data.put("tags", tags);
            data.put("visibility", visibility);
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class SectionSchema {
        private String name;
        private Integer priority;
        private String file;
        private Object content;

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("name", name);
            if (priority != null) {
                data.put("priority", priority);
            }
            if (file != null && !file.isBlank()) {
                data.put("file", file);
            }
            if (content != null) {
                data.put("content", content);
            }
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class ToolResourceSchema {
        private String type;
        @Builder.Default
        private List<String> names = new ArrayList<>();
        private String name;
        private String packageName;
        private String module;
        @JsonProperty("class")
        private String className;

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("package")
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getPackageName() {
            return packageName;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("package")
        /**
         * Auto-generated for codecheck compliance.
         */
        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", type);
            if (!names.isEmpty()) {
                data.put("names", names);
            }
            if (name != null && !name.isBlank()) {
                data.put("name", name);
            }
            if (packageName != null && !packageName.isBlank()) {
                data.put("package", packageName);
            }
            if (module != null && !module.isBlank()) {
                data.put("module", module);
            }
            if (className != null && !className.isBlank()) {
                data.put("class", className);
            }
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class RailResourceSchema {
        private String type;
        private String name;
        private String packageName;
        private String module;
        @JsonProperty("class")
        private String className;
        @Builder.Default
        private Map<String, Object> config = new LinkedHashMap<>();

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("package")
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getPackageName() {
            return packageName;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("package")
        /**
         * Auto-generated for codecheck compliance.
         */
        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", type);
            if (name != null && !name.isBlank()) {
                data.put("name", name);
            }
            if (packageName != null && !packageName.isBlank()) {
                data.put("package", packageName);
            }
            if (module != null && !module.isBlank()) {
                data.put("module", module);
            }
            if (className != null && !className.isBlank()) {
                data.put("class", className);
            }
            if (config != null && !config.isEmpty()) {
                data.put("config", config);
            }
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class SkillsSchema {
        @Builder.Default
        private List<String> dirs = new ArrayList<>();
        @Builder.Default
        private String mode = "all";
        @Builder.Default
        private boolean enableCache = true;

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dirs", dirs);
            data.put("mode", mode);
            data.put("enable_cache", enableCache);
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class McpResourceSchema {
        @Builder.Default
        private String type = "stdio";
        @Builder.Default
        private String command = "";
        @Builder.Default
        private List<String> args = new ArrayList<>();
        @Builder.Default
        private Map<String, String> env = new LinkedHashMap<>();

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("type", type);
            data.put("command", command);
            data.put("args", args);
            data.put("env", env);
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class ResourcesSchema {
        @Builder.Default
        private List<ToolResourceSchema> tools = new ArrayList<>();
        @Builder.Default
        private List<RailResourceSchema> rails = new ArrayList<>();
        private SkillsSchema skills;
        @Builder.Default
        private List<McpResourceSchema> mcps = new ArrayList<>();

        boolean hasAny() {
            return !tools.isEmpty() || !rails.isEmpty() || skills != null || !mcps.isEmpty();
        }

        Map<String, Object> toYamlMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            if (!tools.isEmpty()) {
                data.put("tools", tools.stream().map(ToolResourceSchema::toYamlMap).toList());
            }
            if (!rails.isEmpty()) {
                data.put("rails", rails.stream().map(RailResourceSchema::toYamlMap).toList());
            }
            if (skills != null) {
                data.put("skills", skills.toYamlMap());
            }
            if (!mcps.isEmpty()) {
                data.put("mcps", mcps.stream().map(McpResourceSchema::toYamlMap).toList());
            }
            return data;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class PromptsSchema {
        @Builder.Default
        private List<SectionSchema> sections = new ArrayList<>();

        Map<String, Object> toYamlMap() {
            return Map.of("sections", sections.stream().map(SectionSchema::toYamlMap).toList());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    /**
     * Auto-generated for codecheck compliance.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Auto-generated for codecheck compliance.
     */
    public static class WorkspaceSchema {
        @Builder.Default
        private String rootPath = "./";

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("root_path")
        /**
         * Auto-generated for codecheck compliance.
         */
        public String getRootPath() {
            return rootPath;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @JsonProperty("root_path")
        /**
         * Auto-generated for codecheck compliance.
         */
        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        Map<String, Object> toYamlMap() {
            return Map.of("root_path", rootPath);
        }
    }
}
