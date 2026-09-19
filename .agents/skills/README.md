# Shared Agent Skills

Canonical public location: `.agents/skills/<source>/<skill>/SKILL.md` (Agent Skills standard).
Auto-discovered by Codex/Claude (`~/.agents/skills`, repo `.agents/skills`) and wired into OpenCode via `opencode.json` (`skills.paths`).

## anthropic (19)
- `anthropic/academy-guide` — >
- `anthropic/algorithmic-art` — Creating algorithmic art using p5.js with seeded randomness and interactive parameter exploration. Use this when users request creating art using code, generati
- `anthropic/brand-guidelines` — Applies Anthropic's official brand colors and typography to any sort of artifact that may benefit from having Anthropic's look-and-feel. Use it when brand color
- `anthropic/canvas-design` — Create beautiful visual art in .png and .pdf documents using design philosophy. You should use this skill when the user asks to create a poster, piece of art, d
- `anthropic/claude-api` — |-
- `anthropic/discernment-nudge` — >
- `anthropic/doc-coauthoring` — Guide users through a structured workflow for co-authoring documentation. Use when user wants to write documentation, proposals, technical specs, decision docs,
- `anthropic/docx` — "Use this skill whenever the user wants to create, read, edit, or manipulate Word documents (.docx files) or Word templates (.dotx files). Triggers include: any
- `anthropic/frontend-design` — Guidance for distinctive, intentional visual design when building new UI or reshaping an existing one. Helps with aesthetic direction, typography, and making ch
- `anthropic/internal-comms` — A set of resources to help me write all kinds of internal communications, using the formats that my company likes to use. Claude should use this skill whenever 
- `anthropic/mcp-builder` — Guide for creating high-quality MCP (Model Context Protocol) servers that enable LLMs to interact with external services through well-designed tools. Use when b
- `anthropic/pdf` — Use this skill whenever the user wants to do anything with PDF files. This includes reading or extracting text/tables from PDFs, combining or merging multiple P
- `anthropic/pptx` — "Use this skill any time a .pptx or .potx file is involved in any way — as input, output, or both. This includes: creating slide decks, pitch decks, or presenta
- `anthropic/skill-creator` — Create new skills, modify and improve existing skills, and measure skill performance. Use when users want to create a skill from scratch, edit, or optimize an e
- `anthropic/slack-gif-creator` — Knowledge and utilities for creating animated GIFs optimized for Slack. Provides constraints, validation tools, and animation concepts. Use when users request a
- `anthropic/theme-factory` — Toolkit for styling artifacts with a theme. These artifacts can be slides, docs, reportings, HTML landing pages, etc. There are 10 pre-set themes with colors/fo
- `anthropic/web-artifacts-builder` — Suite of tools for creating elaborate, multi-component claude.ai HTML artifacts using modern frontend web technologies (React, Tailwind CSS, shadcn/ui). Use for
- `anthropic/webapp-testing` — Toolkit for interacting with and testing local web applications using Playwright. Supports verifying frontend functionality, debugging UI behavior, capturing br
- `anthropic/xlsx` — "Use this skill any time a spreadsheet file is the primary input or output. This means any task where the user wants to: open, read, edit, or fix an existing .x

## mattpocock (0)

