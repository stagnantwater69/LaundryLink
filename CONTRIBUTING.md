# Contributing to LaundryLink

All members must work on a separate branch and open a pull request into main. Ken (@stagnantwater69) must approve member changes before merging.

## Workflow

```bash
git switch main
git pull --ff-only origin main
git switch -c feature/your-module
# Make and test your changes.
git add <changed-files>
git commit -m "Describe your change"
git push -u origin feature/your-module
```

Open a pull request targeting main. Describe the changes and how they were tested. Wait for Ken's approval. New commits require another review when stale approvals are dismissed.

Do not push directly to main, force-push main, delete main, commit credentials, or merge without required approval.

## Enforcement setup (repository owner)

This document and CODEOWNERS do not enforce branch protection by themselves. After merging CODEOWNERS into main, configure an active branch ruleset targeting refs/heads/main:

- Require a pull request before merging.
- Require at least one approving review.
- Require review from Code Owners.
- Dismiss stale approvals when new commits are pushed.
- Require conversation resolution.
- Block force pushes and deletion.
- Give collaborators no bypass permissions.

Ken cannot approve his own pull requests. If an owner exception is needed, configure only the repository administrator for pull-request-only bypass, keep Ken as the sole administrator, and continue using pull requests for owner changes. Do not grant collaborators administrator access.
