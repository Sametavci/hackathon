---
name: frontend-page
description: Build or change a Lunch² React page or component (mobile-first, Tailwind, typed API calls, mocks, accessibility). Use for HACK-3xx tickets and for ConnectPage/AdminPage work.
---

# Lunch² frontend page

1. Find the page's route and owner in `docs/API_CONTRACT.md` §6; edit only that page file (and new components you own). Never edit `App.tsx` routes.
2. Data: import functions from `src/api/endpoints.ts` only. Types come from `src/api/types.ts`. If a function is missing, ask Dev 3 – do not add `fetch` calls.
3. Develop against mocks first (`VITE_USE_MOCKS=true` in `.env.development`); every page must handle loading, error (`ApiError.message`) and empty states.
4. Auth: `src/api/session.ts` holds the token; pages that need it redirect to `#/` when it is missing.
5. Layout: content inside the existing `Layout` (max-w-md). Mobile first; test at 375 px width. Tailwind classes only; brand colours `brand`, `brand-soft`, `ink`, `ink-muted`.
6. Accessibility: every input has a `<label>`; toggles use `aria-pressed`; buttons ≥ 44 px high; visible focus ring; animations wrapped in `motion-safe:`.
7. Text: English strings via `t()` from `src/i18n/en.ts` (Dev 3) or a `<Page>.strings.ts` file next to your page (Dev 1/Dev 2). Friendly, short, no jargon.
8. Privacy: show only fields the contract returns; never display or log the token.
9. Verify: `cd frontend && npm run lint && npm test && npm run build`, then check the page in `npm run dev` at 375 px.
