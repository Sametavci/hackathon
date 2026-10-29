# AGENTS.md – Lunch² (6-hour hackathon, 3 developers)

## Project
Lunch² ("Bring a Friend. Meet Two.") is a mobile-first web app for THWS: a student registers in ~60 s via QR code,
optionally brings a friend, and is matched into a lunch group of 4 (2 locally-based + 2 international students,
same campus, shared slot, shared language). Members see a group reveal, AI conversation cards, and can opt in
to mutual contact sharing.
- **MVP goal:** one reliable end-to-end demo: register → invite friend → match 2+2 → reveal → cards → mutual connect.
- **Non-goals:** chat, notifications, SSO/passwords, admin dashboard beyond 3 buttons, DynamoDB/Lambda, profile editing, German UI.
- **Stack:** Java 21 + Quarkus 3.40 (REST, Jackson, Hibernate Validator) · React 18 + TypeScript + Vite 5 + Tailwind 3 · JSON-file persistence · Gemini API for cards.

Docs (read the relevant part **before** coding): `docs/SPEC.md` (what/acceptance), `docs/ARCHITECTURE.md`
(modules, ownership, matching algorithm), `docs/API_CONTRACT.md` (endpoints, DTOs, interfaces, config – **frozen**),
`docs/TASKS.md` (tickets, your queue, Git rules).

## Architecture rules
- Modular monolith. Backend packages `de.thws.lunch2.<module>`; frontend `frontend/src/{api,components,pages,i18n}`.
- Dependency direction: `resource → service → persistence/domain`. `matching.MatchingEngine` is pure Java (no CDI, no repositories).
  Dev 2 modules may inject Dev 1's repositories, `CurrentParticipant`, `CatalogService`, `ApiException`; Dev 1 modules never import `matching`, `group`, `cards`, `admin`.
- Frontend pages call only `src/api/endpoints.ts`; never `fetch` directly. Mocks via `VITE_USE_MOCKS=true`.
- API: `/api/...`, JSON camelCase, enums UPPER_SNAKE, IDs not labels, error envelope `{ "error": { code, message, details } }`.
- One Quarkus process serves the API (and later the built SPA). In dev, Vite proxies `/api` → `:8080`, so no CORS.

## Ownership (never edit files you don't own)
| Dev | Owns |
|---|---|
| Dev 1 – platform, identity, connect, integration lead | `backend/pom.xml`, `application.properties`, packages `common, domain, persistence, auth, participant, invite, connect`; `frontend/src/pages/ConnectPage*.tsx`, `components/connect/`; `docs/*`, `AGENTS.md`, `README.md`, `.claude/` |
| Dev 2 – matching, groups, cards, admin, demo | packages `matching, group, cards, admin`; `backend/src/main/resources/seed/`, `backend/src/main/docker/`, `scripts/`, `infra/` (AWS CDK); `frontend/src/pages/AdminPage*.tsx` |
| Dev 3 – frontend experience | everything else in `frontend/` (incl. `App.tsx`, `api/`, `i18n/`, `package.json`) |
Tests live next to the code owner's package (`backend/src/test/java/de/thws/lunch2/<module>/`).

## Coding standards
- **Java:** records for DTOs and engine types; constructor injection; resources thin, logic in `@ApplicationScoped` services;
  Bean Validation annotations on request DTOs; throw `ApiException(status, code, message)` for expected errors.
  Always pass `Locale.ROOT` to `toUpperCase/toLowerCase/String.format` (dev laptops use a Turkish locale: `"i".toUpperCase()` ≠ `"I"`).
- **TypeScript:** `strict`, no `any`; types mirror `docs/API_CONTRACT.md` exactly; function components; Tailwind only; files `PascalCase.tsx` for components/pages, `camelCase.ts` otherwise.
- **Naming:** Java classes `XxxResource`, `XxxService`, `XxxRepository`, `XxxRequest`/`XxxResponse`; tickets `HACK-nnn`.
- **Logging:** JBoss/Quarkus `Log` at INFO for state changes (e.g. "match run created 3 groups"); never log tokens, contacts, display names or request bodies.
- **Privacy:** expose only fields allowed by API_CONTRACT; never return tokens (except once on registration), categories/languages per member, or contacts unless MUTUAL + shared.
- **UI:** mobile-first (test at 375 px), labelled controls, visible focus, 44 px touch targets, AA contrast, `prefers-reduced-motion`, strings via `t()`.
- **Tests:** every endpoint ticket adds RestAssured `@QuarkusTest`s for its acceptance criteria; matching has plain JUnit tests; frontend logic (client, mocks) gets Vitest tests.

## AI agent rules
0. Keep new features prototype-sized unless the user requests more. For every feature
   change, update the relevant SPEC, ARCHITECTURE, API_CONTRACT, TASKS and README sections
   in the same branch so subsequent work follows the implemented behavior. HACK-111's
   scoped games/card/frontend extension is authorized in TASKS.md; unrelated ownership
   and contract restrictions remain in force.
1. Read the ticket in `docs/TASKS.md` and the contract sections it references first.
2. Follow the contracts and architecture; never rename/remove a field, endpoint, enum or config key. Missing or wrong contract → stop and report.
3. Touch only files needed for the current ticket and owned by your developer. No unrelated refactors, no reformatting of others' code.
4. No new dependencies. Backend: JDK + extensions already in `pom.xml`. Frontend: packages already in `package.json`.
5. Never overwrite or revert another developer's work; if `git pull` brings conflicts in their files, take theirs.
6. Prefer the simplest readable implementation that meets the acceptance criteria.
7. Run the verification commands below and report the real output. Never claim tests passed unless you ran them.
8. Finish with: changed files, how to verify, open issues, anything other developers must do.

## Parallel development rules
- Shared files (`pom.xml`, `application.properties`, `package.json`, `App.tsx`, `docs/API_CONTRACT.md`) change only by their owner, after the team agrees.
- Contract change: propose in team chat → both affected owners agree → Dev 1 edits `docs/API_CONTRACT.md` → owners update DTOs/`types.ts`. After 2:30 only additive optional fields.
- One ticket per branch `d<n>/HACK-nnn-short-name`; rebase on `main`, run checks, merge within ~45 min. `main` must always build.

## Commands
| Task | Backend (`cd backend`) | Frontend (`cd frontend`) |
|---|---|---|
| Install | `./mvnw -q -DskipTests package` (downloads deps) | `npm install` |
| Dev | `./mvnw quarkus:dev` → http://localhost:8080 | `npm run dev` → http://localhost:5173 |
| Build | `./mvnw -q package` | `npm run build` |
| Lint/typecheck | `./mvnw -q compile` (compiler only, no linter configured) | `npm run lint` (`tsc --noEmit`) |
| Test | `./mvnw -q verify` (single test: `./mvnw -q test -Dtest=PingResourceTest`) | `npm test` |
Deploy (AWS, see README): `./scripts/deploy.sh` · teardown `./scripts/destroy.sh` · infra checks `cd infra && npm run build && npm test` (CDK stack assertions + script tests, nothing is deployed).
Smoke check: `curl -s localhost:8080/api/ping` → `{"ok":true}`.
