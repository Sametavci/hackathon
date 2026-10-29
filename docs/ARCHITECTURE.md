# Lunch² – Architecture

## Play Together prototype extension (HACK-111, 2026-10-29)

GroupPage links to PlayPage at `#/group/:groupId/play`; the existing member-only
`getGroup` endpoint gates both the catalog and direct game links. Matching membership
is sufficient: no schedule check, attendance verification, check-in or levels.
`components/games/gameCatalog.ts` registers metadata and a renderer per game. Only
Conversation Shuffle (`icebreakers`) has a renderer; future games are disabled and
clearly marked coming soon. Add a component and catalog entry to extend this local model.

Opening the hub does not fetch or generate questions. Starting Icebreakers mounts
ConversationCards, which calls only the existing `getCards` endpoint. The wire deck
remains five cached questions. The frontend adds the ten shared classics for `en`,
deduplicates and shuffles copies locally; other languages keep their translated server
deck. Skip, shuffle, replay and finishing make no additional AI requests. Each phone
has its own order/progress, reset on leaving/reloading; there is no shared game session,
polling, scoring, answer text or audio storage.

The English bank lives in `backend/src/main/resources/cards/icebreakers.en.json`.
Existing non-English generic fallback questions live in `generic-translations.json`.
IcebreakerQuestions loads these immutable resources for FallbackCards; frontend mocks
import the same JSON. Existing generated sets, cache locking, timeout and provider
privacy rules are unchanged. HACK-111's narrow coordinated ownership scope is recorded
in TASKS.md; no general module-ownership transfer is implied.

> Rules for module boundaries live here; endpoint and DTO details live in `API_CONTRACT.md`.

## 0. Assumptions (stated, not asked)

| # | Assumption | Why |
|---|---|---|
| A1 | **Team = 3 developers.** The brief says "exactly 3" in Context/Constraints and uses owners "Developer 1, 2, 3", but "four" in several later sections. The plan is built for 3; **TASKS.md §F.4 gives a drop-in split if a 4th developer joins.** | Contradiction in brief; 3 is the stricter case. |
| A2 | Every laptop has JDK 21, Maven 3.9+, Node 20+, Git, and an AI coding agent. Checked at 0:00–0:10. | Environment failures are a top risk. |
| A3 | A Gemini API key may be configured (`gemini-3.8-flash` by default). If not → fallback cards only; no plan change. | AI is behind an interface. |
| A4 | AWS access is uncertain. Cloud deploy is **optional**; the guaranteed demo path is laptop + HTTPS tunnel (cloudflared quick tunnel) so phones can scan a QR code. | MVP must run locally. |
| A5 | "Domestic/international" is self-declared ("I've lived in Germany for a while" / "I'm new to Germany"), not nationality or ethnicity. | GDPR data minimisation, avoids Art. 9-adjacent data. |
| A6 | One matching round = each participant can be in at most one active group. This trivially guarantees "no overlapping assignments". | Simplest correct rule. |
| A7 | A friend pair is **never split by the algorithm**. If a pair can't be placed, both wait with an explained reason and get a "Match us separately" button. | Turns a soft promise into a clear, testable rule. |
| A8 | "At least one shared communication language" = one language spoken by **all four** members. | Strictest reasonable reading. |
| A9 | Judging criteria unknown → optimise for: Fit4Germany relevance, motivation for German students, working demo, UX, low cost. | Per brief. |

---


## C. Proposed Architecture

### C.1 Stack (deliberately boring)
| Layer | Choice | Rejected alternative & why |
|---|---|---|
| Frontend | React 18 + TS + Vite + Tailwind, `react-router-dom` (HashRouter), `qrcode.react`, Vitest | Next.js – needless SSR |
| Backend | Java 21, Quarkus 3 (LTS), `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-validator`, `quarkus-junit`, `rest-assured` | Microservices / Lambda – cold starts, more infra |
| Persistence | In-memory maps + **JSON snapshot file** behind repository interfaces | DynamoDB (IAM/infra time), JPA+SQLite (dialect friction, `@ElementCollection` for lists) |
| AI | Gemini API via JDK `java.net.http.HttpClient` (no new dependency), `gemini-3.8-flash` | LLM in matching – non-deterministic |
| Hosting | One Quarkus jar serving API **and** built SPA from `META-INF/resources` → same origin, no CORS. AWS: CDK stack in `infra/` = one free-tier t3.micro EC2 + Elastic IP in a public-subnet-only VPC, port 80, secrets in SSM Parameter Store, deployed with `scripts/deploy.sh`. Fallback: laptop + tunnel. | Amplify + API Gateway + Lambda / ALB / RDS – more services and idle cost |