## openai (38)
- `openai/aspnet-core` — Build, review, refactor, or architect ASP.NET Core web applications using current official guidance for .NET web development. Use when working on Blazor Web App
- `openai/chatgpt-apps` — Build, scaffold, refactor, and troubleshoot ChatGPT Apps SDK applications that combine an MCP server and widget UI. Use when Codex needs to design tools, regist
- `openai/cli-creator` — Build a composable CLI for Codex from API docs, an OpenAPI spec, existing curl examples, an SDK, a web app, an admin tool, or a local script. Use when the user 
- `openai/cloudflare-deploy` — Deploy applications and infrastructure to Cloudflare using Workers, Pages, and related platform services. Use when the user asks to deploy, host, publish, or se
- `openai/define-goal` — Help the user define a concrete, measurable goal before starting work, especially when they ask to use the goal tool, create a goal, set an objective, clarify s
- `openai/figma` — Use the Figma MCP server to fetch design context, screenshots, variables, and assets from Figma, and to translate Figma nodes into production code. Trigger when
- `openai/figma-code-connect-components` — Connects Figma design components to code components using Code Connect mapping tools. Use when user says "code connect", "connect this component to code", "map 
- `openai/figma-create-design-system-rules` — Generates custom design system rules for the user's codebase. Use when user says "create design system rules", "generate rules for my project", "set up design r
- `openai/figma-create-new-file` — Create a new blank Figma file. Use when the user wants to create a new Figma design or FigJam file, or when you need a new file before calling use_figma. Handle
- `openai/figma-generate-design` — "Use this skill alongside figma-use when the task involves translating an application page, view, or multi-section layout into Figma. Triggers: 'write to Figma'
- `openai/figma-generate-library` — "Build or update a professional-grade design system in Figma from a codebase. Use when the user wants to create variables/tokens, build component libraries, set
- `openai/figma-implement-design` — Translates Figma designs into production-ready application code with 1:1 visual fidelity. Use when implementing UI code from Figma files, when user mentions "im
- `openai/figma-use` — "**MANDATORY prerequisite** — you MUST invoke this skill BEFORE every `use_figma` tool call. NEVER call `use_figma` directly without loading this skill first. S
- `openai/gh-address-comments` — Help address review/issue comments on the open GitHub PR for the current branch using gh CLI; verify gh auth first and prompt the user to authenticate if not lo
- `openai/gh-fix-ci` — "Use when a user asks to debug or fix failing GitHub PR checks that run in GitHub Actions; use `gh` to inspect checks and logs, summarize failure context, draft
- `openai/hatch-pet` — Create, repair, validate, visually QA, and package Codex-compatible animated pets and pet spritesheets from character art, generated images, company or prospect
- `openai/jupyter-notebook` — "Use when the user asks to create, scaffold, or edit Jupyter notebooks (`.ipynb`) for experiments, explorations, or tutorials; prefer the bundled templates and 
- `openai/linear` — Manage issues, projects & team workflows in Linear. Use when the user wants to read, create or updates tickets in Linear.
- `openai/migrate-to-codex` — Migrate supported instruction files, skills, agents, and MCP config into Codex project and global files.
- `openai/netlify-deploy` — Deploy web projects to Netlify using the Netlify CLI (`npx netlify`). Use when the user asks to deploy, host, publish, or link a site/repo on Netlify, including
- `openai/notion-knowledge-capture` — Capture conversations and decisions into structured Notion pages; use when turning chats/notes into wiki entries, how-tos, decisions, or FAQs with proper linkin
- `openai/notion-meeting-intelligence` — Prepare meeting materials with Notion context and Codex research; use when gathering context, drafting agendas/pre-reads, and tailoring materials to attendees.
- `openai/notion-research-documentation` — Research across Notion and synthesize into structured documentation; use when gathering info from multiple Notion sources to produce briefs, comparisons, or rep
- `openai/notion-spec-to-implementation` — Turn Notion specs into implementation plans, tasks, and progress tracking; use when implementing PRDs/feature specs and creating Notion plans + tasks from them.
- `openai/pdf` — "Use when tasks involve reading, creating, or reviewing PDF files where rendering and layout matter; prefer visual checks by rendering pages (Poppler) and use P
- `openai/playwright` — "Use when the task requires automating a real browser from the terminal (navigation, form filling, snapshots, screenshots, data extraction, UI-flow debugging) v
- `openai/playwright-interactive` — "Persistent browser and Electron interaction through `js_repl` for fast iterative UI debugging."
- `openai/render-deploy` — Deploy applications to Render by analyzing codebases, generating render.yaml Blueprints, and providing Dashboard deeplinks. Use when the user wants to deploy, h
- `openai/screenshot` — "Use when the user explicitly asks for a desktop or system screenshot (full screen, specific app or window, or a pixel region), or when tool-specific capture ca
- `openai/security-best-practices` — "Perform language and framework specific security best-practice reviews and suggest improvements. Trigger only when the user explicitly requests security best p
- `openai/security-ownership-map` — "Analyze git repositories to build a security ownership topology (people-to-file), compute bus factor and sensitive-code ownership, and export CSV/JSON for grap
- `openai/security-threat-model` — "Repository-grounded threat modeling that enumerates trust boundaries, assets, attacker capabilities, abuse paths, and mitigations, and writes a concise Markdow
- `openai/sentry` — "Use when the user asks to inspect Sentry issues or events, summarize recent production errors, or pull basic Sentry health data via the Sentry CLI; perform rea
- `openai/speech` — "Use when the user asks for text-to-speech narration or voiceover, accessibility reads, audio prompts, or batch speech generation via the OpenAI Audio API; run 
- `openai/transcribe` — "Transcribe audio files to text with optional diarization and known-speaker hints. Use when a user asks to transcribe speech from audio/video, extract text from
- `openai/vercel-deploy` — Deploy applications and websites to Vercel. Use when the user requests deployment actions like "deploy my app", "deploy and give me the link", "push this live",
- `openai/winui-app` — Bootstrap, develop, and design modern WinUI 3 desktop applications with C# and the Windows App SDK using official Microsoft guidance, WinUI Gallery patterns, Wi
- `openai/yeet` — "Use only when the user explicitly asks to stage, commit, push, and open a GitHub pull request in one flow using the GitHub CLI (`gh`)."

