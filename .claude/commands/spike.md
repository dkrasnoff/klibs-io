---
description: Throwaway exploratory prototype to validate an idea — no spec, no plan, no tests. Output is a findings document.
---

# /spike

Run a throwaway exploration to answer a specific question or test a hypothesis. This command intentionally **suppresses** CLAUDE.md's Align → Execute milestone gating — that gate exists to keep production work disciplined, and it would defeat the purpose of a spike.

A spike is not production code. Its only deliverable is **learning**, captured in a findings document.

## Input
$ARGUMENTS

A specific question or hypothesis. Examples:
- "Can we extract dependents count from Maven Central without exceeding the solrsearch rate limit?"
- "Does the OssHealth score still make sense if we drop the issue-velocity component?"
- "Try PostgreSQL FTS `rank_cd` vs `ts_rank` — which gives better results for our queries?"

If `$ARGUMENTS` is vague (e.g. "spike a thing"), STOP and ask for a specific question.

## Process
1. **Pick a short kebab-case slug** for the spike (3–6 words).
2. **Time-box.** A spike is small. If you find yourself touching more than ~5 files, STOP and reconsider — this should probably be a real spec.
3. **Write code freely.** Skip tests, skip migrations, skip OpenAPI updates. Hardcode where useful. Use `println` / `logger.info` liberally. Hit real APIs when allowed.
4. **No production discipline.**
    - Do **not** match JPA vs JDBC patterns — pick whichever is faster to write.
    - Do **not** respect module-by-feature boundaries — put exploration code wherever is easiest.
    - Do **not** worry about backwards compatibility, error handling, or edge cases unless they're *the thing being explored*.
5. **Write findings to `docs/spikes/<slug>/findings.md`** with this shape:
    - **Question:** (the input)
    - **What I tried:** 2–5 bullets
    - **What happened:** observations, numbers, errors
    - **Verdict:** Proceed / Abandon / Pivot — one line
    - **Implications:** what this means for the eventual real implementation (links to constraints, gotchas, surprise behaviors)
    - **Files touched:** path list (so `/specify --from-spike` can find them)
6. **STOP.** Report:
    - Path of the findings file
    - Verdict in one line
    - Whether you'd recommend running `/specify --from-spike <slug>` next, or that the spike's answer is "no, don't build this"

Spike code is **not meant to be merged.** Stay on a spike branch or revert before formalizing into a real spec.
