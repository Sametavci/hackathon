# Lunch² — API & Data Contracts (FROZEN at 0:30)

> Single source of truth for frontend ↔ backend and module ↔ module interfaces.
> Changing anything here after 0:30 follows the **contract change procedure** in `docs/TASKS.md` §I.3.
> Java DTOs (backend) and `frontend/src/api/types.ts` (frontend) MUST mirror this file exactly.

---

## 1. Global conventions

| Topic | Convention |
|---|---|
| Base path | `/api` (same origin in prod; Vite dev server proxies `/api` → `http://localhost:8080`) |
| Format | JSON, UTF-8, `camelCase` fields, enums as UPPER_SNAKE strings |
| IDs | Participant/Group IDs: UUID v4 strings. Invite code: 10 chars, Crockford base32, `SecureRandom` |
| Auth (participant) | `Authorization: Bearer <accessToken>`. Token = 32 random bytes, base64url (43 chars). Server stores **only SHA-256 hash**. |
| Auth (admin/demo) | Header `X-Admin-Key: <LUNCH2_ADMIN_KEY>` |
| Time | Slot IDs are local Europe/Berlin wall time `YYYY-MM-DDTHH:mm`. `startsAt` is ISO-8601 with offset. |
| Labels | Backend sends **IDs**; labels come from `GET /api/meta` (frontend maps). Enables later German i18n. |
| Nulls | Optional fields are present with `null`, never omitted. Lists are never `null` (use `[]`). |

### Error envelope (all non-2xx)

```json
{
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Please pick at least one language.",
    "details": [{ "field": "languages", "message": "must contain 1-4 items" }]
  }
}
```

| HTTP | `code` values |
|---|---|
| 400 | `VALIDATION_FAILED`, `CONSENT_REQUIRED`, `UNKNOWN_SLOT` |
| 401 | `UNAUTHORIZED` (missing/invalid token or admin key) |
| 403 | `FORBIDDEN` (not a member of the group) |
| 404 | `NOT_FOUND`, `INVITE_INVALID` |
| 409 | `ALREADY_PAIRED`, `INVITE_USED`, `NOT_MUTUAL`, `NOT_MATCHED`, `DEMO_NOT_EMPTY` |
| 500 | `INTERNAL` (never leak stack traces) |

`message` is a human-readable English sentence safe to show in the UI.

---

## 2. Enums & catalogs

```
Category        = DOMESTIC | INTERNATIONAL        // UI: "I've lived in Germany for a while" / "I'm new to Germany" (self-declared, NOT nationality)
Campus          = WUERZBURG | SCHWEINFURT
MatchStatus     = WAITING | MATCHED
PairStatus      = ACTIVE | INCOMPATIBLE | UNLINKED
ConnectChoice   = YES | NO
ConnectionStatus= NONE | WAITING_FOR_THEM | MUTUAL
CardSource      = AI | FALLBACK
```

**Waiting / issue reason codes** (`ReasonCode`):

| code | meaning (default English message) |
|---|---|
| `WAITING_FOR_NEXT_ROUND` | Matching has not run since you registered. |
| `NOT_ENOUGH_DOMESTIC` | Not enough locally-based students share your campus & time slots yet. |
| `NOT_ENOUGH_INTERNATIONAL` | Not enough international students share your campus & time slots yet. |
| `NO_COMMON_LANGUAGE` | No group with a shared language is possible yet. Adding a language helps. |
| `FRIEND_DIFFERENT_CAMPUS` | You and your friend chose different campuses. |
| `FRIEND_NO_COMMON_SLOT` | You and your friend have no lunch slot in common. |
| `FRIEND_NO_COMMON_LANGUAGE` | You and your friend have no language in common. |
| `INVITER_ALREADY_MATCHED` | Your friend was already matched before you joined; you'll be matched on your own. |

Catalogs (served by `GET /api/meta`, defined in backend `CatalogService`, owned by Dev 1):

