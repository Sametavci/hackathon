# Lunch² demo — solo registration first

The first frontend release demonstrates register → wait → admin match → group → conversation cards → mutual connect. Friend invitations, QR sharing and friend management are deferred. Invite routes and API functions remain in place; registration sends `inviteCode: null`. The full “Bring a Friend. Meet Two.” scenario is not complete in this release. Dev 1 should align TASKS/SPEC and the backend team's priorities with this staged delivery.

## Frontend with mocks

```sh
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Mock mode defaults to `VITE_USE_MOCKS=true`. Open `/#/admin` in the same browser, enter the **mock-only** key `dev-admin-key`, and seed the fixtures once. No backend is needed for this mode.

The local fixture set has one ready group at Würzburg Mon 2 Nov 11:30, plus Aiko and Omar (international) waiting at Würzburg Tue 3 Nov 12:15. Register two fictional locally-based students at that second slot, with English and Würzburg selected; save each private return link. Run matching on Admin and refresh each student's My lunch screen. Matching is explicitly triggered, not automatic.

Mock data persists in localStorage and is shared by same-origin tabs. Tabs also share the active participant token: opening someone else's return link changes the active identity in all tabs. Protected screens discard the previous identity’s state when the token changes; use separate browser profiles for isolated active sessions with the real backend. Separate browser profiles have separate mock databases and cannot interact in mock mode. Clear this origin's site data to start a new mock demo. Seed is idempotent while its fixture registrations still exist.

## Real backend integration

Dev 1 supplies E1–E4 and E10–E13; Dev 2 supplies E8–E9 and A1–A4. E5–E7 are reserved for the deferred friend flow. The frozen API contract remains the integration source of truth.

Set `VITE_USE_MOCKS=false` in `frontend/.env.development.local`, start Quarkus with `cd backend && ./mvnw quarkus:dev`, and run Vite in another terminal. Vite proxies `/api` to port 8080. Check `curl -s http://localhost:8080/api/ping` returns `{"ok":true}`. API errors never silently fall back to mocks.

**Open contract issue:** A3 (`POST /api/admin/reset`) does not define its success status/body. The Reset button and endpoint adapter stay blocked until Dev 1 specifies it. Do not use the existing reset/seed demo script as a verified flow until that issue is resolved.

## Build one Quarkus application

From the repository root (Node ≥20 and Java 21 required):

```sh
./scripts/build-all.sh
LUNCH2_PUBLIC_BASE_URL=http://localhost:8080 java -jar backend/target/quarkus-app/quarkus-run.jar
```

The script installs locked frontend dependencies, runs checks, builds with `VITE_USE_MOCKS=false`, copies only generated SPA assets into Quarkus, and runs backend verification/package. It does not start a long-lived server. Launch from the root consistently so the relative JSON data-file location stays consistent.

The supplied Dockerfile currently targets Java 17; Dev 2 must update it before using Docker with this Java 21 project. The local demo does not require Docker or AWS.

## HTTPS phone demo

With `cloudflared` installed, start a tunnel in another terminal:

```sh
cloudflared tunnel --url http://localhost:8080
```

Keep the tunnel running. Restart the application with the returned HTTPS origin as `LUNCH2_PUBLIC_BASE_URL` (replace the example value):

```sh
LUNCH2_PUBLIC_BASE_URL=https://your-tunnel.trycloudflare.com java -jar backend/target/quarkus-app/quarkus-run.jar
```

Use the HTTPS origin on both phones, not localhost. Seed through Admin using the configured backend admin key. Dev 2's real seed fixtures are defined by HACK-203; they are richer than the local mock set. Do not reseed a dirty real database without the backend owner's reset procedure.

1. Register two fictional locally-based students with Würzburg, English and Tue 3 Nov 12:15. Save their return links privately.
2. On Admin run matching, then refresh My lunch on both phones.
3. Open the group, reveal all four members and check date, time, place and shared interests.
4. Use previous/next and swipe through conversation cards. AI is optional; Classic fallback is a valid demo result.
5. From two isolated participant sessions select each other. Refresh Connect to see MUTUAL, explicitly share a fictional contact, then refresh the other session.
6. Withdraw the contact and verify it disappears after refresh. Withdraw YES and verify mutual status ends.
7. Delete a registration. Its old return link must fail and the group must show “Former participant”.

## Acceptance checklist

- `cd frontend && npm run lint && npm test && npm run build`
- `cd backend && ./mvnw -q verify`
- Widths: 320, 375, 390, 430, 768, 1280 px; no horizontal overflow, long names and errors wrap.
- Real iOS Safari and Android Chrome: keyboard, registration, private return links, card gestures and buttons.
- Keyboard-only: labels, visible focus, toggles, confirmation and errors; reduced-motion setting; AA contrast and 44 px targets.
- Network failure and invalid participant/admin credentials produce clear states. Invalid participant credentials clear the participant session; admin failures do not.
- Group 403/404, card loading/failure/empty list and optional stats 404 are handled separately.
- Never publish return links or admin keys in logs, screenshots, recordings or shared slides.

Manual phone and real-backend results must be recorded separately from automated frontend checks. A frontend build cannot establish endpoint readiness or mobile browser acceptance.

## Integration check — 29 October 2026, main `4cf098c`

