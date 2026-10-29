# Lunch² — API & Data Contracts (FROZEN at 0:30)

> Single source of truth for frontend ↔ backend and module ↔ module interfaces.
> Changing anything here after 0:30 follows the **contract change procedure** in `docs/PLAN.md` §I.
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
| 409 | `ALREADY_PAIRED`, `INVITE_USED`, `NOT_MUTUAL`, `NOT_MATCHED` |
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

### A2 `POST /api/admin/match` → 200 `MatchRunResponse`

```json
{
  "groupsCreated": [{ "groupId": "…", "campus": "WUERZBURG", "slotId": "2026-11-03T12:15", "memberNames": ["Lena", "Sam", "Aiko", "Omar"], "score": 17 }],
  "waiting": [{ "participantId": "…", "displayName": "Jonas", "reasonCode": "NOT_ENOUGH_INTERNATIONAL" }]
}
```

### A4 `GET /api/admin/stats` → 200 (optional)

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
class ClaudeCardGenerator implements ConversationCardGenerator   // only if lunch2.ai.api-key set
class FallbackCards { static List<Card> pick(String groupId, List<String> interestIds, String lang); } // deterministic
```
AI payload contains **only** interest IDs and a language code — no names, IDs, campus or times.

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
| `lunch2.ai.api-key` | `ANTHROPIC_API_KEY` | *(empty → fallback only)* | Dev 2 |
| `lunch2.ai.model` | `LUNCH2_AI_MODEL` | `claude-haiku-5-5` | Dev 2 |
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
| `#/privacy` | `pages/PrivacyPage.tsx` (Dev 3) | Privacy notice |
| `#/admin` | `pages/AdminPage.tsx` (Dev 2, optional) | Seed / match / reset buttons |

`frontend/src/api/endpoints.ts` (Dev 3, HACK-302) exports **one typed function per endpoint above, including E10–E13 and A1–A4**, with mock implementations in `api/mocks.ts` built from the sample payloads in this file. Token storage: `localStorage['lunch2.token']` via `api/session.ts`.