- **languages** (`code`): `en, de, tr, ar, es, fr, it, pt, ru, uk, zh, hi`
- **interests** (`id`): `gaming, travel, tech, football, music, cooking, hiking, movies, photography, art, reading, fitness, startups, languages, anime, boardgames`
- **studyFields** (`id`): `COMPUTER_SCIENCE, ENGINEERING, BUSINESS, DESIGN, SOCIAL_SCIENCES, HEALTH, OTHER`
- **slots**: 5 weekdays starting `lunch2.event-start-date` (default `2026-11-02`) × times `11:30, 12:15, 13:00`, each 45 min.
- **campus locations** (config, placeholder text): `WUERZBURG → "Mensa Würzburg – Lunch² table (yellow sign)"`, `SCHWEINFURT → "Mensa Schweinfurt – Lunch² table (yellow sign)"`.

---

## 3. REST endpoints

| # | Method & path | Auth | Owner | Purpose |
|---|---|---|---|---|
| E1 | `GET /api/meta` | – | Dev 1 | Catalogs + slots |
| E2 | `POST /api/participants` | – | Dev 1 | Register (optionally with `inviteCode`) |
| E3 | `GET /api/me` | Bearer | Dev 1 | My profile + match/pair status |
| E4 | `DELETE /api/me` | Bearer | Dev 1 | Delete all my data (GDPR erase) |
| E5 | `POST /api/me/invite` | Bearer | Dev 1 | Create (or return existing) friend invite |
| E6 | `GET /api/invites/{code}` | – | Dev 1 | Public invite preview |
| E7 | `POST /api/me/friend/unlink` | Bearer | Dev 1 | "Match us separately" |
| E8 | `GET /api/groups/{groupId}` | Bearer, member | Dev 2 | Group reveal |
| E9 | `GET /api/groups/{groupId}/cards` | Bearer, member | Dev 2 | Conversation cards (lazy-generated, cached) |
| E10 | `GET /api/groups/{groupId}/connections` | Bearer, member | Dev 1 | My connect choices for the other 3 members |
| E11 | `PUT /api/groups/{groupId}/connections/{participantId}` | Bearer, member | Dev 1 | Set YES/NO |
| E12 | `PUT /api/groups/{groupId}/connections/{participantId}/contact` | Bearer, member | Dev 1 | Share my contact with a MUTUAL connection |
| E13 | `DELETE /api/groups/{groupId}/connections/{participantId}/contact` | Bearer, member | Dev 1 | Withdraw shared contact |
| A1 | `POST /api/admin/seed` | Admin | Dev 2 | Load fictional demo data, return demo restore links |
| A2 | `POST /api/admin/match` | Admin | Dev 2 | Run matching round |
| A3 | `POST /api/admin/reset` | Admin | Dev 2 | Wipe all data |
| A4 | `GET /api/admin/stats` | Admin | Dev 2 | Success metrics (optional) |
| H1 | `GET /q/health` | – | Dev 1 | Health (optional extension) |

### E1 `GET /api/meta` → 200 `MetaResponse`

```json
{
  "campuses": [{ "id": "WUERZBURG", "label": "Würzburg", "location": "Mensa Würzburg – Lunch² table (yellow sign)" }],
  "languages": [{ "code": "en", "label": "English" }, { "code": "de", "label": "Deutsch" }],
  "interests": [{ "id": "travel", "label": "Travel", "emoji": "✈️" }],
  "studyFields": [{ "id": "COMPUTER_SCIENCE", "label": "Computer Science" }],
  "slots": [{ "id": "2026-11-03T12:15", "date": "2026-11-03", "start": "12:15", "end": "13:00", "label": "Tue 3 Nov · 12:15" }]
}
```

### E2 `POST /api/participants` — `RegistrationRequest` → 201 `RegistrationResponse`

```json
{
  "displayName": "Lena",
  "category": "DOMESTIC",
  "campus": "WUERZBURG",
  "languages": ["de", "en"],
  "interests": ["travel", "gaming", "cooking"],
  "slotIds": ["2026-11-03T12:15", "2026-11-04T12:15"],
  "studyField": "COMPUTER_SCIENCE",
  "inviteCode": null,
  "consent": true
}
```