### C.2 Modules & ownership
```
lunch2/
├─ AGENTS.md, CLAUDE.md, .claude/skills/  # agent rules + skills – Dev 1
├─ docs/PLAN.md, docs/API_CONTRACT.md  # contracts – Dev 1 (change procedure §I.3)
├─ docs/PRIVACY.md, docs/DEMO.md    # Dev 1 / Dev 2
├─ scripts/demo.sh, build-all.sh     # Dev 2
├─ backend/                          # Maven, de.thws.lunch2
│  ├─ pom.xml, application.properties               ── Dev 1 (all deps & keys pre-declared in HACK-101)
│  └─ src/main/java/de/thws/lunch2/
│     ├─ common/      ApiException, ErrorMapper, CatalogService, SlotService      ── Dev 1
│     ├─ domain/      Participant, FriendPair, Invite, LunchGroup, CardSet, ConnectionChoice, enums ── Dev 1
│     ├─ persistence/ *Repository interfaces + JsonFileStore                      ── Dev 1
│     ├─ auth/        TokenService, AuthFilter, CurrentParticipant, @Authenticated, @AdminOnly ── Dev 1
│     ├─ participant/ MetaResource, ParticipantResource, MeResource, ParticipantService, DTOs ── Dev 1
│     ├─ invite/      InviteResource, InviteService, DTOs                          ── Dev 1
│     ├─ connect/     ConnectionResource, ConnectionService, DTOs                  ── Dev 1
│     ├─ matching/    MatchCandidate, MatchingEngine, GroupValidator, MatchService ── Dev 2
│     ├─ group/       GroupResource, GroupService, DTOs                            ── Dev 2
│     ├─ cards/       CardsResource, CardService, GeminiCardGenerator, FallbackCards ── Dev 2
│     └─ admin/       AdminResource, SeedService, StatsService; resources/seed/demo-participants.json ── Dev 2
└─ frontend/
   ├─ package.json, vite.config.ts, tailwind config                ── Dev 3
   └─ src/
      ├─ App.tsx (router, all routes pre-wired)                    ── Dev 3
      ├─ api/ client.ts, types.ts, endpoints.ts, mocks.ts, session.ts ── Dev 3
      ├─ i18n/en.ts                                                ── Dev 3 (others add keys only via own `*.strings.ts` files)
      ├─ components/ (Chip, Button, Card, Layout, QrShare, ConversationCards…) ── Dev 3
      └─ pages/ Landing, Register, Join, Restore, Me, Group, Privacy ── Dev 3 · ConnectPage ── Dev 1 · AdminPage ── Dev 2
```
**Cross-module calls allowed:** Dev 2 code may *inject* Dev 1's repositories, `CurrentParticipant`, `CatalogService`, `ApiException`. Dev 1 code never imports `matching/`, `group/`, `cards/`. Frontend pages only call `api/endpoints.ts`.

### C.3 Matching algorithm (spec for HACK-201)
1. **Pre-check friend pairs** (`friendId` set on both): different campus → both `FRIEND_DIFFERENT_CAMPUS`; no common slot → `FRIEND_NO_COMMON_SLOT`; no common language → `FRIEND_NO_COMMON_LANGUAGE`. Such candidates are excluded from this round (they wait with that reason).
2. For each campus (enum order), for each slot (chronological):
   - `pool` = unassigned candidates on that campus with that slot. Cap each category at the 40 earliest-registered (bounds enumeration ≤ 780² ≈ 600k combos).
   - Enumerate every {2 DOMESTIC} × {2 INTERNATIONAL}. **Valid** iff: all on campus, all have slot, ∩languages ≠ ∅, and for every member with a `friendId` the friend is also in the set.
   - **Score** = `10 × friendPairsInside` + `Σ pairwise |interests ∩|` × 2 + `1` if ≥3 distinct study fields + `1` if `en` or `de` in common languages − `8 × previouslyMetPairs`.
   - Pick max score; tie-break by lexicographically smallest sorted ID list (determinism). Commit, remove from pool, repeat until no valid set.
3. Every remaining candidate gets the first matching reason: (friend issue) → `NOT_ENOUGH_DOMESTIC` / `NOT_ENOUGH_INTERNATIONAL` (count by category over all their slots on campus) → `NO_COMMON_LANGUAGE` → fallback `NOT_ENOUGH_INTERNATIONAL`/`DOMESTIC` by the scarcer category.
4. `MatchService` runs `GroupValidator.violations()` on every proposed group **before** persisting; any violation → throw (never persist an invalid group).

Why not optimal (ILP/max-flow)? Greedy-with-validation is explainable, deterministic, and correct by construction; optimality isn't a hackathon requirement.

---


## Data flow

```
Phone (React SPA, HashRouter) ──fetch /api/*──▶ Quarkus resource ──▶ service ──▶ repository interface ──▶ JsonFileStore (in-memory maps + data/lunch2.json)
                                                    │
                       MatchService ──maps to MatchCandidate──▶ MatchingEngine (pure Java) ──▶ GroupValidator ──▶ LunchGroup saved
                       CardService ──interest labels + language only──▶ Gemini API (8 s timeout) ──fallback──▶ FallbackCards ──▶ cached in LunchGroup
```

