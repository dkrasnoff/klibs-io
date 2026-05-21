---
description: Generate a klibs.io feature spec — from a vague task description, or from a completed spike (`--from-spike <spike-name>`).
---

# /specify

Generate a feature specification ready for human review. The spec must follow the template at `.claude/templates/spec.md` and respect the rules in `CLAUDE.md` (module-by-feature boundaries, minimal diff, no unsolicited refactors, etc.).

## Input
$ARGUMENTS

## Mode detection

Inspect `$ARGUMENTS`:

- If it begins with `--from-spike <spike-name>`, operate in **reverse-spec mode** (see below).
- Otherwise, operate in **forward-spec mode** (default).

## Forward-spec mode (default)

1. **Pick a short name for the feature** — 3–6 words, lowercase with hyphens (e.g. `oss-health-metric`). This becomes the directory under `docs/specs/`. Derive it from the main noun phrase of the input if not obvious.
2. **Read `.claude/templates/spec.md`** for the full structure.
3. **Fill in every applicable section.** The template marks several sections "include only what applies" / "mark only lines that apply" — omit lines and entire sections that don't apply rather than leaving empty placeholders. A small spec may legitimately use only sections 1–4 and 7.
4. **For anything you can't answer from the input**, insert `[NEEDS CLARIFICATION: <specific question>]` inline at the point it matters. Do not invent details to fill silence.
5. **Don't include why-now / priority-rationale / project-management context.** Those are settled by the time the spec exists. Capture only consequences that shape implementation.
6. **Write the result to `docs/specs/<feature-name>/spec.md`.** Create the directory if needed.
7. **STOP.** Do not generate a plan, tasks, or code. Report back:
    - Path of the file written
    - List of every `[NEEDS CLARIFICATION]` marker you inserted, with the line it sits on
    - Up to 3 assumptions you'd most like the reviewer to confirm or correct
    - Any sections you deliberately omitted, and why

## Reverse-spec mode (`--from-spike <spike-name>`)

You're formalizing what a completed spike *taught* into a real spec. The spike code is **not** the source of truth — the *learnings* are. Do not transcribe spike implementation choices into FRs; question them.

1. **Read `docs/spikes/<spike-name>/findings.md`** to understand the question, verdict, and implications.
2. **Read the files listed under "Files touched"** in findings.md to see what the spike actually did.
3. **Pick a fresh name for the production spec** — often different from the spike's name, since the spec is a deliberate redesign.
4. **Fill in `.claude/templates/spec.md` as in forward-spec mode**, with these extra rules:
    - Cite the spike in §12 References: `Spike: docs/spikes/<spike-name>/findings.md`.
    - In §8 Design options, capture what the spike ruled out (Option A = the approach the spike tried but found problematic; Decision = the refined approach). If the spike's approach was obviously right, skip §8.
    - In §11 Assumptions, include any constraint the spike discovered (rate-limit ceilings, schema gotchas, surprise API behaviors).
    - Spike code does not match production patterns (JPA vs JDBC, error handling, etc.). §7 must explicitly state which production patterns the real implementation will follow — don't inherit the spike's choices by default.
5. **Write to `docs/specs/<feature-name>/spec.md`** — never overwrite the spike. Both artifacts stay.
6. **STOP.** Report:
    - Path of the spec written
    - Which spike findings shaped which spec sections
    - Anything the spike *did* that you're deliberately *not* carrying into the spec, and why
    - `[NEEDS CLARIFICATION]` markers and top-3 assumptions

The spec is the deliverable. Plan and implementation come later under separate commands.
