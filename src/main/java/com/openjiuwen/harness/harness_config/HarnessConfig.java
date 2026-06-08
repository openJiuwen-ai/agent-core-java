/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.harness_config;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level harness configuration schema.
 * <p>
 * Mirrors Python's {@code HarnessConfig} in
 * {@code openjiuwen/harness/harness_config/schema.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class HarnessConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Builder.Default
    @JsonProperty("schema_version")
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

    @Builder.Default
    private Map<String, Object> extraFields = new LinkedHashMap<>();

    /**
     * Mirrors Python's {@code MetaSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MetaSchema {

        @Builder.Default
        private String owner = "";

        @Builder.Default
        private List<String> tags = new ArrayList<>();

        @Builder.Default
        private String visibility = "internal";
    }

    /**
     * Mirrors Python's {@code SectionSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SectionSchema {

        private String name;

        private Integer priority;

        private String file;

        private Object content;
    }

    /**
     * Mirrors Python's {@code ToolResourceSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolResourceSchema {

        private String type;

        private List<String> names;

        private String name;

        private String packageName;

        private String module;

        @JsonProperty("class")
        private String className;

        @JsonProperty("package")
        public String getPackageName() {
            return packageName;
        }

        @JsonProperty("package")
        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }
    }

    /**
     * Mirrors Python's {@code RailResourceSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RailResourceSchema {

        private String type;

        private String name;

        private String packageName;

        private String module;

        @JsonProperty("class")
        private String className;

        @JsonProperty("package")
        public String getPackageName() {
            return packageName;
        }

        @JsonProperty("package")
        public void setPackageName(String packageName) {
            this.packageName = packageName;
        }
    }

    /**
     * Mirrors Python's {@code SkillsSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillsSchema {

        @Builder.Default
        private List<String> dirs = new ArrayList<>();

        @Builder.Default
        private String mode = "all";
    }

    /**
     * Mirrors Python's {@code McpResourceSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class McpResourceSchema {

        @Builder.Default
        private String type = "stdio";

        @Builder.Default
        private String command = "";

        @Builder.Default
        private List<String> args = new ArrayList<>();

        @Builder.Default
        private Map<String, String> env = new LinkedHashMap<>();
    }

    /**
     * Mirrors Python's {@code ResourcesSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourcesSchema {

        @Builder.Default
        private List<ToolResourceSchema> tools = new ArrayList<>();

        @Builder.Default
        private List<RailResourceSchema> rails = new ArrayList<>();

        private SkillsSchema skills;

        @Builder.Default
        private List<McpResourceSchema> mcps = new ArrayList<>();
    }

    /**
     * Mirrors Python's {@code PromptsSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromptsSchema {

        @Builder.Default
        private List<SectionSchema> sections = new ArrayList<>();
    }

    /**
     * Mirrors Python's {@code WorkspaceSchema} in
     * {@code openjiuwen/harness/harness_config/schema.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkspaceSchema {

        @Builder.Default
        @JsonProperty("root_path")
        private String rootPath = "./";
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        extraFields.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    /**
     * Serialize this config to a YAML string.
     *
     * @return YAML representation
     */
    public String toYaml() {
        return toYaml((Path) null);
    }

    /**
     * Serialize this config to YAML and optionally write it to disk.
     *
     * @param outputPath output path, or {@code null}
     * @return YAML representation
     */
    public String toYaml(String outputPath) {
        return outputPath == null ? toYaml((Path) null) : toYaml(Path.of(outputPath));
    }

    /**
     * Serialize this config to YAML and optionally write it to disk.
     *
     * @param outputPath output path, or {@code null}
     * @return YAML representation
     */
    public String toYaml(Path outputPath) {
        Map<String, Object> data = MAPPER.convertValue(this, new TypeReference<LinkedHashMap<String, Object>>() {
        });
        Yaml yaml = new Yaml(createYamlOptions());
        String yamlString = yaml.dump(data);
        if (outputPath != null) {
            try {
                Files.writeString(outputPath, yamlString);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to write yaml to " + outputPath, e);
            }
        }
        return yamlString;
    }

    private static DumperOptions createYamlOptions() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        return options;
    }
}
