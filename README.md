# Lunch² – Bring a Friend. Meet Two.

Mobile-first web app for the THWS Fit4Germany hackathon: students register in ~60 seconds via QR code, bring a friend,
and get matched into lunch groups of four (2 locally-based + 2 international students), with a group reveal,
AI conversation cards and mutual, opt-in contact sharing. Prototype – uses fictional demo data only.

## Prerequisites
- JDK 21 (`java -version`), Node.js ≥ 20 (`node -v`). Maven is optional – the backend ships `./mvnw`.
- Optional: `GEMINI_API_KEY` for AI conversation cards (without it the app uses built-in fallback cards).

## Install
```
cd backend && ./mvnw -q -DskipTests package && cd ..
cd frontend && npm install && cd ..
```

## Configuration
Backend keys and env vars are listed in `docs/API_CONTRACT.md` §5 (all have local defaults). Important ones:
| Env var | Default | Purpose |
|---|---|---|
| `LUNCH2_ADMIN_KEY` | `dev-admin-key` | header `X-Admin-Key` for seed/match/reset |
| `LUNCH2_DATA_FILE` | `./data/lunch2.json` | JSON persistence file |
| `LUNCH2_PUBLIC_BASE_URL` | `http://localhost:5173` | base for invite/restore links |
| `GEMINI_API_KEY` | *(empty)* | enables Gemini AI cards |
| `LUNCH2_AI_MODEL` | `gemini-3.8-flash` | Gemini model for cards |
Frontend: `frontend/.env.development` → `VITE_USE_MOCKS=true` serves mock data; put `VITE_USE_MOCKS=false` in `frontend/.env.development.local` to use the real backend.

## Run locally
```
cd backend && ./mvnw quarkus:dev        # API on http://localhost:8080
cd frontend && npm run dev              # UI on http://localhost:5173 (proxies /api to :8080)
```

## Verify
```
cd backend && ./mvnw -q verify                                   # backend tests
cd frontend && npm run lint && npm test && npm run build          # typecheck, tests, build
curl -s localhost:8080/api/ping                                   # {"ok":true} while quarkus:dev runs
```

## Project structure
```
AGENTS.md / CLAUDE.md     rules for AI coding agents (CLAUDE.md imports AGENTS.md)
.claude/skills/           backend-endpoint, frontend-page, verify-integration
docs/                     SPEC, ARCHITECTURE, API_CONTRACT (frozen), TASKS (tickets + queues)
backend/                  Quarkus app, packages de.thws.lunch2.{common,domain,persistence,auth,participant,invite,connect,matching,group,cards,admin}
frontend/                 React + Vite + Tailwind, src/{api,components,pages,i18n}
```
Team workflow, ownership and the ticket queue for each developer: `docs/TASKS.md`.