## superpowers (15)
- `superpowers/brainstorming` — "You MUST use this before any creative work - creating features, building components, adding functionality, or modifying behavior. Explores user intent, require
- `superpowers/diagnosing-superpowers` — Use when a superpowers session went wrong and your human partner wants to know why — repeated work, ignored plans, stumbles, poor results, a skill that didn't f
- `superpowers/dispatching-parallel-agents` — Use when facing 2+ independent tasks that can be worked on without shared state or sequential dependencies
- `superpowers/executing-plans` — Use when executing an implementation plan in the current session as the implementer yourself — your human partner chose inline execution, or no subagent tool is
- `superpowers/finishing-a-development-branch` — Use when implementation is complete, all tests pass, and you need to decide how to integrate the work
- `superpowers/receiving-code-review` — Use when receiving code review feedback, before implementing suggestions, especially if feedback seems unclear or technically questionable - requires technical 
- `superpowers/requesting-code-review` — Use when completing tasks, implementing major features, or before merging to verify work meets requirements
- `superpowers/subagent-driven-development` — Use when executing implementation plans with independent tasks in the current session
- `superpowers/systematic-debugging` — Use when encountering any bug, test failure, or unexpected behavior, before proposing fixes
- `superpowers/test-driven-development` — Use when implementing any feature or bugfix, before writing implementation code
- `superpowers/using-git-worktrees` — Use when starting feature work that needs isolation from current workspace or before executing implementation plans - ensures an isolated workspace exists via n
- `superpowers/using-superpowers` — Use when starting any conversation - establishes how to find and use skills, requiring skill invocation before ANY response including clarifying questions
- `superpowers/verification-before-completion` — Use when about to claim work is complete, fixed, or passing, before committing or creating PRs - requires running verification commands and confirming output be
- `superpowers/writing-plans` — Use when you have a spec or requirements for a multi-step task, before touching code
- `superpowers/writing-skills` — Use when creating new skills, editing existing skills, or verifying skills work before deployment

## vercel (9)
- `vercel/composition-patterns` — React composition patterns that scale. Use when refactoring components with
- `vercel/deploy-to-vercel` — Deploy applications and websites to Vercel. Use when the user requests deployment actions like "deploy my app", "deploy and give me the link", "push this live",
- `vercel/react-best-practices` — React and Next.js performance optimization guidelines from Vercel Engineering. This skill should be used when writing, reviewing, or refactoring React/Next.js c
- `vercel/react-native-skills` — React Native and Expo best practices for building performant mobile apps. Use
- `vercel/react-view-transitions` — Guide for implementing smooth, native-feeling animations using React's View Transition API (`<ViewTransition>` component, `addTransitionType`, and CSS view tran
- `vercel/vercel-cli-with-tokens` — Deploy and manage projects on Vercel using token-based authentication. Use when working with Vercel CLI using access tokens rather than interactive login — e.g.
- `vercel/vercel-optimize` — "Use for Vercel cost and performance optimization on deployed projects, especially Next.js, SvelteKit, Nuxt, and limited Astro apps. Collect Vercel metrics, usa
- `vercel/web-design-guidelines` — Review UI code for Web Interface Guidelines compliance. Use when asked to "review my UI", "check accessibility", "audit design", "review UX", or "check my site 
- `vercel/writing-guidelines` — Review docs/prose for Writing Guidelines compliance. Use when asked to "review my docs", "check writing style", "audit prose", "review docs voice and tone", or 
