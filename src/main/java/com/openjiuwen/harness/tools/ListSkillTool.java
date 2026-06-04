package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.harness.prompts.sections.SkillsSection;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code ListSkillTool} in {@code openjiuwen.harness.tools.skills.list_skill}.
 */
public class ListSkillTool extends AbstractHarnessTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<List<Skill>> getSkills;
    private final Object listSkillModel;

    public ListSkillTool(Supplier<List<Skill>> getSkills) {
        this(getSkills, null);
    }

    public ListSkillTool(Supplier<List<Skill>> getSkills, Object listSkillModel) {
        super(toolCard("list_skill", "list_skill", "List enabled skills or return a fallback selection set."), null);
        this.getSkills = getSkills;
        this.listSkillModel = listSkillModel;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        try {
            String query = inputs != null && inputs.get("query") != null
                    ? String.valueOf(inputs.get("query")).trim()
                    : "";
            Map<String, Object> data = new LinkedHashMap<>();
            if (query.isBlank()) {
                data.put("skills", dumpAllSkills());
                data.put("mode", "all");
                return new ToolOutput(true, data, null);
            }

            if (listSkillModel == null) {
                data.put("skills", dumpAllSkills());
                data.put("mode", "all");
                data.put("message", "list_skill_model is not configured, fallback to all skills.");
                return new ToolOutput(true, data, null);
            }

            List<String> selectedNames = routeSkills(query);
            List<Skill> selectedSkills = selectSkillsByNames(selectedNames);
            data.put("skills", dumpSkills(selectedSkills));
            data.put("mode", "filtered");
            data.put("selected_skill_names", selectedSkills.stream().map(Skill::getName).toList());
            return new ToolOutput(true, data, null);
        } catch (Exception exc) {
            return new ToolOutput(false, null, exc.getMessage());
        }
    }

    private List<Map<String, Object>> dumpAllSkills() {
        return dumpSkills(getSkills.get());
    }

    private List<Map<String, Object>> dumpSkills(List<Skill> skills) {
        List<Skill> safeSkills = skills != null ? skills : List.of();
        return safeSkills.stream().map(skill -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", skill.getName());
            item.put("description", skill.getDescription());
            item.put("directory", skill.getDirectory());
            item.put("skill_md_path", normalizePath(Path.of(skill.getDirectory()).resolve("SKILL.md").toString()));
            return item;
        }).toList();
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/');
    }

    private List<String> routeSkills(String query) throws Exception {
        Object response = invokeRoutingModel(query);
        return parseSelectedSkillNames(readContent(response));
    }

    private Object invokeRoutingModel(String query) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SkillsSection.getListSkillSystemPrompt("cn")),
                Map.of("role", "user", "content", "User task:\n" + query
                        + "\n\nAvailable skills:\n" + MAPPER.writeValueAsString(dumpAllSkills())
                        + "\n\nReturn only the names of the skills that are relevant to the task.")
        ));
        for (Method method : listSkillModel.getClass().getMethods()) {
            if (!"invoke".equals(method.getName())) {
                continue;
            }
            method.setAccessible(true);
            if (method.getParameterCount() == 1) {
                return method.invoke(listSkillModel, payload);
            }
            if (method.getParameterCount() == 0) {
                return method.invoke(listSkillModel);
            }
        }
        throw new IllegalStateException("list_skill_model does not expose invoke()");
    }

    private static String readContent(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof String text) {
            return text;
        }
        Object value = invokeGetter(response, "getContent");
        if (value == null) {
            value = readField(response, "content");
        }
        return value != null ? String.valueOf(value) : "";
    }

    private static Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    protected static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exc) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", exc);
            }
        }
        return null;
    }

    private static List<String> parseSelectedSkillNames(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isBlank()) {
            return List.of();
        }
        if (text.startsWith("```")) {
            List<String> lines = new ArrayList<>(text.lines().toList());
            if (!lines.isEmpty()) {
                lines.remove(0);
            }
            if (!lines.isEmpty() && "```".equals(lines.get(lines.size() - 1).trim())) {
                lines.remove(lines.size() - 1);
            }
            text = String.join("\n", lines).trim();
        }
        try {
            Map<String, Object> data = MAPPER.readValue(text, new TypeReference<>() {
            });
            Object names = data.get("skills");
            if (!(names instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Skill> selectSkillsByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Map<String, Skill> byName = new LinkedHashMap<>();
        for (Skill skill : getSkills.get()) {
            byName.put(skill.getName(), skill);
        }
        List<Skill> selected = new ArrayList<>();
        for (String name : names) {
            Skill skill = byName.get(name);
            if (skill != null) {
                selected.add(skill);
            }
        }
        return selected;
    }
}
