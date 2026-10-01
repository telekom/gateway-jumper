---
name: pr-code-review
description: Review gateway-jumper pull requests for defects in reactive routing, header trust, OAuth and JWT handling, failover, TLS, configuration, tests, and documentation. Use only when running as the GitHub Copilot Cloud Agent and when reviewing a GitHub pull request. In other cases, prefer other global code review skills to this one.
license: Apache-2.0
---

# Pull request code review

Review only the change under consideration. Find defects that the author should
fix before merge. Read `README.md` to gain understanding of how jumper works together with the other gateway components. Apply the engineering rules in `AGENTS.md`. Do not propose
broad rewrites or style preferences.

## 1. Establish intent

Read the pull request title and description, linked issue, commits, full diff,
repository instructions, and enough surrounding code to understand each changed
path. Use the intended behavior as the review criterion.

Focus on behavior introduced or changed by the pull request. Mention a
pre-existing problem only when the change makes it newly reachable or materially
worse.

## 2. Check gateway invariants

Apply every group touched by the change.

### Reactive request lifecycle

- Preserve a non-blocking WebFlux flow. Check for blocking calls on event-loop
  threads, unmanaged subscriptions or executors, lost cancellation or Reactor
  context, and side effects outside the returned publisher.
- Check filter ordering against the order constants and
  `GATEWAY_REQUEST_URL_ATTR`. Routing, OAuth, header removal, plaintext
  validation, body rewriting, Spectre, and response handling depend on order.
- Keep request and response body buffering within
  `spring.http.codecs.max-in-memory-size`. Check unknown content lengths,
  streaming bodies, empty bodies, and cancellation.
- When retry behavior changes, check whether non-idempotent requests or external
  side effects can run more than once.

### Routing and trust boundaries

- Trace `/proxy`, `/listener`, and `/autoevent` separately. They use different
  filters and must preserve raw path and query semantics when constructing the
  upstream URI.
- Treat `jumper_config`, `routing_config`, routing URLs, OAuth headers, forwarded
  headers, and token-exchange headers as security-sensitive inputs. Establish
  whether each value comes from Kong, the control plane, configuration, or the
  caller before trusting it.
- Preserve deliberate first-value and last-value header semantics. Check
  duplicate, blank, missing, and conflicting headers.
- Ensure credentials, internal routing headers, consumer metadata, and
  configuration headers are removed on the paths where they are consumed and do
  not leak to providers.
- Reject new logging of authorization headers, tokens, client secrets, private
  keys, passwords, or cache keys derived from credentials.

### Authentication and token behavior

- Trace every affected mode: mesh token exchange, external OAuth, Basic Auth,
  Last Mile Security, access-token forwarding, and `x-token-exchange`.
- Preserve OAuth credential precedence and blank-value handling. Check that
  token cache keys include every input that changes a token, concurrent misses
  coalesce, failed requests leave no stale in-flight entry, and upstream 401/403
  responses evict the correct token.
- Preserve JWT claim meaning and wire shape, including issuer, audience
  precedence, string-versus-array `aud`, expiration, `kid`, RS256, and the
  minimum RSA key size.

### Failover and shared state

- Trace primary, secondary, skipped-zone, and unhealthy-zone routes. Check
  routing choice, audit logging, realm derivation, forwarded headers, and cleanup
  of failover metadata.
- Check load-balancing boundary values and empty or zero-weight server lists.
- For Redis zone-health changes, check optional startup, reconnect and retry
  behavior, duplicate listener registration, thread lifecycle, malformed
  messages, and the configured default health state.
- Keep per-request state in `ExchangeStateManager` unless a framework attribute
  is required. Check that state cannot leak between exchanges.

### TLS, configuration, and observability

- Preserve the documented `strict`, `warn`, and `insecure` certificate and
  plaintext modes. A default change or fallback must be explicit and tested.
- Keep environment-variable defaults and Spring profile behavior compatible.
  Exactly one tracing exporter must be active while B3 propagation remains
  compatible with Kong.
- Check metrics for unbounded tag values and traces or logs for sensitive data.
- Treat the Jib base-image digest as workflow-managed. Expect updates from
  `.github/workflows/update-base-image.yml`; scrutinize unrelated manual edits.

### Documentation

- When behavior, routes, configuration, deployment, or operational procedures
  change, verify that `README.md` and affected documentation remain accurate.

### Tests

- Match routing, headers, tokens, failover, path handling, and body behavior to
  the Cucumber features under `src/test/resources/features`.
- Match service, configuration, cryptography, Redis, and utility changes to the
  focused JUnit tests under `src/test/java`.
- Require integration coverage when behavior crosses WebFlux filters, an IdP,
  Redis, TLS, or an upstream server.
- Use the commands in `AGENTS.md` when tool access permits. Report only commands
  whose result was observed.

## 3. Verify each finding

Before reporting a finding:

1. Show a reachable caller, input, or runtime path.
2. Confirm that validation, types, framework behavior, or surrounding code does
   not prevent it.
3. Check repository instructions and conventions.
4. Identify the smallest correct fix direction.

Reject hypothetical states, duplicated findings, preference-only rewrites, and
issues outside the pull request's control. Scrutinize correctness and security
claims even when the rest of the change looks sound.

## 4. Report actionable findings

Place each comment on the changed line nearest its root cause. Explain the
triggering scenario, concrete impact, and minimal fix direction. Keep one root
cause per comment and avoid praise or summary comments that require no action.

Prioritize release-blocking and materially unsafe behavior, then reachable
defects and significant missing requirements. Report bounded maintainability
risks only when they have concrete impact. A review with no actionable comments
is a valid result.

The review is complete when every touched gateway invariant and applicable
engineering rule has been assessed, and every reported finding is reachable,
evidenced, and caused by the pull request.
