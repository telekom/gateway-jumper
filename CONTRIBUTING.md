<!--
SPDX-FileCopyrightText: 2026 Deutsche Telekom AG

SPDX-License-Identifier: CC0-1.0
-->

# Contributing

All contributors must follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Build and test

Jumper requires Java 25. Use the Maven wrapper so the build uses the repository's
Maven version:

```bash
./mvnw clean package
```

The test suite uses Docker through Testcontainers and pulls its test images from
the network. Start Docker before running the build, allow Maven and Docker network
access, and leave local ports `1080`, `1081`, and `1082` available for the
Cucumber WireMock servers. Testcontainers and Spring Boot also allocate dynamic
host ports.

Run one test with a clean build directory:

```bash
./mvnw clean test -Dtest=TestClassName
```

See [Building](README.md#building) for application and OCI image build commands.

## Git hooks

The repository includes an optional [Lefthook](https://lefthook.dev/)
configuration. Install the hooks once in each clone:

```bash
lefthook install
```

The installed hook wrapper reports a missing Lefthook binary instead of silently
skipping checks. This does not install hooks or enforce Lefthook in clones where
you have not run `lefthook install`. CI remains the authoritative validation gate.

Install these tools before using the hooks:

- [Lefthook](https://lefthook.dev/#how-to-install-lefthook) 2.0.4 or newer
- [Gitleaks](https://github.com/gitleaks/gitleaks#installing) 8.20.0 or newer
- [committed](https://github.com/crate-ci/committed#install)
- [REUSE](https://github.com/fsfe/reuse-tool#install) 4.0.0 or newer
- Java 25

The hooks run these read-only checks:

| Hook | Check |
| --- | --- |
| `pre-commit` | Run Spotless when staged Java or Maven build inputs change, run `reuse lint` over the whole repository, and scan staged content with Gitleaks. These jobs run in parallel. |
| `commit-msg` | Validate the commit message with `committed --commit-file <path>` and require a lowercase type, except during merges and rebases. |
| `pre-push` | Always check formatting without running the full build or test suite. |

Spotless checks all configured Java sources even though staged paths decide
whether the pre-commit job runs. The pre-push hook repeats the check to cover
changes introduced without a pre-commit hook, such as rebases and cherry-picks.
Neither hook rewrites source files. Gitleaks uses `gitleaks git --staged`, so it
scans staged content rather than the full history or unstaged working tree. Do
not add broad secret allowlists. Any exclusion must address a demonstrated false
positive, remain as narrow as possible, and receive review. If a
`.gitleaks.toml` becomes necessary, retain the default rules with
`[extend] useDefault = true`.

To bypass an installed hook for one command, set `LEFTHOOK=0` or use Git's
`--no-verify` option:

```bash
LEFTHOOK=0 git commit ...
git commit --no-verify ...
LEFTHOOK=0 git push ...
git push --no-verify ...
```

Use a bypass only when necessary. CI still runs its required checks.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/). The accepted
types match [`release.config.js`](release.config.js):

`feat`, `fix`, `build`, `chore`, `ci`, `docs`, `perf`, `refactor`, `revert`,
`style`, and `test`.

Types must be lowercase. Scopes are optional and unrestricted. Both the `!`
marker and a `BREAKING CHANGE:` footer trigger a major release for every accepted
type. Merge commits skip local message validation. Rewrite Git's generated revert
subject as `revert: <subject>` so semantic-release recognizes it. The policy does
not restrict subject capitalization, punctuation, imperative mood, or line
length beyond the Conventional Commits structure.

## Releases

See [Releases](README.md#releases) for branch roles and the automatic release
process. Because every accepted commit type can publish a release, choose the
type that describes the change.
