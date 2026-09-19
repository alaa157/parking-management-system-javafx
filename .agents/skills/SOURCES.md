# Skill Sources & Maintenance

Vendored 2026-09-19. Update with `git clone --depth 1 <url>` + copy into
`.agents/skills/<source>/` (keep leaf skill-folder names so `SKILL.md`
frontmatter `name` stays valid), then refresh this file.

| Source dir | Upstream | Commit | License |
|---|---|---|---|
| `superpowers/` (15) | https://github.com/obra/superpowers (`skills/`) | `5bf4e78` | MIT |
| `anthropic/` (19) | https://github.com/anthropics/skills (`skills/`, minus `template/`) | `34040c9` | Apache-2.0 (docx/pdf/pptx/xlsx are source-available, see their LICENSE.txt) |
| `mattpocock/` (31) | https://github.com/mattpocock/skills (`skills/engineering`, `skills/productivity` stable only; `in-progress/`, `misc/` excluded) | `c55ee46` | MIT |
| `openai/` (38) | https://github.com/openai/skills (`skills/.curated`, minus Codex-internal `.system` duplicates) | `49f948f` | Per-skill LICENSE.txt |
| `vercel/` (9) | https://github.com/vercel-labs/agent-skills (`skills/`) | `063bee9` | Per-skill license |

Trimmed on vendor: `*.zip` bundles, `*.ttf/*.otf/*.woff*` font binaries
(`anthropic/canvas-design/canvas-fonts` — reinstall from upstream if you need
poster fonts).

Do NOT edit vendored skills in place except to fix breakage; upstream is the
source of truth. Project-specific guidance belongs in `AGENTS.md` /
`CONTEXT.md`, not inside `.agents/skills/`.
