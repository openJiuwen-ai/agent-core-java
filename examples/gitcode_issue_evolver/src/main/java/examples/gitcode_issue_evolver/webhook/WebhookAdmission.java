/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.webhook;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fail-closed admission policy for the GitCode demo.
 *
 * @since 0.1.12
 */
public final class WebhookAdmission {
    private final boolean enabled;
    private final Set<String> allowedRepositories;

    /**
     * Create a demo admission policy.
     *
     * @param enabled whether webhook automation is explicitly enabled
     * @param allowedRepositories exact GitCode repository paths accepted by this endpoint
     */
    public WebhookAdmission(boolean enabled, Collection<String> allowedRepositories) {
        this.enabled = enabled;
        this.allowedRepositories = normalize(allowedRepositories);
    }

    /**
     * Return a policy that rejects all remote events.
     *
     * @return disabled admission policy
     */
    public static WebhookAdmission disabled() {
        return new WebhookAdmission(false, Set.of());
    }

    /**
     * Check whether one repository is on the explicit demo allowlist.
     *
     * @param repository repository path from the verified webhook
     * @return whether the repository is allowed while demo mode is enabled
     */
    public boolean allowsRepository(String repository) {
        return enabled && repository != null && allowedRepositories.contains(repository);
    }

    /**
     * Check whether an Issue webhook can create a demo job.
     *
     * @param event normalized Issue event
     * @return whether the event is an allowlisted update that newly adds bug
     */
    public boolean allowsIssue(GitCodeIssueEvent event) {
        return event != null && allowsRepository(event.repository()) && event.eligible();
    }

    /**
     * Check whether a Pull Request webhook can update an existing demo job.
     *
     * @param event normalized Pull Request event
     * @return whether the repository is allowlisted
     */
    public boolean allowsPullRequest(GitCodePullRequestEvent event) {
        return event != null && allowsRepository(event.repository());
    }

    private static Set<String> normalize(Collection<String> repositories) {
        Set<String> normalized = new LinkedHashSet<>();
        if (repositories != null) {
            repositories.stream()
                    .filter(repository -> repository != null && !repository.isBlank())
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        return Set.copyOf(normalized);
    }
}
