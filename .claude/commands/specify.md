---
description: Generate a klibs.io feature spec from a vague task description, ready for human review.
---

# /specify

Generate a feature specification from the user's input below. The spec must follow the template at `.claude/templates/spec.md` and respect the rules in `CLAUDE.md` (module-by-feature boundaries, minimal diff, no unsolicited refactors, etc.).

## Input
$ARGUMENTS

## Process
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

The spec is the deliverable for this command. Plan and implementation come later under separate commands.
