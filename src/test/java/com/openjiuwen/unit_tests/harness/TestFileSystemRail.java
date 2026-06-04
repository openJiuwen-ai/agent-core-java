/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness;

import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.SysOperationRail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SysOperationRail filesystem tool registration.
 *
 * <p>Mirrors Python's {@code test_filesystem_rail.py} in
 * {@code tests.unit_tests.harness}.</p>
 */
class TestFileSystemRail {

    @TempDir
    Path tempDir;

    @Test
    @Tag("level0")
    @DisplayName("SysOperationRail registers base filesystem and shell tools")
    void testSysOperationRailRegistersBaseTools() {
        SysOperationRail rail = new SysOperationRail();
        DeepAgent agent = agent("test_sys_operation_rail_base_tools");
        rail.setSysOperation(sysOperation("test_sys_operation_rail_base_tools"));

        try {
            rail.init(agent);

            Set<String> names = abilityNames(agent);
            assertTrue(names.containsAll(Set.of(
                    "read_file",
                    "write_file",
                    "edit_file",
                    "glob",
                    "list_files",
                    "grep",
                    "bash"
            )));
            assertFalse(names.contains("code"));
            assertFalse(names.contains("audio_transcription"));
            assertFalse(names.contains("audio_question_answering"));
            assertFalse(names.contains("audio_metadata"));
            assertFalse(names.contains("image_ocr"));
            assertFalse(names.contains("visual_question_answering"));
        } finally {
            rail.uninit(agent);
        }
    }

    @Test
    @Tag("level0")
    @DisplayName("SysOperationRail optionally registers the code tool")
    void testSysOperationRailWithCodeTool() {
        SysOperationRail rail = new SysOperationRail(true);
        DeepAgent agent = agent("test_sys_operation_rail_with_code_tool");
        rail.setSysOperation(sysOperation("test_sys_operation_rail_with_code_tool"));

        try {
            rail.init(agent);

            assertTrue(abilityNames(agent).contains("code"));
        } finally {
            rail.uninit(agent);
        }
    }

    private SysOperation sysOperation(String id) {
        SysOperationCard card = SysOperationCard.builder()
                .id(id)
                .name(id)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(tempDir.toString()).build())
                .build();
        return new SysOperation(card);
    }

    private static DeepAgent agent(String id) {
        return new DeepAgent(AgentCard.builder()
                .id(id)
                .name(id)
                .build());
    }

    private static Set<String> abilityNames(DeepAgent agent) {
        AbilityManager manager = agent.getDelegate().getAbilityManager();
        return manager.list().stream()
                .map(ability -> {
                    try {
                        return String.valueOf(ability.getClass().getMethod("getName").invoke(ability));
                    } catch (ReflectiveOperationException exception) {
                        throw new AssertionError("ability card should expose getName", exception);
                    }
                })
                .collect(Collectors.toSet());
    }
}
