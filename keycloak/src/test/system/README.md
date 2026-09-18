# SCIM Search Benchmark

Run the opt-in benchmark against the system-test Keycloak/Postgres containers:

```sh
SCIM_SEARCH_BENCHMARK=true ./gradlew systemTest --tests '*ScimSearchBenchmarkTest' --rerun-tasks
```

Run from `keycloak/` with Java 21 and Docker available. The existing system-test
environment builds the application and resets the test realm; it does not target
an external server. Normal `systemTest` runs skip the benchmark.

Run either test independently:

```sh
SCIM_SEARCH_BENCHMARK=true ./gradlew systemTest --tests '*ScimSearchBenchmarkTest.benchmark SCIM searches' --rerun-tasks
SCIM_SEARCH_BENCHMARK=true ./gradlew systemTest --tests '*ScimSearchBenchmarkTest.benchmark SCIM traversal' --rerun-tasks
```

Each test seeds and cleans up its own dataset. Selecting the class runs both tests.

The test lives under `no.novari.test.system.benchmark.scim`, separate from E2E
workflows, while reusing the system-test environment and Gradle task.

Defaults: 10,000 users seeded through Keycloak Admin partial import in batches of
500, 5 warm-ups per scenario, and 30 measured
requests per scenario. Override with `SCIM_BENCHMARK_USERS` (minimum 1,000),
`SCIM_BENCHMARK_WARMUPS`, and `SCIM_BENCHMARK_ITERATIONS`.

The traversal test's `full-pagination-100` scenario reads all users using offset pagination.
Set `SCIM_BENCHMARK_PAGE_SIZE` to change its page size (default 100). Each warm-up
and measured iteration is a complete traversal, verifying that every seeded user
appears exactly once. Its CSV timings are the sum of all page request times per
traversal, excluding JSON parsing and assertions, not the latency of a single page.

Scenarios cover first/deep pages, count-only searches, username/external ID/email
equality, complex email value filters, substring matching, active users, roles,
and no matches. Every response is checked for its total and page size; unique
matches are also checked for the expected username. Users are deleted afterward,
including when a search fails, through the Admin API. Each imported user has the
`scim-managed` realm role, organization membership, and SCIM backing attributes.
Setup requires every import and membership assignment to succeed; it does not
tolerate a partially populated dataset. SCIM is used only for measured/warm-up searches.
The shared `KcAdminClient` utility provides user creation, batch seeding, and
best-effort cleanup for both integration and system tests. Call cleanup from a
`finally` block so partially seeded datasets are also removed.

Timings include HTTP round trips and full response-body reads, but exclude
provisioning, token fetching, JSON assertions, and cleanup. Requests are sequential
and reuse connections; this is a search-latency benchmark, not a concurrent load
test. All searches sort by username. The existing environment enables Kover in
Keycloak, so compare runs on the same machine/configuration rather than treating
these as production latency estimates. There are no machine-dependent pass/fail
latency thresholds.

Min, median, p95 (nearest rank), and max milliseconds are printed and written to
`build/reports/scim-search-benchmark/searches.csv` and `traversal.csv`, respectively.
Each test replaces only its own CSV report.