`git pull --ff-only origin main` completed without conflicts; local frontend work was preserved. Frontend typecheck, 31 tests and production build passed.

The backend now includes persistence, auth and the pure matching engine. Only `/api/ping` is registered as a production REST resource. E1–E13 and A1–A4 resources are still pending, so real registration/group/connect integration cannot pass yet. Live probes returned 200 for ping and the packaged SPA, but 500 `INTERNAL` for `/api/meta`, `/api/me`, `/api/groups/check` and `/api/admin/stats`. The catch-all `common/ErrorMapper` currently converts missing-route exceptions into 500 responses; Dev 1 should preserve expected HTTP errors as the contract requires.

Default JVM locale caused 11 RestAssured test errors on this machine. All 49 backend tests and packaging passed with the locale set before the forked JVM starts:

```sh
cd backend
JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US' ./mvnw -q verify
```

Passing only Maven `-Duser.language` / `-Duser.country` properties did not resolve the issue here. No backend-owned source files were changed. The backend owner should make the test runtime locale reproducible.

For the current checkpoint use the explicitly mocked Vite UI on port 5173; keep the real backend on 8080 for readiness checks. A successful backend build does not mean the pending REST resources exist.

## Updated registration and Mensa verification (2026-10-29)

The registration form now asks one question at a time: campus, experience, languages, interests,
optional study field, Mensa, day/time, nickname, then review/consent. Use Continue/Back; selections
survive navigation. Changing campus or Mensa clears selected times. Each Mensa offers 45-minute
lunches inside its own service hours; multiple days/times are allowed (15 total).

The green/mint/yellow palette applies throughout the UI, including selected chips, reveal cards,
conversation cards and shared controls. Selection checkmarks do not depend on color alone.

Backend E1–E4, E8 and A2 now support actual registration, JSON persistence, Mensa-aware matching
and location reveal. Other backend endpoint tickets remain separate; mock demo seed/connect/cards
remain available. Register four compatible fictional participants (2 domestic, 2 international) at
one Mensa/time to test the real registration → admin match → group-location flow. No group may mix
Mensas. Use separate browser profiles for participants; return links are private credentials.

Run `cd frontend && npm run lint && npm test && npm run build` and
`cd backend && ./mvnw -q verify`. On Turkish-locale laptops, RestAssured's failure handler may
mis-handle expected error responses: start the test JVM in English with
`JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US' ./mvnw -q clean verify`.
The static catalog describes normal weekly hours; exceptional closures are not live-synced.

### Recorded verification for this extension

- Frontend: `npm run lint && npm test && npm run build` — passed, 44 tests.
- Backend: `JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US' ./mvnw -q verify`
  — passed, 62 tests, no failures/errors/skips. Default-locale HTTP error-response tests
  fail in RestAssured on this Turkish-locale machine; the startup locale workaround is required here.
- Headless Chrome at 375 × 812 against real API: nine-step registration, required-step errors,
  backward navigation retaining selections, multiple days, clearing times on Mensa change,
  summary, reduced-motion styling and duplicate-submit guard passed. No horizontal overflow
  was found on the tested steps; one registration was persisted with the chosen Mensa.
- Text/background palette pairs checked: contrast ratios 5.58:1–12.26:1 (AA normal text).
- Screenshots of Mensa/time steps visually inspected. Native iOS Safari and real-device
  keyboard testing remain manual checks; no result is claimed for those devices.


## Pull integration check — main `fb288a9`

Pulled 17 commits from origin/main (previous HEAD `4cf098c`), including four commits
that arrived during verification. Incoming participant, invite, connect, group, matching/reset,
snapshot-retry, demo-seed and fallback-card implementations are preserved.
The local registration/theme changes remain in the working tree. MensaService now supplies
the additional catalog/availability while the incoming CatalogService and SlotService keep
their existing public APIs. Mensa preferences survive incoming match, invite, unlink and
friend-deletion operations; integration tests cover these combinations.

The reset endpoint's 204 response is now specified in API_CONTRACT A3. The frontend Reset
button is still disabled by the earlier implementation; enabling its UI remains a separate
follow-up. The real seed and cached fallback-card endpoints are now implemented; the optional
stats endpoint and AI card generator are still absent.

Local work is backed up in stash `codex-before-pull-registration-theme-2026-10-29` and
`/private/tmp/lunch2-before-pull-20261029-115639/working-files.tar.gz`.

Verification after this pull: frontend lint + 44 tests + production build passed; backend
`JAVA_TOOL_OPTIONS='-Duser.language=en -Duser.country=US' ./mvnw -q verify` passed
150 tests with no failures/errors/skips; infrastructure `npm run build` passed.
Packaged application smoke check passed: root UI HTTP 200, `/api/ping` OK, catalog with
five Mensas and 65 deduplicated global slots. The smoke used an isolated temporary data file.

The final packaged-app smoke additionally passed: seed 18 fictional participants, match one
valid 2+2 group, retrieve/cache fallback cards, opt into mutual connections, share/withdraw a
fictional contact, reset only the isolated demo store, and confirm token revocation. The server
was shut down after the check. A second backup/stash protects the integrated local changes:
`codex-before-final-pull-integration-2026-10-29`,
`/private/tmp/lunch2-before-final-pull-20261029-120151/working-files.tar.gz`.
