# klibs.io Agent Skills

This directory contains [Agent Skills](https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills)
published by [klibs.io](https://klibs.io). An Agent Skill is a folder with a `SKILL.md` file that teaches an
AI coding agent how to perform a specific task. Agent Skills are an open standard supported by
JetBrains Junie, Claude Code, Codex, Gemini CLI and others.

## How installation works

A skill is just a folder containing a `SKILL.md` file (plus optional supporting files). To install a skill you
copy its folder into the directory your agent scans for skills. Each agent looks in two kinds of locations:

- **Project scope** — the skill is available only in the current project and can be committed to version
  control so the whole team gets it automatically.
- **User scope** — the skill is available globally across all your projects on your machine, but stays private
  to your user account.

After copying, restart the agent session (skills are usually loaded at session start) and the agent will
discover the skill automatically based on its `description`.

## Quick install (recommended)

The easiest way to install is with one of the commands below. They pull the skill directly from
[`JetBrains/klibs-io`](https://github.com/JetBrains/klibs-io), so you don't need to clone the repo or copy
files by hand. If you prefer to do it manually, skip to [Manual installation](#manual-installation).

### skills CLI (works with most agents)

The [`skills`](https://github.com/vercel-labs/skills) CLI installs the skill into whichever agent you choose:

```bash
npx skills add JetBrains/klibs-io
```

After that, restart the agent session and ask a KMP-library question to confirm the skill triggers
(see [Verify the installation](#verify-the-installation)).

## Manual installation

If your agent isn't covered above, or you'd rather install by hand, follow the two steps below.

## Step 1 — Get the skill folder

The goal is to end up with a local copy of the
`kmp-libraries-expert/` folder.

### Download a single file

The skill currently consists of just `SKILL.md`, so you can download it directly:

```bash
mkdir -p kmp-libraries-expert
curl -fsSL \
  https://raw.githubusercontent.com/JetBrains/klibs-io/master/skills/kmp-libraries-expert/SKILL.md \
  -o kmp-libraries-expert/SKILL.md
```

## Step 2 — Install into your agent

Copy the `kmp-libraries-expert/` folder into the appropriate directory for your agent. The table below lists
the project-level and user-level locations for the most common agents.

| Agent | Project-level | User-level (global) |
| --- | --- | --- |
| JetBrains Junie | `.junie/skills/` | `~/.junie/skills/` |
| Claude Code | `.claude/skills/` | `~/.claude/skills/` |
| Codex CLI | `.agents/skills/` (or `.codex/skills/`) | `~/.agents/skills/` |
| Gemini CLI | `.gemini/skills/` (or `.agents/skills/`) | `~/.gemini/skills/` |
| Cursor | `.cursor/skills/` | `~/.cursor/skills/` |
| GitHub Copilot | `.github/skills/` | — |

On Windows, replace `~/` with `%USERPROFILE%\` and use backslashes (for example, `%USERPROFILE%\.junie\skills\`).

After copying, the layout should look like this (Junie project scope shown as an example):

```
your-project/
└── .junie/
    └── skills/
        └── kmp-libraries-expert/
            └── SKILL.md
```

### JetBrains Junie (IDEA, PyCharm, Rider, etc.)

Project scope (shareable via version control):

```bash
mkdir -p .junie/skills
cp -R kmp-libraries-expert .junie/skills/
```

User scope (available in all your projects):

```bash
mkdir -p ~/.junie/skills
cp -R kmp-libraries-expert ~/.junie/skills/
```

Junie scans `.junie/skills/` at both the project and user levels and applies relevant skills automatically.
See the [Junie Agent skills docs](https://junie.jetbrains.com/docs/agent-skills.html).

### Claude Code

```bash
# project scope
mkdir -p .claude/skills && cp -R kmp-libraries-expert .claude/skills/

# or user scope
mkdir -p ~/.claude/skills && cp -R kmp-libraries-expert ~/.claude/skills/
```

Start (or restart) Claude Code in the project and ask a KMP-library question — the skill is discovered
automatically. See the [Claude Code skills docs](https://code.claude.com/docs/en/skills).

### Codex CLI

```bash
mkdir -p .agents/skills && cp -R kmp-libraries-expert .agents/skills/
```

### Gemini CLI

```bash
mkdir -p .gemini/skills && cp -R kmp-libraries-expert .gemini/skills/
```

## Verify the installation

1. Restart the agent session so the new skill is loaded.
2. Ask a question that should trigger the skill, for example:
   - "What is the latest stable version of Ktor for Kotlin Multiplatform?"
   - "Which KMP library should I use for local key-value storage, and does it support iOS and Wasm?"
3. The agent should consult `klibs.io` (via the MCP server or web pages) and respond with verified
   coordinates and a `klibs.io` link.

If the skill does not trigger:

- Confirm the folder is at the exact path for your agent and that `SKILL.md` sits directly inside it.
- Make sure the YAML frontmatter at the top of `SKILL.md` is intact (it must start on line 1 with `---`).
- Restart the session — skills are typically snapshotted at session start.

## Updating

Re-run Step 1 to fetch the latest version of the skill, then copy it over the existing folder (overwriting
`SKILL.md`). Restart the agent session afterwards.

## Uninstalling

Delete the `kmp-libraries-expert/` folder from the agent's skills directory (or remove/disable it from the UI
for Claude.ai), and remove the `klibs` entry from your MCP configuration if you added one.

## Security note

Agent Skills can include instructions (and, in general, scripts) that influence agent behavior. Install skills
only from trusted sources and review the contents of `SKILL.md` before use. The skills in this directory are
published by the klibs.io team at [JetBrains/klibs-io](https://github.com/JetBrains/klibs-io).
