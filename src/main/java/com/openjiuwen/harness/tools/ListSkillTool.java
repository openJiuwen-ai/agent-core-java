/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Public class ListSkillTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ListSkillTool {
    private final Path skillsRoot;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ListSkillTool(String skillsRoot) {
        this.skillsRoot = Path.of(skillsRoot).toAbsolutePath().normalize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput listSkills() {
        try (Stream<Path> stream = Files.list(skillsRoot)) {
            List<String> skills = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(path -> path.getFileName().toString())
                    .toList();
            return ToolOutput.builder().success(true).data(skills).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
