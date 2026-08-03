package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.agent_evolving.agent_rl.online.rail.llm_response} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/llm_response.py}.
 */
public final class LlmResponseUtils {

    private LlmResponseUtils() {
    }

    public static List<Double> extractLogprobs(Object response) {
        if (response == null) {
            return null;
        }
        Map<?, ?> responseJson = providerResponseJson(response);
        Map<?, ?> choice = firstChoice(responseJson);
        Object direct = choice != null ? choice.get("logprobs") : null;
        if (direct == null && responseJson != null) {
            direct = responseJson.get("logprobs");
        }
        if (direct == null) {
            return null;
        }
        if (direct instanceof List<?> directList) {
            return extractDoubleList(directList);
        }

        Object content = nestedValue(direct, "content");
        if (content instanceof List<?> contentList) {
            List<Double> values = new ArrayList<>();
            for (Object item : contentList) {
                Double logprob = asDouble(nestedValue(item, "logprob"));
                if (logprob != null) {
                    values.add(logprob);
                }
            }
            return values.isEmpty() ? null : values;
        }
        return null;
    }

    public static List<Integer> extractTokenIds(Object response) {
        if (response == null) {
            return null;
        }
        return extractIntList(response, null, "completion_token_ids", "token_ids", "response_tokens");
    }

    public static List<Integer> extractPromptIds(Object response) {
        if (response == null) {
            return null;
        }
        return extractIntList(response, null, "prompt_token_ids", "prompt_ids");
    }

    private static Map<?, ?> providerResponseJson(Object response) {
        if (response instanceof Map<?, ?> map) {
            return map;
        }
        Object metadata = nestedValue(response, "metadata");
        return metadata instanceof Map<?, ?> map ? map : null;
    }

    private static Map<?, ?> firstChoice(Map<?, ?> responseJson) {
        if (responseJson == null) {
            return null;
        }
        Object choices = responseJson.get("choices");
        if (!(choices instanceof List<?> choiceList) || choiceList.isEmpty()) {
            return null;
        }
        Object first = choiceList.get(0);
        return first instanceof Map<?, ?> map ? map : null;
    }

    private static List<Integer> extractIntList(Object response, String runtimeField, String... fieldNames) {
        Map<?, ?> responseJson = providerResponseJson(response);
        List<Object> candidates = new ArrayList<>();
        if (runtimeField != null && responseJson != null) {
            candidates.add(responseJson.get(runtimeField));
        }
        Map<?, ?> choice = firstChoice(responseJson);
        if (choice != null) {
            for (String fieldName : fieldNames) {
                candidates.add(choice.get(fieldName));
            }
        }
        if (responseJson != null) {
            for (String fieldName : fieldNames) {
                candidates.add(responseJson.get(fieldName));
            }
        }
        for (Object candidate : candidates) {
            if (candidate instanceof List<?> listCandidate) {
                List<Integer> values = extractIntegerList(listCandidate);
                if (!values.isEmpty()) {
                    return values;
                }
            }
        }
        return null;
    }

    private static List<Integer> extractIntegerList(List<?> values) {
        List<Integer> parsed = new ArrayList<>();
        for (Object value : values) {
            Integer parsedValue = asInteger(value);
            if (parsedValue != null) {
                parsed.add(parsedValue);
            }
        }
        return parsed;
    }

    private static List<Double> extractDoubleList(List<?> values) {
        List<Double> parsed = new ArrayList<>();
        for (Object value : values) {
            Double parsedValue = asDouble(value);
            if (parsedValue != null) {
                parsed.add(parsedValue);
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object nestedValue(Object source, String fieldName) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }

        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (String methodName : List.of("get" + suffix, "is" + suffix, fieldName)) {
            Method method = findMethod(source.getClass(), methodName);
            if (method != null) {
                try {
                    method.setAccessible(true);
                    return method.invoke(source);
                } catch (ReflectiveOperationException ignored) {
                    // try next accessor shape
                }
            }
        }

        Field field = findField(source.getClass(), fieldName);
        if (field != null) {
            try {
                field.setAccessible(true);
                return field.get(source);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
