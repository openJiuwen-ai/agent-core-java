/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lists enabled skills or routes a query to matching skill names.
 *
 * <p>Mirrors Python's {@code ListSkillTool} in
 * {@code openjiuwen/harness/tools/skills/list_skill.py}.</p>
 */
public class ListSkillTool extends AbstractHarnessTool {

    private final Supplier<List<SkillDescriptor>> skillsSupplier;
    private final SkillRouter skillRouter;

    public ListSkillTool(Supplier<List<SkillDescriptor>> skillsSupplier) {
        this(skillsSupplier, null);
    }

    public ListSkillTool(Supplier<List<SkillDescriptor>> skillsSupplier, SkillRouter skillRouter) {
        super(toolCard("list_skill", "ListSkillTool", "List all enabled skills or relevant skills for a task."));
        this.skillsSupplier = skillsSupplier;
        this.skillRouter = skillRouter;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            String query = stringValue(inputs == null ? null : inputs.get("query")).trim();
            List<SkillDescriptor> skills = safeSkills();
            if (query.isEmpty()) {
                return ToolOutput.success(Map.of(
                        "skills", dumpSkills(skills),
                        "mode", "all"
                ));
            }
            if (skillRouter == null) {
                return ToolOutput.success(Map.of(
                        "skills", dumpSkills(skills),
                        "mode", "all",
                        "message", "list_skill_model is not configured, fallback to all skills."
                ));
            }
            List<String> selectedNames = skillRouter.route(query, dumpSkills(skills));
            List<SkillDescriptor> selected = selectByName(skills, selectedNames);
            return ToolOutput.success(Map.of(
                    "skills", dumpSkills(selected),
                    "mode", "filtered",
                    "selected_skill_names", selected.stream().map(SkillDescriptor::name).toList()
            ));
        } catch (Exception exception) {
            return ToolOutput.failure(exception.getMessage());
        }
    }

    private List<SkillDescriptor> safeSkills() {
        List<SkillDescriptor> skills = skillsSupplier == null ? List.of() : skillsSupplier.get();
        return skills == null ? List.of() : skills;
    }

    private static List<Map<String, Object>> dumpSkills(List<SkillDescriptor> skills) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillDescriptor skill : skills == null ? List.<SkillDescriptor>of() : skills) {
            if (skill != null) {
                result.add(skill.asMap());
            }
        }
        return result;
    }

    private static List<SkillDescriptor> selectByName(List<SkillDescriptor> skills, List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<SkillDescriptor> selected = new ArrayList<>();
        for (String name : names) {
            for (SkillDescriptor skill : skills) {
                if (skill != null && name.equals(skill.name())) {
                    selected.add(skill);
                    break;
                }
            }
        }
        return selected;
    }

    @FunctionalInterface
    public interface SkillRouter {
        List<String> route(String query, List<Map<String, Object>> availableSkills) throws Exception;
    }
}
