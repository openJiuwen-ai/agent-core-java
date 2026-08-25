/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.curation;

import examples.gitcode_issue_evolver.webhook.GitCodeWebhookVerifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates untrusted curator output against admitted CodeCheck rule identifiers.
 *
 * @since 0.1.12
 */
public final class CodingStandardCurationValidator {
    private static final int MAX_LESSONS = 8;
    private static final int MIN_TEXT_LENGTH = 8;
    private static final int MAX_TEXT_LENGTH = 500;
    private static final Pattern RULE_ID = Pattern.compile("G\\.[A-Z]+\\.[0-9]+");
    private static final Pattern UNSAFE_TEXT = Pattern.compile(
            "(?i)(https?://|src/|[a-z]:\\\\|/(?:home|root|opt|etc)/|"
                    + "api[_ -]?key|access[_ -]?token|password|secret|cookie|bearer)");

    private CodingStandardCurationValidator() {
    }

    /**
     * Validate and fingerprint reusable lessons.
     *
     * @param task admitted finding evidence
     * @param result untrusted Agent result
     * @return accepted immutable lessons
     */
    public static List<CodingStandardLesson> validate(CodingStandardCurationTask task,
                                                       CodingStandardCurationResult result) {
        if (result.status() == CodingStandardCurationResult.Status.INVALID_OUTPUT) {
            throw new IllegalArgumentException("Curator returned invalid output");
        }
        if (result.status() == CodingStandardCurationResult.Status.NO_UPDATE) {
            if (!result.lessons().isEmpty()) {
                throw new IllegalArgumentException("NO_UPDATE must not include lessons");
            }
            return List.of();
        }
        if (result.lessons().isEmpty() || result.lessons().size() > MAX_LESSONS) {
            throw new IllegalArgumentException("Curator lesson count is outside policy");
        }
        Map<String, CodingStandardFindingEvidence> evidence = evidenceByRule(task.findings());
        List<CodingStandardLesson> accepted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CodingStandardCurationResult.LessonDraft draft : result.lessons()) {
            CodingStandardLesson lesson = validateDraft(draft, evidence);
            if (seen.add(lesson.fingerprint())) {
                accepted.add(lesson);
            }
        }
        return List.copyOf(accepted);
    }

    private static Map<String, CodingStandardFindingEvidence> evidenceByRule(
            List<CodingStandardFindingEvidence> findings) {
        Map<String, CodingStandardFindingEvidence> result = new HashMap<>();
        for (CodingStandardFindingEvidence finding : findings) {
            if (finding.ruleId() != null && RULE_ID.matcher(finding.ruleId().strip()).matches()) {
                result.putIfAbsent(finding.ruleId().strip(), finding);
            }
        }
        return Map.copyOf(result);
    }

    private static CodingStandardLesson validateDraft(
            CodingStandardCurationResult.LessonDraft draft,
            Map<String, CodingStandardFindingEvidence> evidence) {
        String ruleId = requiredText(draft.ruleId(), "ruleId");
        if (!evidence.containsKey(ruleId) || !RULE_ID.matcher(ruleId).matches()) {
            throw new IllegalArgumentException("Curator proposed a rule absent from evidence");
        }
        String category = ruleId.substring(0, ruleId.lastIndexOf('.'));
        if (!category.equals(requiredText(draft.category(), "category"))) {
            throw new IllegalArgumentException("Curator category does not match ruleId");
        }
        String summary = safeGuidance(draft.summary(), "summary");
        String prevention = safeGuidance(draft.prevention(), "prevention");
        String material = ruleId + '\n' + summary + '\n' + prevention;
        String fingerprint = GitCodeWebhookVerifier.sha256(material.getBytes(StandardCharsets.UTF_8));
        return new CodingStandardLesson(fingerprint, ruleId, category, summary, prevention);
    }

    private static String safeGuidance(String value, String name) {
        String text = requiredText(value, name);
        if (text.length() < MIN_TEXT_LENGTH || text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Curator " + name + " length is outside policy");
        }
        if (UNSAFE_TEXT.matcher(text).find() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Curator " + name + " contains forbidden content");
        }
        return text;
    }

    private static String requiredText(String value, String name) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Curator " + name + " is required");
        }
        return text;
    }
}
