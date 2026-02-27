// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a skill with its metadata.
 *
 * <p>This class holds information about a skill including its name,
 * description, and directory location.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/skills/skill_manager.py::Skill}
 *
 * @since 0.1.4
 */
public class Skill {

    /**
     * The name of the skill.
     */
    private String name;

    /**
     * The description of the skill.
     */
    private String description;

    /**
     * The directory path where the skill is located.
     */
    private Path directory;

    /**
     * Default constructor.
     */
    public Skill() {
    }

    /**
     * Full constructor.
     *
     * @param name        the skill name
     * @param description the skill description
     * @param directory   the skill directory
     */
    public Skill(String name, String description, Path directory) {
        this.name = name;
        this.description = description;
        this.directory = directory;
    }

    /**
     * Private constructor for builder.
     */
    private Skill(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.directory = builder.directory;
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    /**
     * Returns a string representation of this skill.
     *
     * @return a formatted string containing skill information
     */
    @Override
    public String toString() {
        return String.format("Skill: %s\nDescription: %s\nDirectory: %s",
                name, description, directory);
    }

    /**
     * Returns a brief representation of this skill for debugging.
     *
     * @return a brief formatted string
     */
    public String toBriefString() {
        String truncatedDesc = description != null && description.length() > 30
                ? description.substring(0, 30) + "..."
                : description;
        return String.format("[Skill: %s / Description: %s / Directory: %s]",
                name, truncatedDesc, directory);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Skill skill = (Skill) o;
        return Objects.equals(name, skill.name) &&
                Objects.equals(description, skill.description) &&
                Objects.equals(directory, skill.directory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, directory);
    }

    /**
     * Creates a new builder for constructing Skill instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for constructing Skill instances.
     */
    public static class Builder {
        private String name;
        private String description;
        private Path directory;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder directory(Path directory) {
            this.directory = directory;
            return this;
        }

        public Skill build() {
            return new Skill(this);
        }
    }
}
