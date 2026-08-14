<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

## Project conventions

- This repository is the Lingxi Workbench user-facing application; admin features belong in `lingxi-workbench-admin`.
- Use React 19, TypeScript, Next.js App Router, and LobeUI. Prefer LobeUI components and tokens before introducing another component library.
- The user-facing Workbench is built on the open-source LobeUI component system. Treat the installed LobeUI types and component contracts as the source of truth for values, events, loading states, keyboard behavior, and composition; inspect them before adding adapters or custom replacements.
- Keep model-provider secrets and Sa-Token authentication decisions on the backend. Browser code must only call the Lingxi backend.
- Add feature-oriented layers only when real business logic needs them. Avoid speculative abstractions.
- Non-code work artifacts (QA screenshots, acceptance records, temporary evidence, design reviews) go into `attachments/`, which is gitignored and never committed. Keep `src/` and the repo root clean of such files.
- Use Vitest for focused unit tests under `src/**/*.test.ts`; run `pnpm test` when tested logic changes.
- Before committing, run `pnpm test`, `pnpm lint`, and `pnpm build`.