Validation (Bean Validation + service checks):
- `displayName`: 2–30 chars, regex `^[\p{L}\p{N} ._-]+$` (no emails/phones)
- `languages`: 1–4 known codes · `interests`: 1–5 known ids · `slotIds`: 1–15 known ids · `studyField`: null or known id
- `consent` must be `true` → else 400 `CONSENT_REQUIRED`
- `inviteCode` (optional): must exist & be unused → else 404 `INVITE_INVALID` / 409 `INVITE_USED`

Response:

```json
{
  "accessToken": "kq3...43chars",
  "restoreUrl": "https://lunch2.example/#/back?t=kq3...",
  "me": { "...": "MeResponse" }
}
```

Invite registration side effects: invite marked used; `FriendPair` created; compatibility checked immediately (campus, ≥1 common slot, ≥1 common language). Incompatible → pair `INCOMPATIBLE` with `issue` reason; registration still succeeds. If inviter is already `MATCHED` → pair `INCOMPATIBLE` with `INVITER_ALREADY_MATCHED`.

### E3 `GET /api/me` → 200 `MeResponse`

```json
{
  "id": "6f1c...-uuid",
  "displayName": "Lena",
  "category": "DOMESTIC",
  "campus": "WUERZBURG",
  "languages": ["de", "en"],
  "interests": ["travel", "gaming", "cooking"],
  "slotIds": ["2026-11-03T12:15"],
  "studyField": "COMPUTER_SCIENCE",
  "matchStatus": "WAITING",
  "waitingReason": { "code": "NOT_ENOUGH_INTERNATIONAL", "message": "Not enough international students share your campus & time slots yet." },
  "groupId": null,
  "friend": {
    "displayName": "Sam",
    "pairStatus": "INCOMPATIBLE",
    "issue": { "code": "FRIEND_NO_COMMON_SLOT", "message": "You and your friend have no lunch slot in common." }
  },
  "pendingInvite": null
}
```

- `waitingReason` non-null only when `matchStatus = WAITING`. `groupId` non-null only when `MATCHED`.
- `friend` is `null` if no pair or pair `UNLINKED`. `pendingInvite` = `{ "code", "url" }` if I created an unused invite.