Dependency direction: `resource → service → persistence/domain`. `matching` engine depends on nothing but its own records. Dev 2 modules may inject Dev 1's repositories, `CurrentParticipant`, `CatalogService`, `ApiException`; Dev 1 modules never import `matching`, `group`, `cards`, `admin`.

## Architectural decisions
| Problem found in draft | Revision |
|---|---|
| Matching engine depended on Dev 1's `Participant` entity → Dev 2 blocked until ~1:10 | Engine takes its own `MatchCandidate` records; `MatchService` maps. Engine starts at 0:30. |
| `App.tsx`, `endpoints.ts`, `pom.xml`, `application.properties` would be edited by 3 people | Pre-allocated all routes, endpoint functions, deps, config keys in the skeleton tickets. |
| Dev 3 had ~285 min of frontend | Moved Connect UI to Dev 1 (makes Connect a full vertical slice) and admin page to Dev 2 (optional). |
| SPA deep links + CORS + two hosts | HashRouter + Quarkus serves SPA + Vite proxy → zero CORS, one deployable. |
| DynamoDB/Lambda would consume an hour of IAM/infra | JSON file store behind repositories; AWS optional at the end. |
| "Keep friends together where possible" was fuzzy and hard to test | Rule A7: never split; explain + offer unlink. Fully testable. |
| AI call at matching time = cost for unopened groups, slows matching | Lazy on first `GET /cards`, cached in group, fallback shipped first. |
| Connect "no" could leak via status | `WAITING_FOR_THEM` for both "no" and "no answer"; incoming requests hidden. |
| Integrated MVP at 3:30 vs Connect UI at 4:00 | Accepted honestly: core MVP (register→invite→match→reveal→cards) at CP4 3:30, Connect complete by 4:00 (its API is test-verified by 3:25). |
| Dev 2 is the heaviest queue | Fallbacks pre-agreed (simplified engine, 108 → Dev 1). |

### Mensa-aware registration extension (2026-10-29)
The approved scope adds a nine-step registration wizard and a shared static Mensa catalog
(`backend/src/main/resources/catalog/mensas.json`, imported by frontend mocks). See API_CONTRACT §7.
MensaService extends the incoming CatalogService/SlotService APIs with five weekdays and per-Mensa 45-minute availability. ParticipantService
validates campus/Mensa/slots and persists `mensaId`; missing legacy values resolve to campus defaults.
MatchService checks friend compatibility before partitioning candidates by Mensa, then invokes the
unchanged pure MatchingEngine per partition and validates every proposal before saving groups.
Existing group location strings remain intact; new groups store the catalog name and address.

## Frontend locale and landing motion (HACK-309, 2026-10-29)

`frontend/src/i18n/index.ts` owns the locale and exposes a `useSyncExternalStore`
subscription through `useI18n`. Typed `en.ts`/`de.ts` dictionaries require matching
keys; existing page/game string modules delegate to this shared dictionary.
`presentation.ts` maps catalog IDs, known error/reason codes, dates and existing
group explanation templates to localized display values. Locale updates preserve
component identity and form state, set HTML `lang`, and persist `lunch2.locale`.
`useLandingMotion` coordinates the four-second headline cycle with pause, visibility
and reduced-motion state; the table uses a 24-second CSS rotation with counter-
rotating faces. Timers and listeners are cleaned up on unmount. No new dependencies
or backend changes are needed.

## Buddy frontend integration (2026-10-29)

InviteService builds a public plan preview from the inviter's current saved registration.
Only Mensa ID, slot IDs and match status are added; tokens, contacts, category and
languages stay private. JoinPage validates this plan against meta. RegisterPage loads
it again from the invite code on direct navigation/reload, skips campus/Mensa steps,
limits available times to inviter slots and submits the actual inviteCode. It rechecks
the invite before submitting; backend registration remains authoritative for races.
A missing/inconsistent preview blocks inherited registration rather than guessing.
BuddyFlow contains plan validation and wizard steps. QrShare uses existing qrcode.react,
Web Share and clipboard (manual selection fallback). MePage reads current buddy state
with Refresh, and unlink reloads it; no polling or existing-user linking endpoint.

Buddy session correction: JoinPage never substitutes the saved session's My lunch
for a valid invitation. BuddySessionChoice offers an explicit different-person route
(register?invite=CODE&new=1) and the old participant's private return-link control.
RegisterPage validates the public invite even with an existing token; without new=1
it shows the same identity choice. The name-first buddy wizard uses an explicit step
sequence (name -> category -> languages -> interests -> study -> slots -> consent).
POST /participants remains unauthenticated and creates a new participant. Only its
successful response replaces the active browser token; failed/cancelled forms leave
the original session intact. The switch also affects other tabs sharing localStorage.
