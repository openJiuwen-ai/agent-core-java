/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * Validated target, publication, and baseline repository coordinates for GitCode automation.
 *
 * @param targetOwner target repository owner
 * @param targetName target repository name
 * @param publishOwner publication repository owner
 * @param publishName publication repository name
 * @param baseBranch target repository baseline branch
 * @since 0.1.12
 */
public record RepositoryCoordinates(
        String targetOwner,
        String targetName,
        String publishOwner,
        String publishName,
        String baseBranch) {
    /** Default target repository retained for the production profile. */
    public static final String DEFAULT_TARGET_REPOSITORY = "openJiuwen/agent-core-java";

    /** Default baseline branch retained for the production profile. */
    public static final String DEFAULT_BASE_BRANCH = "develop";
    private static final Pattern REPOSITORY_COMPONENT_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}");
    private static final Pattern BRANCH_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,127}");
    private static final Pattern HIDDEN_BRANCH_COMPONENT_PATTERN = Pattern.compile("(^|/)\\.");

    /**
     * Validate repository components and the baseline branch.
     */
    public RepositoryCoordinates {
        targetOwner = requireRepositoryComponent(targetOwner, "target owner");
        targetName = requireRepositoryComponent(targetName, "target repository name");
        publishOwner = requireRepositoryComponent(publishOwner, "publish owner");
        publishName = requireRepositoryComponent(publishName, "publish repository name");
        baseBranch = requireBaseBranch(baseBranch);
    }

    /**
     * Parse canonical target and publication repository paths.
     *
     * @param targetRepository target repository in owner/name form
     * @param publishRepository publication repository in owner/name form
     * @param baseBranch target baseline branch
     * @return validated repository coordinates
     * @throws IllegalArgumentException when a repository or branch is invalid
     */
    public static RepositoryCoordinates from(String targetRepository, String publishRepository,
                                             String baseBranch) {
        RepositoryParts target = parseRepository(targetRepository, "target repository");
        RepositoryParts publish = parseRepository(publishRepository, "publish repository");
        return new RepositoryCoordinates(target.owner(), target.name(), publish.owner(), publish.name(),
                baseBranch);
    }

    /**
     * Return the backward-compatible production target and baseline defaults.
     *
     * @return default same-repository coordinates
     */
    public static RepositoryCoordinates defaults() {
        return from(DEFAULT_TARGET_REPOSITORY, DEFAULT_TARGET_REPOSITORY, DEFAULT_BASE_BRANCH);
    }

    /**
     * Check a canonical owner/name repository value without accepting a URL.
     *
     * @param repository repository path to validate
     * @return whether the value is a valid repository path
     */
    public static boolean isValidRepository(String repository) {
        if (!isUnmodifiedText(repository)) {
            return false;
        }
        int separator = repository.indexOf('/');
        return separator > 0
                && separator == repository.lastIndexOf('/')
                && separator < repository.length() - 1
                && REPOSITORY_COMPONENT_PATTERN.matcher(repository.substring(0, separator)).matches()
                && REPOSITORY_COMPONENT_PATTERN.matcher(repository.substring(separator + 1)).matches();
    }

    /**
     * Check a baseline branch against the restricted Git reference policy.
     *
     * @param branch branch to validate
     * @return whether the branch is valid
     */
    public static boolean isValidBaseBranch(String branch) {
        return isUnmodifiedText(branch)
                && BRANCH_PATTERN.matcher(branch).matches()
                && !branch.endsWith("/")
                && !branch.endsWith(".")
                && !branch.endsWith(".lock")
                && !branch.contains("..")
                && !branch.contains("//")
                && !HIDDEN_BRANCH_COMPONENT_PATTERN.matcher(branch).find();
    }

    /**
     * Return the canonical target repository path.
     *
     * @return target owner/name
     */
    public String targetRepository() {
        return targetOwner + "/" + targetName;
    }

    /**
     * Return the canonical publication repository path.
     *
     * @return publication owner/name
     */
    public String publishRepository() {
        return publishOwner + "/" + publishName;
    }

    /**
     * Report whether generated branches and pull requests remain in one repository.
     *
     * @return {@code true} when target and publication repositories are identical
     */
    public boolean sameRepository() {
        return targetOwner.equals(publishOwner) && targetName.equals(publishName);
    }

    /**
     * Build the fixed-host target repository HTTPS clone URI.
     *
     * @return safe target clone URI
     */
    public URI targetCloneUri() {
        return cloneUri(targetRepository());
    }

    /**
     * Build the fixed-host publication repository HTTPS clone URI.
     *
     * @return safe publication clone URI
     */
    public URI publishCloneUri() {
        return cloneUri(publishRepository());
    }

    /**
     * Build the target repository API root below the fixed GitCode API base.
     *
     * @return relative target API root
     */
    public String targetApiPath() {
        return "repos/" + targetRepository();
    }

    private static URI cloneUri(String repository) {
        return URI.create("https://gitcode.com/" + repository + ".git");
    }

    private static RepositoryParts parseRepository(String repository, String name) {
        if (!isValidRepository(repository)) {
            throw new IllegalArgumentException(name + " must use a valid owner/name form");
        }
        String value = repository;
        int separator = value.indexOf('/');
        String owner = requireRepositoryComponent(value.substring(0, separator), name + " owner");
        String repositoryName = requireRepositoryComponent(value.substring(separator + 1), name + " name");
        return new RepositoryParts(owner, repositoryName);
    }

    private static String requireRepositoryComponent(String component, String name) {
        String value = requireUnmodifiedText(component, name);
        if (!REPOSITORY_COMPONENT_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " contains invalid characters");
        }
        return value;
    }

    private static String requireBaseBranch(String branch) {
        if (!isValidBaseBranch(branch)) {
            throw new IllegalArgumentException("base branch is invalid");
        }
        return branch;
    }

    private static String requireUnmodifiedText(String value, String name) {
        if (!isUnmodifiedText(value)) {
            throw new IllegalArgumentException(name + " is required and must not contain surrounding whitespace");
        }
        return value;
    }

    private static boolean isUnmodifiedText(String value) {
        return value != null && !value.isBlank() && value.equals(value.trim());
    }

    private record RepositoryParts(String owner, String name) {
    }
}
