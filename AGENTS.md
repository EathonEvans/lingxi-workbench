<!-- BEGIN:nextjs-agent-rules -->
# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` before writing any code. Heed deprecation notices.
<!-- END:nextjs-agent-rules -->

## Project conventions

- This repository is the Lingxi Workbench user-facing application; admin features belong in `lingxi-workbench-admin`.
- Use React 19, TypeScript, Next.js App Router, and LobeUI. Prefer LobeUI components and tokens before introducing another component library.
- Keep model-provider secrets and Sa-Token authentication decisions on the backend. Browser code must only call the Lingxi backend.
- Add feature-oriented layers only when real business logic needs them. Avoid speculative abstractions.
- Before committing, run `pnpm lint` and `pnpm build`.
