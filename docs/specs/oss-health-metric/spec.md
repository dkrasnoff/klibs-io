# Spec: OSS Health Metric

**Input:** verbatim seed text — preserved for traceability

> We need to implement OSS Health metric for project. Here is RFC, that contains a section describing this metric: `/Users/nikita.vlaev/Downloads/KTL-4246 Exploratory research for author-faced insights.md`. Do a thorough research on the mentioned papers and align the metric to the klibs.io usecase.

## 1. Goal
Compute and expose a per-project **OSS Health** score (0–100) — a composite, research-backed signal of repository activity and responsiveness — so that visitors of a klibs.io project page can judge whether a library is actively maintained without needing to skim GitHub themselves.

## 2. Problem
- klibs.io currently exposes only point-in-time GitHub fields per repository: `stars`, `open_issues`, `last_activity_ts`, `license`. None of these on their own tell a visitor whether a library is being actively maintained — a high-star library can be abandoned, a low-star one can be healthy.
- The research the RFC cites ([Iqbal et al., 2023](https://arxiv.org/abs/2309.12120v3)) shows that single raw indicators (stars, commit recency, issue counts) do not reliably identify OSS sustainability. A composite is needed.
- **Affected:** end users of klibs.io evaluating library trustworthiness; library maintainers who want a neutral, defensible signal of their project's activity surfaced on the platform.

## 3. User scenarios & acceptance

### Scenario 1 — Visitor sees health on a project page (P1)
- **Given:** a project whose backing GitHub repo has at least 12 weeks of recorded activity and the OSS Health score has been computed.
- **When:** the visitor opens the project details page (`/project/{ownerLogin}/{projectName}/details`).
- **Then:** the response includes an `ossHealth` field with an integer 0–100, plus the timestamp at which it was last computed.
- **Independent test:** seed `scm_repo` + the new snapshot/stats tables with a fixture worth of activity; call the details endpoint; assert `ossHealth` is present and within [0, 100].

### Scenario 2 — Insufficient data is reported, not hidden silently (P1)
- **Given:** a project whose backing repo has fewer than 12 weeks of recorded commit/issue/PR activity, OR for which the heavy weekly job has not yet succeeded.
- **When:** the visitor opens the project details page.
- **Then:** `ossHealth` is `null` (or carries an explicit "insufficient data" indicator — see FR-008) and a machine-readable reason is conveyed so the frontend can render *"Insufficient activity data"* rather than a misleading "0".
- **Independent test:** seed a brand-new repo with one commit; assert the field is `null`/insufficient and not `0`.

### Scenario 3 — Health updates on a predictable cadence (P1)
- **Given:** a project that has had an OSS Health score computed.
- **When:** the weekly recomputation job runs.
- **Then:** the score is updated using the latest 12-week window of commit/issue/PR/contributor data, and the stored `computed_at` timestamp moves forward.
- **Independent test:** run the job in a test harness with a stubbed `GitHubIntegration`; assert the score row is upserted with the new `computed_at`.

### Scenario 4 — Rate-limit-aware refresh (P1)
- **Given:** the heavy weekly job is running through a backlog of projects, each costing ~5–10 GitHub API calls.
- **When:** the remaining GitHub rate-limit budget drops below a safe threshold.
- **Then:** the job pauses/yields without failing the overall scheduled run, and resumes on the next iteration without dropping any project.
- **Independent test:** unit test the job loop with a fake `GitHubIntegration.getRateLimitInfo()` that returns low remaining; assert the loop exits cleanly and the un-processed projects are still queued.

### Edge cases
- **Repo younger than 12 weeks:** report insufficient data. Do not extrapolate (per RFC's "negative impact risk" note — a misleadingly low score is worse than no score).
- **Repo with zero issues / zero PRs over the window:** the Issue and PR sub-scores have undefined ratios. We treat the sub-component as `0` for the ratio term but **must not** zero out the entire `I` or `P` — see FR-009.
- **Repo with one contributor:** `TopContributorCommitShare = 1.0`, so the diversity sub-score becomes `0.6 * (1/5) + 0.4 * 0 = 0.12`. This is intended; a one-person project legitimately scores low on diversity.
- **Archived / disabled GitHub repo:** [NEEDS CLARIFICATION: do we skip computation for archived repos (and clear any existing score), or compute as normal so the score decays naturally?]
- **GitHub returns 202 for `/stats/participation` or `/stats/contributors`** (stats not yet cached on GitHub's side): retry on the next weekly run; do not fail the job. The previously-computed score is retained until the next successful run.
- **Repository renamed / transferred:** the `nativeId` (numeric repo ID) is stable across renames; snapshots and history must key on `nativeId`, not on `owner/name`.
- **klibs egress NAT IP pool is shared** between `klibs-stage` and `klibs-features`; with ~5–10 calls per repo per week and N projects, the heavy job must stay within the shared GitHub PAT budget (5,000 req/hr authenticated) and yield well before exhaustion — see NFR-Rate-limits.

## 4. Functional requirements

- **FR-001:** System MUST compute an OSS Health score in `[0, 100]` per scm-repository, defined as `100 * (0.30*C + 0.25*I + 0.25*P + 0.20*A)`, where the four sub-scores `C, I, P, A ∈ [0, 1]` are computed per the formula below.
- **FR-002:** System MUST compute `C` (Commit Consistency) over the last 12 weeks of weekly commit buckets from GitHub `GET /repos/{owner}/{repo}/stats/participation` as `C = max(0, 1 - CV / 0.6)` where `CV = stdev(weekly_commits) / mean(weekly_commits)`. If `mean == 0`, then `C = 0` (no commits = no consistency).
- **FR-003:** System MUST compute `I` (Issue Responsiveness) as `I = 0.5 * min(1, IssueCloseRatio / 0.4) + 0.5 * max(0, 1 - MedianIssueCloseDays / 21)`, where:
    - `IssueCloseRatio = issues_closed_in_window / issues_opened_in_window` over the last 12 weeks, derived from the daily diff snapshots.
    - `MedianIssueCloseDays` is computed by paginating issues closed within the last 12 weeks (`GET /repos/{owner}/{repo}/issues?state=closed&since=...`) and taking the median of `(closed_at - created_at)` in days.
- **FR-004:** System MUST compute `P` (PR Management) as `P = 0.5 * min(1, PRMergeRatio / 0.5) + 0.5 * max(0, 1 - MedianPRMergeDays / 14)`, with `PRMergeRatio` and `MedianPRMergeDays` defined analogously to FR-003 but for PRs (`GET /repos/{owner}/{repo}/pulls?state=closed&since=...`, filtered to merged).
- **FR-005:** System MUST compute `A` (Author Diversity) as `A = 0.6 * min(1, ActiveContributors / 5) + 0.4 * (1 - TopContributorCommitShare)`, using `GET /repos/{owner}/{repo}/stats/contributors`. `ActiveContributors` is the count of distinct contributors with at least one commit in the last 12 weeks; `TopContributorCommitShare = top_committer_commits / total_commits` in that window. If `total_commits == 0`, `TopContributorCommitShare = 0` and `ActiveContributors = 0` → `A = 0`.
- **FR-006:** System MUST run a **daily diff snapshot** job that records, per `scm_repo`, `open_issues_total` (and PR equivalent if obtainable cheaply) so that `opened_delta` and `closed_delta` can be derived without paginating issues each day. This piggybacks on the existing 30-second `GitHubRepositoryUpdatingJob` and adds no extra GitHub calls.
- **FR-007:** System MUST run a **weekly heavy job** that, per repo, calls `/stats/participation`, `/stats/contributors`, and paginates closed issues + closed PRs since `now - 12 weeks` to compute medians; then computes the final OSS Health score and upserts it. ShedLock name: `computeOssHealthLock`. Cadence: [NEEDS CLARIFICATION: exact weekly cron — e.g., Sundays 03:00 UTC, or staggered to avoid clashing with the daily 02:00 Maven indexing job mentioned in CLAUDE.md].
- **FR-008:** System MUST distinguish "score not yet computed" / "insufficient data" from "score is 0". The persisted shape is `(score INT NULL, computed_at TIMESTAMP NULL, status ENUM: OK | INSUFFICIENT_DATA | STALE)`. The API field is nullable; the response also carries a status enum.
- **FR-009:** System MUST treat *missing* sub-component inputs distinctly from *zero* sub-component inputs. Specifically: if a repo has zero issues opened in the window, its `IssueCloseRatio` ratio term contributes `0` (the project simply didn't get any issues to respond to), but the median term contributes its full `1.0` weight (there were no slow closes either). Same for PRs. The RFC's formula already accommodates this via the `min(1, …)` / `max(0, …)` clamps; this requirement just makes the intent explicit.
- **FR-010:** System MUST require **at least 12 weeks** of `scm_repo` history (since `created_at`) before computing a score. Repos younger than 12 weeks are marked `INSUFFICIENT_DATA`.
- **FR-011:** System MUST expose `ossHealth` (nullable integer) and `ossHealthStatus` (enum) on `ProjectDetailsDTO`. [NEEDS CLARIFICATION: do we also expose `ossHealth` in the search results DTO (`SearchProjectResult`) — i.e., on the `project_index` materialized view — for ranking or display, or only on the details endpoint?]
- **FR-012:** System MUST be controllable via a `klibs.*` feature toggle so the heavy weekly job can be disabled in `local` / `prod` independently (mirroring the existing `klibs.indexing` toggle pattern).
- **FR-013:** System MUST NOT compute or display an OSS Health score for archived or disabled GitHub repositories — pending FR-edge-case clarification above. [NEEDS CLARIFICATION: confirm.]
- **FR-014:** System MUST NOT change the weights / thresholds from the RFC formula without an explicit spec amendment — they are deliberately frozen to the RFC's simplified CSI variant. (See §8 Option A vs B.)

## 5. Non-functional requirements

- **Performance:** Score computation is offline (job-driven), not request-time. The API read path (`/project/.../details`) MUST add no more than a single indexed lookup on the score table (or a join on `scm_repo_id`). No GitHub API call on the read path.
- **External rate limits:**
    - GitHub: heavy job costs ~5 calls per repo (`/stats/participation` + `/stats/contributors` + ≥1 page of closed issues + ≥1 page of closed PRs + occasional retry on 202). With ~5,000/hr authenticated budget and the klibs-stage / klibs-features shared NAT pool (see `[[reference_klibs_egress_ips]]`), the job must process repos serially with rate-limit checks, not in parallel bursts.
    - The job MUST check `GitHubIntegration.getRateLimitInfo()` before each repo and yield if `remaining < safetyMargin` (margin TBD; recommend ≥ 200 to leave headroom for the existing 30-second `GitHubRepositoryUpdatingJob` running concurrently).
- **Concurrency:** New ShedLock keys: `computeOssHealthLock` (weekly heavy job) and — if we add a separate daily diff snapshot job rather than piggybacking — `snapshotScmRepoMetricsLock`. The heavy job and existing `updateGitHubRepositoryLock` job both call GitHub; they share the same egress IP / PAT and therefore the same rate-limit pool — see above.
- **Observability:**
    - Micrometer counter for each new GitHub call type (`stats_participation`, `stats_contributors`, `issues_paginated`, `pulls_paginated`), consistent with the existing per-request-type counters in `GitHubIntegrationKohsukeLibrary`.
    - Counter / gauge for `oss_health_compute_success_total`, `oss_health_compute_failure_total{reason}`, `oss_health_insufficient_data_total`.
    - Log line per repo at INFO with the four sub-scores so we can debug a surprising final number without re-running.
- **Security:** Read-only endpoint addition (no auth boundary change). The score is non-sensitive aggregate metadata. No personal data is persisted beyond the *count* of contributors (no logins stored in the snapshot table).

## 6. Out of scope

- Author-facing analytics (the P3 items in the RFC: page views, snippet copies, outbound clicks, search impressions/clicks). Those depend on the GitHub OAuth flow described in the RFC's "Authentication" section — separate spec.
- Number-of-dependents (P1 in the RFC) — already in the codebase (`project.dependent_count`). Not part of this spec.
- Maven downloads, pub.dev–style Likes/Points, trending. Not in this spec.
- Triangular-membership / fuzzy normalization from the original CSI paper (arXiv 2504.00542). We deliberately use the RFC's simplified linear-with-clamps form — see §8.
- Repository-centrality network analysis (arXiv 2405.07508). Explicitly rejected by the RFC as "too much computation for klibs purposes."
- Showing a numeric score below a display cutoff (the RFC suggests "show only if ≥ 40, else *Insufficient activity data*"). The **backend always returns the score**; the **display cutoff is a frontend concern**, except that we expose the `status` enum (FR-008) so the frontend can implement the rule trivially.
- Backfilling historical commit / issue / PR data older than what GitHub returns in a single call window. We start the window at `now`; the first 12 weeks after rollout will yield `INSUFFICIENT_DATA` for the issue-delta-derived ratio terms unless we explicitly backfill — see §11 assumption.

## 7. Klibs.io technical surface

- **Modules touched:**
    - `core/scm-repository` — new sub-entity / table for the OSS Health score and the daily delta snapshot; possibly a new sub-package `oss-health` to keep concerns isolated (or a sibling module `core/oss-health` — see §8 Option B vs C).
    - `integrations/github` — new methods on `GitHubIntegration`: `getWeeklyCommitParticipation(repositoryId)`, `getContributorStats(repositoryId)`, `listClosedIssuesSince(repositoryId, since)`, `listClosedPullRequestsSince(repositoryId, since)`. New per-request micrometer counters. Possibly a `BusyException`-equivalent for 202 responses.
    - `app` — new scheduled job(s); new Liquibase migration; wiring in `ProjectDetailsService` (or wherever `ProjectDetailsDTO` is assembled) to read the score.
    - `core/project` — additive change to `ProjectDetailsDTO`.
- **Database:**
    - New table `scm_repo_metrics_daily` (or similar): `scm_repo_id INT PK part`, `snapshot_date DATE PK part`, `open_issues_total INT`, `[NEEDS CLARIFICATION: do we want open_prs_total here too — GHRepository.openIssueCount lumps issues + PRs together, so we'd need a cheap separate read, or accept lumping]`, retention policy TBD (recommend 90 days rolling).
    - New table `oss_health_score`: `scm_repo_id INT PK`, `score INT NULL`, `status VARCHAR/ENUM`, `c_component NUMERIC`, `i_component NUMERIC`, `p_component NUMERIC`, `a_component NUMERIC`, `computed_at TIMESTAMP NULL`. Storing the sub-components alongside the final score makes both debugging and a future "why is my score X?" author breakdown trivial.
    - Migration folder: `app/src/main/resources/db/migration/2026-Q2/` (current quarter per the survey).
    - Additive-only: yes. No backfill of historical data; FR-010 covers the cold-start period.
- **Persistence style:** `scm-repository` currently mixes JPA entities and raw SQL. Match the existing style of `ScmRepositoryRepository` for the new sub-entity. [NEEDS CLARIFICATION: confirm whether the maintainer prefers JPA or raw JDBC for the new score/snapshot tables — both are present in the module.]
- **Search / materialized views:**
    - `project_index` will need a new column `oss_health` and the view's `CREATE OR REPLACE MATERIALIZED VIEW` SQL updated **only if** FR-011 clarification lands on "expose in search results." Otherwise the score lives in a side table joined at details-fetch time only.
    - `package_index` is unaffected (per-package, not per-project).
- **External integrations:**
    - GitHub REST API endpoints added: `GET /repos/{owner}/{repo}/stats/participation`, `GET /repos/{owner}/{repo}/stats/contributors`, paginated `GET /repos/{owner}/{repo}/issues?state=closed&since=…`, paginated `GET /repos/{owner}/{repo}/pulls?state=closed&since=…`.
    - GitHub may return `HTTP 202 Accepted` from the `/stats/*` endpoints on first access while it generates the stats; the integration MUST treat 202 as "retry next week," not as an error.
    - Retry / backoff: piggyback on whatever the existing kohsuke wrapper does for transient errors. No new backoff scheme.
- **Scheduled jobs:**
    - New `OssHealthComputeJob` (`@Scheduled` weekly, e.g. `cron = "0 0 3 * * SUN"`); ShedLock name `computeOssHealthLock`. Iterates over `scm_repo` rows where `oss_health_score.computed_at IS NULL OR < now() - 7 days`.
    - Either extend `GitHubRepositoryUpdatingJob` to also write a row into `scm_repo_metrics_daily` (preferred — no extra API calls), or add a tiny daily job that walks `scm_repo` and writes snapshots from the *already-stored* `open_issues` value. Either way, **no new GitHub calls per day**.
    - Idempotency: both jobs upsert keyed by `(scm_repo_id, snapshot_date)` / `scm_repo_id`. Re-running the weekly job for the same repo on the same day MUST yield the same score (i.e., must not double-count anything).
- **Storage:** No S3 / no local cache impact. README handling is untouched.
- **Configuration:** New `klibs.oss-health.*` properties — `enabled: Boolean = true`, `weekly-cron: String = "0 0 3 * * SUN"`, `repos-per-iteration: Int = N`, `rate-limit-safety-margin: Int = 200`. Profile defaults: enabled in `local` and `prod`; disabled in `test` (see `[[feedback_property_toggles_over_profile]]`).
- **API surface:** Additive change to `ProjectDetailsDTO`: new nullable `ossHealth: Int?` and `ossHealthStatus: String` (or enum). OpenAPI doc auto-regenerates. **Not** a breaking change.
- **Frontend contract:** `klibs-frontend` needs to:
    1. Render the new field, with the RFC's "show only if ≥ 40, else *Insufficient activity data*" rule applied client-side.
    2. Optionally render a tooltip explaining the four sub-scores (the breakdown is stored server-side per FR-table-design, so a future `/oss-health/{projectId}/breakdown` endpoint is cheap to add — out of scope for v1).

## 8. Design options considered

### Option A — Paper-faithful CSI (triangular membership functions)
Use the original CSI paper's normalization: each sub-component is normalized via a triangular function with the paper's target values (`μ_c = 0.25, σ_c = 0.25`, etc.) and the paper's stability thresholds (CSI ≥ 0.7 = stable).

- **Pros:** Defensible against academic critique; the paper's authors picked those constants for a reason.
- **Cons:** The paper itself is "conceptual" and "open to debate"; empirical validation is acknowledged-pending. Triangular membership functions are non-monotonic — a repo can score *worse* by getting *more* commits than the target, which is counterintuitive for authors and harder to explain.

### Option B — RFC's simplified linear-with-clamps form (recommended)
Use the formula in the RFC verbatim: linear normalization clamped by `min(1, x / threshold)` for "more is better" terms and `max(0, 1 - y / threshold)` for "less is better" terms. Always monotonic.

- **Pros:** Monotonic and explainable — "more closed issues is always better, up to the cap"; the RFC's thresholds (`IssueCloseRatio / 0.4`, `MedianIssueCloseDays / 21`, `PRMergeRatio / 0.5`, `MedianPRMergeDays / 14`, `ActiveContributors / 5`) are concrete and reviewable. Simpler to implement and to defend to an author who asks "why did my score drop?"
- **Cons:** Less "faithful" to the source paper; thresholds are picked by the RFC author, not from a published study. (Mitigation: the [Iqbal et al. paper](https://arxiv.org/abs/2309.12120v3) the RFC cites supports the *idea* of a composite over single signals but does not prescribe specific constants — so any choice is a judgment call.)

### Option C — Recompute on-demand from raw GitHub (no persistence)
Cache nothing; compute on first request per project per week and cache in-process.

- **Pros:** Minimal schema change; trivially correct.
- **Cons:** Hot path now depends on GitHub availability; shared NAT IP rate limits make this fragile under traffic; defeats the entire premise of a precomputed score.

**Decision:** **Option B**. Rationale: matches the RFC author's intent; monotonic and explainable; thresholds are reviewable in this spec rather than buried in a paper's appendix. Option A can be revisited if Option B's scores prove poorly calibrated against a hand-labeled sample of known-healthy / known-abandoned repos — but that's empirical follow-up work, not a v1 concern.

## 9. Key entities

- **OssHealthScore:** one row per `scm_repo`. Stores the final integer score, the four sub-components (`c, i, p, a` as numeric for debugging), a status enum (`OK | INSUFFICIENT_DATA | STALE | NOT_APPLICABLE`), and a `computed_at` timestamp. Lifecycle: created lazily on first successful weekly run for a repo; updated weekly; deleted only if the parent `scm_repo` is deleted (cascade).
- **ScmRepoMetricsDailySnapshot:** rolling daily snapshot of fields needed for issue/PR ratio derivation. Composite PK `(scm_repo_id, snapshot_date)`. Lifecycle: appended once per repo per day by the existing 30s job (or a thin new daily job); pruned to the last ~90 days (12 weeks + headroom).

## 10. Test strategy

- **Unit:**
    - `OssHealthCalculator` (pure function from `(weeklyCommits, openedClosedIssueCounts, medianIssueDays, openedClosedPrCounts, medianPrDays, activeContributors, topShare) -> Int score + components`). Test the formula edge cases: zero commits (C=0), zero issues, zero PRs, single contributor, perfectly balanced repo. Cover the FR-009 "missing vs zero" semantics with explicit cases.
    - Job loop unit test (with mocked `GitHubIntegration`) that verifies rate-limit yield (Scenario 4) and that 202 responses cause the repo to be skipped without state change.
- **DB-integration:** `BaseUnitWithDbLayerTest` subclasses for the new score/snapshot repositories. Method-level `@Sql` seeds per `[[feedback_sql_seed_method_level]]`. Verify upsert idempotency.
- **Web / smoke:** `SmokeTestBase` test that hits `/project/.../details` against a seeded fixture and asserts the new fields appear in the JSON response with the expected shape, including the null case (FR-008) and a populated case.
- *Reviewer-only — manual / staging:* deploy to `klibs-features` (per `[[feedback_never_prod_unprompted]]`), run the weekly job manually with `repos-per-iteration` set low, watch the new counters in the actuator output, spot-check 3–5 known repos (one obviously healthy, one obviously abandoned, one new) and confirm the scores feel directionally right. Compare against rate-limit headroom on the shared NAT IP pool.

## 11. Assumptions

- The RFC's "Heavy weekly job (5-10 API calls per repo)" is a soft upper bound; for repos with many closed issues/PRs the pagination cost grows. We assume a `Link: next` cap of ~3 pages (300 items) per endpoint is sufficient to compute medians representative of the 12-week window. If a repo has more than 300 closed issues in 12 weeks, the median over the first 300 is a fine approximation.
- The first 12 weeks after rollout will have only forward-going daily delta data, so the `IssueCloseRatio` and `PRMergeRatio` terms will only be reliable for windows starting at deploy time. We accept that the **first ~3 months post-deploy** is a warmup during which many repos report `INSUFFICIENT_DATA`. Alternative — backfilling via `GET /issues?state=closed&since=12wk_ago` for every repo at deploy time — is feasible but expensive; we defer that decision (see §6 out-of-scope).
- The `GitHubRepositoryUpdatingJob`'s 30-second cadence (3 repos per run = ~360 repos/hour) is comfortably faster than the weekly cadence of the heavy job, so the daily-diff piggyback in FR-006 will populate `scm_repo_metrics_daily` for every repo within ~hours. We assume the heavy job runs *after* enough daily snapshots exist (≥ 84 days of snapshots) — for the first 12 weeks post-rollout, repos will be `INSUFFICIENT_DATA`.
- Display semantics (`>= 40 ⇒ show number`, `< 40 ⇒ show "Insufficient activity data"`) are a frontend concern. The backend always returns the raw number when computable; the `status` enum is the contract. This separation lets us tune the display cutoff later without re-deploying the backend.
- The numeric `nativeId` of a GitHub repo is stable across renames/transfers (this is GitHub's documented behavior); we key all new tables off `scm_repo.id`, which already keys off `nativeId`.

## 12. References

- RFC, source of truth for this spec: `/Users/nikita.vlaev/Downloads/KTL-4246 Exploratory research for author-faced insights.md`.
- *Introducing Repository Stability* — arXiv [2504.00542](https://arxiv.org/abs/2504.00542) (2025). Source of the CSI formula structure (weights 0.30 / 0.25 / 0.25 / 0.20). Paper acknowledges its "conceptual phase" status; we adopt the four-dimension structure but **not** its triangular-membership normalization (see §8).
- *Individual context-free online community health indicators fail to identify open source software sustainability* — arXiv [2309.12120v3](https://arxiv.org/abs/2309.12120v3) (2023, rev. 2024). Motivates the composite-over-single-metric choice; cited in problem statement.
- *Revealing the value of Repository Centrality in lifespan prediction of OSS Projects* — arXiv [2405.07508](https://arxiv.org/abs/2405.07508) (2024). Explicitly out of scope per the RFC (too compute-heavy).
- Existing klibs.io entities referenced: `ScmRepositoryEntity` (`core/scm-repository`), `ProjectDetailsDTO` (`core/project`), `GitHubIntegration` (`integrations/github`), `project_index` materialized view (`core/search`).
- Memory: `[[reference_klibs_egress_ips]]` (shared NAT pool — rate-limit budget is per-IP, not per-replica), `[[reference_ghratelimit_record_ctor]]` (kohsuke ctor-order gotcha), `[[feedback_property_toggles_over_profile]]`, `[[feedback_sql_seed_method_level]]`, `[[feedback_ask_before_additive_migration]]`.
