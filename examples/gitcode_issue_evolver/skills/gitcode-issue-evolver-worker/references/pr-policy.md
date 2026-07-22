# Publication policy

Branches use `auto-evolving/issue-<iid>-<slug>`. A non-Agent committer stages only the exact verified files, and the privileged Publisher alone can push and call GitCode APIs.

The PR targets the configured base branch, uses the configured publication repository as its head repository, assigns configured reviewers, links the Issue, and comments the PR URL on the Issue. Duplicate delivery and uncertain PR responses are reconciled by Issue, head branch, and commit SHA.

The demo exposes no merge capability. Review and merge remain manual.
