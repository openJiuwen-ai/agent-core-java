// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AbilityManager}.
 */
class AbilityManagerTest {

    private AbilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    // ========== Add / Get / List ==========

    @Test
    void testAddAndGetToolCard() {
        ToolCard tc = ToolCard.builder()
                .name("add")
                .description("addition tool")
                .inputParams(Map.of("type", "object"))
                .build();

        manager.add(tc);

        Object result = manager.get("add");
        assertThat(result).isInstanceOf(ToolCard.class);
        assertThat(((ToolCard) result).getName()).isEqualTo("add");
    }

    @Test
    void testAddAndGetAgentCard() {
        AgentCard ac = AgentCard.builder()
                .name("sub-agent")
                .description("a sub agent")
                .build();

        manager.add(ac);

        Object result = manager.get("sub-agent");
        assertThat(result).isInstanceOf(AgentCard.class);
    }

    @Test
    void testAddList() {
        ToolCard tc1 = ToolCard.builder().name("tool1").build();
        ToolCard tc2 = ToolCard.builder().name("tool2").build();

        manager.add(List.of(tc1, tc2));

        assertThat(manager.get("tool1")).isNotNull();
        assertThat(manager.get("tool2")).isNotNull();
    }

    @Test
    void testGetNonExistent() {
        assertThat(manager.get("nonexistent")).isNull();
    }

    @Test
    void testListAll() {
        ToolCard tc = ToolCard.builder().name("t1").build();
        AgentCard ac = AgentCard.builder().name("a1").build();

        manager.add(tc);
        manager.add(ac);

        List<Object> all = manager.list();
        assertThat(all).hasSize(2);
    }

    @Test
    void testListEmpty() {
        assertThat(manager.list()).isEmpty();
    }

    // ========== Remove ==========

    @Test
    void testRemoveTool() {
        ToolCard tc = ToolCard.builder().name("removable").build();
        manager.add(tc);

        Object removed = manager.remove("removable");
        assertThat(removed).isNotNull();
        assertThat(manager.get("removable")).isNull();
    }

    @Test
    void testRemoveNonExistent() {
        Object removed = manager.remove("does_not_exist");
        assertThat(removed).isNull();
    }

    @Test
    void testRemoveByNameList() {
        ToolCard tc1 = ToolCard.builder().name("rem1").build();
        ToolCard tc2 = ToolCard.builder().name("rem2").build();
        manager.add(tc1);
        manager.add(tc2);

        List<Object> removed = manager.remove(List.of("rem1", "rem2"));
        assertThat(removed).hasSize(2);
        assertThat(manager.list()).isEmpty();
    }

    // ========== ToolInfo ==========

    @Test
    void testListToolInfo() {
        ToolCard tc = ToolCard.builder()
                .name("calc")
                .description("calculator")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();

        manager.add(tc);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("calc");
        assertThat(infos.get(0).getDescription()).isEqualTo("calculator");
    }

    @Test
    void testListToolInfoAgent() {
        AgentCard ac = AgentCard.builder()
                .name("agent1")
                .description("test agent")
                .build();

        manager.add(ac);

        List<ToolInfo> infos = manager.listToolInfo();
        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("agent1");
    }

    @Test
    void testListToolInfoEmpty() {
        assertThat(manager.listToolInfo()).isEmpty();
    }

    @Test
    void testListToolInfoFiltersByNames() {
        manager.add(ToolCard.builder().name("tool-a").description("a").build());
        manager.add(ToolCard.builder().name("tool-b").description("b").build());

        List<ToolInfo> infos = manager.listToolInfo(List.of("tool-b"), null);

        assertThat(infos).hasSize(1);
        assertThat(infos.get(0).getName()).isEqualTo("tool-b");
    }

    // ========== setToolDescription ==========

    @Test
    void testSetToolDescription() {
        ToolCard tc = ToolCard.builder().name("mytool").description("old").build();
        manager.add(tc);

        manager.setToolDescription("mytool", "new description");

        ToolCard updated = (ToolCard) manager.get("mytool");
        assertThat(updated.getDescription()).isEqualTo("new description");
    }

    @Test
    void testSetToolDescriptionNonExistent() {
        // Should not throw
        manager.setToolDescription("nonexistent", "desc");
    }

    // ========== Overwrite ==========

    @Test
    void testAddSameNameOverwrites() {
        ToolCard tc1 = ToolCard.builder().name("dup").description("first").build();
        ToolCard tc2 = ToolCard.builder().name("dup").description("second").build();

        manager.add(tc1);
        manager.add(tc2);

        ToolCard result = (ToolCard) manager.get("dup");
        assertThat(result.getDescription()).isEqualTo("second");
    }

    // ========== Unknown type ==========

    @Test
    void testAddUnknownType() {
        // Should not throw, just logs a warning
        manager.add("not a card");
        assertThat(manager.list()).isEmpty();
    }

    @Test
    void testAddNull() {
        manager.add(null);
        assertThat(manager.list()).isEmpty();
    }
}