### E4 `DELETE /api/me` → 204
Deletes participant, my invites, my friend pair (friend's pair becomes `UNLINKED`), all my connection choices and shared contacts. In existing groups my member entry is replaced by a tombstone (`displayName: "Former participant"`, no interests). Token stops working immediately.

### E5 `POST /api/me/invite` → 201 `InviteResponse` (idempotent: returns existing unused invite)

```json
{ "code": "K7Q2M9XA3P", "url": "https://lunch2.example/#/join/K7Q2M9XA3P" }
```
409 `ALREADY_PAIRED` if I already have an ACTIVE/INCOMPATIBLE pair.

### E6 `GET /api/invites/{code}` → 200 `InvitePreview`

```json
{ "code": "K7Q2M9XA3P", "inviterDisplayName": "Lena", "campus": "WUERZBURG", "valid": true }
```
`valid=false` if already used (still 200, so UI can explain). Unknown code → 404 `INVITE_INVALID`.

### E7 `POST /api/me/friend/unlink` → 200 `MeResponse`
Pair → `UNLINKED`; both are matched individually from the next round.

### E8 `GET /api/groups/{groupId}` → 200 `GroupResponse` (403 if caller not a member)

```json
{
  "id": "0b9e...-uuid",
  "campus": "WUERZBURG",
  "slot": { "id": "2026-11-03T12:15", "date": "2026-11-03", "start": "12:15", "end": "13:00", "label": "Tue 3 Nov · 12:15" },
  "startsAt": "2026-11-03T12:15:00+01:00",
  "location": "Mensa Würzburg – Lunch² table (yellow sign)",
  "members": [
    { "id": "6f1c...", "displayName": "Lena", "isMe": true,  "isMyFriend": false, "interests": ["travel", "gaming"] },
    { "id": "a21d...", "displayName": "Sam",  "isMe": false, "isMyFriend": true,  "interests": ["travel", "music"] },
    { "id": "c88f...", "displayName": "Aiko", "isMe": false, "isMyFriend": false, "interests": ["gaming", "anime"] },
    { "id": "d47b...", "displayName": "Omar", "isMe": false, "isMyFriend": false, "interests": ["travel", "football"] }
  ],
  "sharedInterests": ["travel", "gaming"],
  "commonLanguages": ["en"],
  "whyThisGroup": ["3 of you love Travel", "Everyone speaks English", "Lena & Sam signed up together"]
}
```
**Never** included: category, languages per member, study field, tokens, contact info. `sharedInterests` = interests held by ≥2 members, sorted by count desc then id.

### E9 `GET /api/groups/{groupId}/cards` → 200 `CardsResponse`

```json
{
  "groupId": "0b9e...",
  "language": "en",
  "source": "AI",
  "cards": [
    { "id": "c1", "text": "If you could travel anywhere tomorrow, where would you go?", "topic": "travel" },
    { "id": "c2", "text": "Which game would you recommend to everyone at this table?", "topic": "gaming" }
  ]
}
```
5 cards. First call generates (AI with 8 s timeout, else fallback) and **persists in the group**; later calls return the cache. Never 5xx for AI failure — return `source: "FALLBACK"`.

Play Together prototype (§8): this endpoint still returns exactly five cached cards with
the same DTO and auth rules. Its English fallback generic pool uses the ten supplied
classic questions in `resources/cards/icebreakers.en.json`. The browser combines these
classics with English `/cards` results for the local game; the displayed deck can therefore
contain more than five questions. `source: AI` describes the server set, and the game labels
the combined deck "AI + classic questions". Old cached sets are returned unchanged.

### E10 `GET /api/groups/{groupId}/connections` → 200 `ConnectionItem[]` (other 3 members only)

```json
[
  { "participantId": "a21d...", "displayName": "Sam",  "myChoice": "YES", "status": "MUTUAL",           "myContact": "insta @lena.w", "theirContact": "insta @sam.k" },
  { "participantId": "c88f...", "displayName": "Aiko", "myChoice": "YES", "status": "WAITING_FOR_THEM", "myContact": null,            "theirContact": null },
  { "participantId": "d47b...", "displayName": "Omar", "myChoice": null,  "status": "NONE",             "myContact": null,            "theirContact": null }
]
```
Privacy rules (enforced server-side, covered by tests):
- `MUTUAL` only when both chose `YES`. If I chose `YES` and the other chose `NO` **or nothing**, I see `WAITING_FOR_THEM` (a "no" is never revealed).
- The other person's choice is never exposed unless MUTUAL. Incoming one-sided requests are **not shown** to the recipient (no pressure).
- `theirContact` is non-null only when `MUTUAL` **and** they explicitly shared it with me.

### E11 `PUT .../connections/{participantId}` — body `{ "choice": "YES" }` → 200 `ConnectionItem`
Setting `NO` withdraws: also deletes my shared contact for that person. 409 `NOT_MATCHED` if caller has no group; 403 if either is not in this group; 400 if `participantId` = me.

### E12 `PUT .../connections/{participantId}/contact` — body `{ "contact": "insta @lena.w" }` → 200 `ConnectionItem`
`contact`: 1–100 chars free text, user-typed. 409 `NOT_MUTUAL` if not mutual.

### E13 `DELETE .../connections/{participantId}/contact` → 200 `ConnectionItem`

### A1 `POST /api/admin/seed` → 200 (demo only; disabled unless `lunch2.demo-mode=true`)

```json
{ "participants": [{ "id": "…", "displayName": "Mia (demo)", "campus": "WUERZBURG", "category": "DOMESTIC", "restoreUrl": "http://localhost:5173/#/back?t=…" }] }
```

Requires `X-Admin-Key`; no request body is required. Disabled demo mode returns 403 `FORBIDDEN`.
If participants, groups or friend pairs already exist, returns 409 `DEMO_NOT_EMPTY` without modifying data;
an explicit reset is required before seeding again. Successful seeding returns 18 fictional participants.
Restore links are returned once; only token hashes are persisted. Do not log these links.
Slots use the first and second configured event weekdays (Monday/Tuesday with the default start date).
The seed deliberately includes an active cross-campus friend pair to demonstrate the matching pre-check;
this bypasses the normal registration compatibility check for that fictional edge case only.

### A2 `POST /api/admin/match` → 200 `MatchRunResponse`

Optional query `fillWithDemo=true` enables explicit demo completion (403 `FORBIDDEN` unless
`lunch2.demo-mode=true`). Normal matching always combines every WAITING registration, including seed
and live registrations. Completion first runs that normal round, then adds only missing fictional seats
for remaining compatible waiting participants and runs the same engine again. It preserves existing
registrations, tokens and saved groups; it never resets data. Same campus/Mensa/slot, a shared language,
2 DOMESTIC + 2 INTERNATIONAL and active friend-pair constraints remain mandatory. Incompatible friend
pairs remain waiting. The response aggregates all groups created by the action and the final waiting list.
This opt-in action does not require seed data to have been loaded first and never runs on registration.

```json
{
  "groupsCreated": [{ "groupId": "…", "campus": "WUERZBURG", "slotId": "2026-11-03T12:15", "memberNames": ["Lena", "Sam", "Aiko", "Omar"], "score": 17 }],
  "waiting": [{ "participantId": "…", "displayName": "Jonas", "reasonCode": "NOT_ENOUGH_INTERNATIONAL" }]
}
```

### A3 `POST /api/admin/reset` → 204 No Content

Requires `X-Admin-Key`; no request body is required. Calls `DataAdmin.wipeAll()` to delete all
participants, groups (including cached cards), friend pairs, invites, connection choices and shared contacts.
Previously issued participant tokens no longer authenticate after their participant records are deleted.

Success returns **204 No Content with an empty response body**, not a JSON object. Clients must not
attempt to parse JSON for this response. Resetting an already-empty store also returns 204.
Missing or invalid admin keys return 401 `UNAUTHORIZED` using the standard error envelope and do not modify data.

### A4 `GET /api/admin/stats` → 200 (optional)

Requires `X-Admin-Key`. Returns aggregate counts only, without names, IDs, tokens or contacts.
`registrations` and `byCampus` count current participant records and always include all enum keys, even at zero.
`matched`/`waiting` count current participant statuses. `matchRate` is matched/registrations, rounded to two
decimal places (0 for an empty store). `groups` counts saved groups; `friendPairs` counts ACTIVE pairs.
`mutualConnections` counts each unordered pair once per group when both current members chose YES;
sharing contact details is not required. Unlinked friend pairs, deleted participants and one-sided/NO
connection choices do not contribute to the respective counts.

```json
{ "registrations": { "DOMESTIC": 9, "INTERNATIONAL": 8 }, "byCampus": { "WUERZBURG": 11, "SCHWEINFURT": 6 },
  "matched": 12, "waiting": 5, "matchRate": 0.71, "groups": 3, "friendPairs": 3, "mutualConnections": 2 }
```

---

## 4. Backend-internal shared interfaces (Java)

Package root: `de.thws.lunch2`. Owner in brackets. Other developers **use** these, never edit them.

### 4.1 Domain (`de.thws.lunch2.domain`) [Dev 1] — plain mutable classes or records, Jackson-serializable

```java
Participant { String id; String tokenHash; String displayName; Category category; Campus campus;
              List<String> languages; List<String> interests; List<String> slotIds; String studyField /*nullable*/;
              MatchStatus matchStatus; String groupId /*nullable*/; ReasonCode waitingReason /*nullable*/;
              String friendPairId /*nullable*/; Instant createdAt; Instant consentAt; }
FriendPair  { String id; String inviterId; String inviteeId; PairStatus status; ReasonCode issue /*nullable*/; }
Invite      { String code; String inviterId; Instant createdAt; String usedById /*nullable*/; }
LunchGroup  { String id; Campus campus; String slotId; String location; List<String> memberIds /*size 4, sorted*/;
              List<String> sharedInterests; List<String> commonLanguages; List<String> whyThisGroup; int score;
              Instant createdAt; CardSet cards /*nullable until first GET cards*/; }
CardSet     { String language; CardSource source; List<Card> cards; }   Card { String id; String text; String topic; }
ConnectionChoice { String groupId; String fromId; String toId; ConnectChoice choice; String contact /*nullable*/; }
```

### 4.2 Repositories (`de.thws.lunch2.persistence`) [Dev 1]

```java
interface ParticipantRepository { Optional<Participant> findById(String id); Optional<Participant> findByTokenHash(String h);
                                  List<Participant> findAll(); void save(Participant p); void delete(String id); }
interface GroupRepository       { Optional<LunchGroup> findById(String id); List<LunchGroup> findAll(); void save(LunchGroup g); }
interface FriendPairRepository  { Optional<FriendPair> findById(String id); void save(FriendPair p); void delete(String id); List<FriendPair> findAll(); }
interface InviteRepository      { Optional<Invite> findByCode(String c); Optional<Invite> findUnusedByInviter(String id); void save(Invite i); void deleteByInviter(String id); }
interface ConnectionRepository  { List<ConnectionChoice> findByGroup(String groupId); Optional<ConnectionChoice> find(String groupId, String fromId, String toId);
                                  void save(ConnectionChoice c); void deleteByParticipant(String participantId); }
interface DataAdmin             { void wipeAll(); }
```
MVP implementation: one `@ApplicationScoped JsonFileStore` holding `ConcurrentHashMap`s, `synchronized` mutations, full snapshot written to `lunch2.data-file` after every mutation, loaded on startup. Single instance only (documented). DynamoDB = future implementation of the same interfaces.

### 4.3 Auth [Dev 1]

```java
@RequestScoped class CurrentParticipant { String id(); Participant get(); }  // populated by AuthFilter for @Authenticated resources
@NameBinding @interface Authenticated {}                                   // annotate resource class/method
@NameBinding @interface AdminOnly {}                                       // checks X-Admin-Key
class TokenService { String newToken(); String hash(String token); }
class ApiException extends RuntimeException { ApiException(int status, String code, String message); }  // mapped to error envelope
```

### 4.4 Matching (`de.thws.lunch2.matching`) [Dev 2] — **pure Java, no Quarkus/CDI, no repository access**

```java
record MatchCandidate(String id, Category category, Campus campus, Set<String> languages, Set<String> interests,
                      Set<String> slotIds, String studyField, String friendId /*null unless pair ACTIVE*/, Instant registeredAt) {}
record MatchInput(List<MatchCandidate> candidates, List<String> slotIdsChronological, Set<Set<String>> previouslyMetPairs) {}
record ProposedGroup(Campus campus, String slotId, List<String> memberIds, List<String> sharedInterests,
                     List<String> commonLanguages, List<String> whyThisGroup, int score) {}
record MatchOutput(List<ProposedGroup> groups, Map<String, ReasonCode> waiting) {}
final class MatchingEngine { MatchOutput match(MatchInput in); }
final class GroupValidator { static List<String> violations(ProposedGroup g, Map<String, MatchCandidate> byId); } // empty = valid
```
(`whyThisGroup` uses display-name-free phrasing in the engine, e.g. `"FRIENDS_TOGETHER"`; `MatchService` renders names.)

### 4.5 Conversation cards (`de.thws.lunch2.cards`) [Dev 2]

```java
interface ConversationCardGenerator { List<String> generate(List<String> interestIds, String languageCode) throws Exception; }
class GeminiCardGenerator implements ConversationCardGenerator   // only if lunch2.ai.api-key set
class FallbackCards { static List<Card> pick(String groupId, List<String> interestIds, String lang); } // deterministic
```
The generator accepts catalog interest IDs internally; the Gemini payload contains **only** their labels
and a language code as group-specific data — no names, participant/group IDs, campus or times.
Gemini uses `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`,
the `x-goog-api-key` header, and JDK HttpClient. No new SDK dependency is required.
Requests have an overall timeout capped at 8 seconds. Missing keys, errors, incomplete output or invalid questions
use the deterministic fallback and are cached. Accept 3–6 distinct nonblank strings of at most 140 characters;
truncate six to five or supplement three/four with fallback questions to preserve E9's five-card response.
`source: AI` means the set contains validated generated questions, possibly supplemented with fallback questions.

---

## 5. Configuration (`backend/src/main/resources/application.properties`) [Dev 1 creates ALL keys in HACK-101]

| Key | Env var | Default | Used by |
|---|---|---|---|
| `lunch2.data-file` | `LUNCH2_DATA_FILE` | `./data/lunch2.json` | Dev 1 |
| `lunch2.admin-key` | `LUNCH2_ADMIN_KEY` | `dev-admin-key` | Dev 1 filter / Dev 2 admin |
| `lunch2.demo-mode` | `LUNCH2_DEMO_MODE` | `true` | Dev 2 seed |
| `lunch2.public-base-url` | `LUNCH2_PUBLIC_BASE_URL` | `http://localhost:5173` | invite/restore URLs |
| `lunch2.event-start-date` | `LUNCH2_EVENT_START_DATE` | `2026-11-02` | slots |
| `lunch2.slot-times` | – | `11:30,12:15,13:00` | slots |
| `lunch2.location.wuerzburg` / `.schweinfurt` | – | see §2 | groups |
| `lunch2.ai.api-key` | `GEMINI_API_KEY` | *(empty → fallback only)* | Dev 2 |
| `lunch2.ai.model` | `LUNCH2_AI_MODEL` | `gemini-3.8-flash` | Dev 2 |
| `lunch2.ai.timeout-ms` | – | `8000` | Dev 2 |

Frontend env (`frontend/.env.development`): `VITE_USE_MOCKS=true|false` (default `true` until CP2).

---

## 6. Frontend routes (HashRouter — no server-side SPA fallback needed) [Dev 3 creates all route files in HACK-301]

| Route | File (owner) | Purpose |
|---|---|---|
| `#/` | `pages/LandingPage.tsx` (Dev 3) | QR target, pitch, "Join lunch" |
| `#/register` | `pages/RegisterPage.tsx` (Dev 3) | 60-second form |
| `#/join/:code` | `pages/JoinPage.tsx` (Dev 3) | Invite landing → register with code |
| `#/back?t=…` | `pages/RestorePage.tsx` (Dev 3) | Store token from fragment, `history.replaceState`, go to `/me` |
| `#/me` | `pages/MePage.tsx` (Dev 3) | Status, invite, unlink, delete data |
| `#/group/:groupId` | `pages/GroupPage.tsx` (Dev 3) | Reveal + cards |
| `#/group/:groupId/connect` | `pages/ConnectPage.tsx` (**Dev 1**) | Mutual connect |
| `#/group/:groupId/play` | `pages/PlayPage.tsx` (Dev 3, HACK-111 coordinated scope) | Member-only prototype games hub |
| `#/group/:groupId/play/:gameId` | `pages/PlayPage.tsx` (Dev 3, HACK-111 coordinated scope) | Local game; initially `icebreakers` only |
| `#/privacy` | `pages/PrivacyPage.tsx` (Dev 3) | Privacy notice |
| `#/admin` | `pages/AdminPage.tsx` (Dev 2, optional) | Seed / match / reset buttons |

`frontend/src/api/endpoints.ts` (Dev 3, HACK-302) exports **one typed function per endpoint above, including E10–E13 and A1–A4**, with mock implementations in `api/mocks.ts` built from the sample payloads in this file. Token storage: `localStorage['lunch2.token']` via `api/session.ts`.

## 7. Mensa registration extension (user-approved implementation, 2026-10-29)

This additive extension is authorized by the approved registration/theme plan. It supersedes
§2's fixed slot generation for new Mensa-aware registrations. Existing fields and endpoints remain.

- `MetaResponse.mensas?: Mensa[]` is served by the backend. `Mensa` = `{id, campus, name,
  address, defaultForCampus, hours, sourceUrl, verifiedOn, slotIds}`. `hours` contains
  `{dayOfWeek, opens, lunchStart, foodUntil, closes}` (ISO weekday 1–7, local HH:mm).
- Source catalog: `backend/src/main/resources/catalog/mensas.json`. These are static,
  semester-time weekly hours verified against Studierendenwerk on 2026-10-29, not live status.
  Holiday/closure overrides are not implemented. Hubland uses the regular 11:00 opening
  in the official Würzburg listing; its detail page also contains historical seasonal notices.
- Generate five weekdays from `lunch2.event-start-date`, skipping weekends. Each Mensa's slots
  start at `lunchStart`, advance 45 minutes, start strictly before `foodUntil`, and finish by
  `closes`. Global `slots` is the deduplicated union plus the original configured slot times;
  each Mensa's `slotIds` is its allowed subset. Existing Slot wire shape and IDs are unchanged.
- `RegistrationRequest.mensaId?: string|null` and `MeResponse.mensaId?: string|null`:
  new UI requires a selection; missing/null on legacy requests resolves to Röntgenring
  for WUERZBURG and THWS Campus for SCHWEINFURT. Old JSON participants resolve identically.
  Explicit selections require correct campus and all slots in the Mensa's allowed subset.
  Legacy requests may also use original configured slots within that Mensa's service hours.
  Invalid Mensa/campus → 400 VALIDATION_FAILED; invalid slot → 400 UNKNOWN_SLOT.
- The existing 1–15 selected-slot limit remains. Participant persistence adds nullable
  `mensaId`; old Java constructors remain supported. Saved group locations are unchanged.
- MensaService extends the original CatalogService/SlotService without replacing their catalog/legacy APIs.
- Match per Mensa, preserving the pure engine's existing API, 2+2, language, campus and
  friend constraints. Never split friend pairs across Mensas. Add reason code
  `FRIEND_DIFFERENT_MENSA` ("You and your friend chose different Mensas."). This precedes
  slot/language incompatibility after the existing different-campus check.
- GroupResponse wire shape is unchanged: `location` contains Mensa name and address.
  No participant-level category, languages or Mensa preferences are exposed in member objects.
- Current location config keys remain accepted for legacy compatibility; new groups use
  the selected catalog location. `lunch2.slot-times` remains the legacy-client slot schedule.

Sources: [Würzburg listing](https://www.swerk-wue.de/wuerzburg/essen-trinken/mensen-speiseplaene),
[Studentenhaus (Friday exception)](https://www.swerk-wue.de/wuerzburg/essen-trinken/mensen-speiseplaene/mensa-am-studentenhaus-wuerzburg),
[Schweinfurt service hours](https://www.swerk-wue.de/schweinfurt/essen-trinken/mensen-speiseplaene/mensa-thws-campus-schweinfurt/menu).

## 8. Play Together prototype — user-approved HACK-111 scope (2026-10-29)

- Access requires a valid participant token and membership of the selected group.
  Existing E8/E9 enforce this; matching is enough. There is no attendance/time gate.
- Routes are `#/group/:groupId/play` and `#/group/:groupId/play/icebreakers`.
  The `would-you-rather` and `common-ground` catalog entries are coming soon; direct
  links to them or unknown games show an unavailable message, without fetching cards.
- No new endpoint, config key, enum, DTO field, provider payload or persisted schema.
  E9 remains exactly five cards, cached per group; AI failures still return fallback.
- The English game deck is the deduplicated union of E9 cards and the ten supplied
  classic questions. Other languages retain their E9 deck. Both mocks and backend
  fallback resources share the source JSON. The UI uses `AI + classic questions`
  for an AI-sourced combined deck and `Classic` for a fallback deck.
- Previous/next/skip/shuffle/finish/replay are local display actions. They never send
  answers, advance other phones, modify the cached server set or regenerate content.
- Additional games with backend state are future contract work, not part of this prototype.
