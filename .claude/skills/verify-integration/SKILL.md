---
name: verify-integration
description: Run the Lunch² checkpoint checks (backend tests, frontend build, live end-to-end smoke against the real backend) and diagnose integration failures between frontend and backend. Use before merging to main, at checkpoints CP1–CP5, or when a page and an endpoint disagree.
---

# Lunch² verification & integration

## Before every merge
- Backend: `cd backend && ./mvnw -q verify` – must end with exit code 0.
- Frontend: `cd frontend && npm run lint && npm test && npm run build`.
Report the actual result lines; if something fails, report the failure, do not merge.

## Live end-to-end smoke (CP2+)
1. Terminal A: `cd backend && ./mvnw quarkus:dev` (wait for "Listening on: http://localhost:8080").
2. `curl -s localhost:8080/api/ping` → `{"ok":true}`.
3. Once HACK-203/204 exist: `curl -s -XPOST -H 'X-Admin-Key: dev-admin-key' localhost:8080/api/admin/reset`, then `/api/admin/seed`, then `/api/admin/match`; check groups and waiting reasons match docs/TASKS.md HACK-203.
4. Terminal B: set `VITE_USE_MOCKS=false` in `frontend/.env.development.local`, run `npm run dev`, walk the demo in docs/SPEC.md §B.3 at 375 px.

## Diagnosing a mismatch
1. Reproduce with `curl` and compare the JSON with `docs/API_CONTRACT.md` field by field.
2. Backend differs from contract → backend owner fixes. `types.ts`/mocks differ → Dev 3 fixes. Contract itself wrong → contract change procedure (docs/TASKS.md §I.3); never "fix" by silently changing one side.
3. 401 → check `Authorization: Bearer` header and that the token came from this backend instance (data file reset invalidates tokens).
4. Data looks stale → stop Quarkus, delete `backend/data/lunch2.json`, restart, reseed.
