# Lunch² – Product Spec

> What we build and how we know it works. Architecture: `ARCHITECTURE.md` · API: `API_CONTRACT.md` · Work: `TASKS.md`.

## Problem statement

International students at THWS struggle to build local friendships, while German students already have established circles and little reason to join an "integration programme". Lunch² removes that barrier: you come **with** a friend, and the two of you meet two new people over a normal Mensa lunch. It should feel like a fun, spontaneous social plan, not volunteering.

## A. Requirements Analysis

### A.1 Mandatory functional requirements
| ID | Feature | Must demonstrate |
|---|---|---|
| F1 | QR quick registration | ≤60 s form, no install, no password, secure random access token, return later |
| F2 | Bring a Friend | Share URL, friend registers via link, pair linked, kept together, explained when impossible |
| F3 | 2+2 matching | Groups of exactly 4, 2 domestic + 2 international, same campus, common slot, common language, no double assignment, friends together; unmatched users wait with a reason |
| F4 | Group reveal | Names, shared interests, date/time/location, only for members, enjoyable reveal |
| F5 | Play Together / icebreaker questions | Post-match games hub; AI questions plus classic English questions, shared language, skip/shuffle, fallback, cache, minimal data to LLM |
| F6 | Mutual connect | Reciprocal opt-in, one-sided reveals nothing, explicit contact sharing, withdraw/decline |

### A.2 Non-functional / challenge constraints
- **GDPR/DSGVO:** minimal fields (no email, phone, nationality, age, gender, photo); purpose limitation (lunch matching only); explicit consent checkbox; per-participant access control; delete-my-data endpoint; documented retention; fictional demo data; HTTPS (tunnel/AWS give TLS).
- **Low cost:** single process (Quarkus serves the built SPA), JSON-file persistence, no background jobs, lazy + cached LLM calls with a cheap model.
- **Low friction:** mobile-first, chips instead of text fields, one screen form.
- **Reliable matching:** deterministic, validator re-checks every group, automated tests on hard constraints.
- **Privacy/human interaction:** no rankings/public profiles; one-sided requests invisible to recipient; "no" never revealed.
- **A11y & i18n:** English/Deutsch; typed UI dictionaries in `frontend/src/i18n/{en,de}.ts`; backend sends IDs not labels; WCAG AA contrast, ≥44px touch targets, labelled inputs.

### A.3 Ambiguities & how we resolved them
| Ambiguity | Decision |
|---|---|
| 3 vs 4 developers | A1 |
| "Shared language" across group or pairwise? | A8 (all four) |
| What happens when an inviter is matched before the friend registers? | Pair becomes `INCOMPATIBLE / INVITER_ALREADY_MATCHED`; friend matched individually and told why. Pending invites do **not** block the inviter. |
| When is matching triggered? | Explicit admin action (`POST /api/admin/match`, button on `#/admin`). No scheduler → no background processing. Production: cron/day-before trigger. |
| "After a meetup" for Connect | MVP allows connect once matched (so it's demoable); production gate = after `startsAt`. |
| Which contact data? | None stored at registration. Contact is free text typed **per mutual connection** at share time. |
| Repeat-match avoidance | Pairs from previous groups get a score penalty (soft). |

### A.4 Implementation risks (top 5, details in §J)
1. Matching engine correctness/time (critical path). 2. DTO drift between frontend and backend. 3. Live demo network/QR. 4. Token/auth bug blocking all authenticated screens. 5. Polishing animation before E2E works.

---

## B. MVP Definition

### B.1 Essential (simplest functional level of all six features)
| Feature | Simplest functional level we build |
|---|---|
| F1 | One-page form with chips; token returned and stored in `localStorage`; "restore link" `#/back?t=…` (token in URL fragment → never sent to server logs) |
| F2 | `POST /api/me/invite` → link + QR + Web Share/copy; `#/join/:code` landing shows inviter name; pair compatibility check at registration |
| F3 | Greedy best-scoring 2D+2I enumeration per campus/slot + validator + reason codes |
| F4 | Card-flip reveal of 4 members (CSS only), why-this-group bullets, time/place |
| F5 | Games hub opens after matching. Conversation Shuffle combines the cached five-card API set with ten supplied classics for English groups; local skip/shuffle/replay, no recorded answers. Gemini keeps its 8 s timeout and fallback. |
| F6 | Per-member YES/NO toggle; mutual → contact text box; withdraw button |

### B.2 Explicitly excluded
Play Together is a local prototype: no levels/XP, leaderboard, attendance verification,
meeting-time gate, recorded answers, synchronized multiplayer state or game polling.
Only Conversation Shuffle is playable; other game catalog entries are future work.

Real-time chat, push/e-mail notifications, admin dashboard beyond 3 buttons, university SSO, DynamoDB, Lambda, multiple matching rounds per user, editing profile after registration (delete & re-register instead), attendance check-in (optional idea only), German translation (architecture-ready only), analytics beyond one stats endpoint.

### B.3 Primary demo scenario (≈4 min)
1. **Pitch hook (30 s):** "German students already have friends. So we don't ask them to come alone — bring one. Bring a Friend, meet two."
2. Admin page: **Seed** fictional data (Würzburg + Schweinfurt, with built-in edge cases). Seed leaves **two international students (Aiko, Omar)** waiting at Würzburg Tue 12:15 — the live demo supplies the two domestic students.
3. Presenter scans QR on a projected slide → registers as **Lena (DOMESTIC, Würzburg, de/en, Tue 12:15)** in ~45 s → taps **Bring a friend** → shows QR.
4. Teammate scans it on a second phone → "Lena invited you" → registers **Sam (DOMESTIC)**.
5. Admin: **Run matching** → result shows 1 new Würzburg group (Lena, Sam, Aiko, Omar) **plus** rejected cases: Schweinfurt "Jonas waits – NOT_ENOUGH_INTERNATIONAL", cross-campus friend pair "FRIEND_DIFFERENT_CAMPUS", Turkish-only speaker "NO_COMMON_LANGUAGE".
6. Lena's phone: **group reveal** animation → 4 names, "3 of you love Travel", Mensa table, time.
7. Open **Play together** → **Conversation Shuffle**. Answer aloud, skip a question or shuffle. The deck shows "AI + classic questions" or "Classic"; Would You Rather and Find Common Ground are visibly coming soon.
8. **Connect:** Lena → YES to Aiko (shows "Waiting for them" – Aiko's view reveals nothing; open Aiko's demo restore link to prove it). Lena ↔ Sam both YES → MUTUAL → Lena shares "insta @lena.w" → Sam sees it. Withdraw once to show control.
9. Close with **metrics slide** (`GET /api/admin/stats`): registration balance, match rate, mutual connections; GDPR one-pager.

---


## Privacy: production gaps
Prototype is **not** production-compliant. Before real use: replace bearer-token-in-localStorage with THWS SSO (Shibboleth/DFN-AAI) or e-mail magic link restricted to university addresses; rate limiting & abuse/blocking reports; privacy notice and records of processing (Art. 30) reviewed with the THWS data protection officer; DPIA screening; DPAs with AWS (EU region) and the LLM provider plus transfer assessment; automated retention deletion job; access logging policy without personal data; security review/pen-test; incident process; code of conduct for meetups.


## Success metrics
Registration balance D:I, registration → match conversion, attendance (future: one-tap "We met" check-in), repeat participation, mutual connections per group, share of registrations arriving via friend invites (proves the "bring a friend" thesis for German students).

## Animated landing and bilingual interface (HACK-309, 2026-10-29)

The centered landing cycles three paired headlines every four seconds and rotates its
four-person table once every 24 seconds. A pause control, reduced-motion preference
and hidden-tab suspension stop motion without moving the CTA.
All frontend screens, including Play Together, Connect and Admin, support English
and Deutsch. The saved UI preference takes priority over the browser's primary
language (German selects Deutsch; otherwise English). Changing language preserves
registration answers, the current step, loaded data and the session. Conversation
questions remain in the group's shared language; names, addresses and supplied
contact text are never translated. Existing matching and API behavior is unchanged.

## Buddy registration extension (2026-10-29)

From My lunch, waiting unpaired participants create an idempotent single-use invite,
share/copy its URL or show its QR. The join page previews inviter name, campus, Mensa
and availability. Buddy registration inherits campus/Mensa and asks the buddy to select
a nonempty subset of the inviter's slots; personal category, languages, interests,
nickname and consent remain their own answers. No times fit -> explicit solo registration.
Used/invalid invites and already matched inviters receive explanations and a solo
registration option. With a saved browser session, valid invites still show the plan
and offer "Join as a different person" alongside the existing My lunch link. Buddy
registration asks for a new nickname first, then the remaining personal answers.
The previous session stays active until successful registration; its private return
link is available to save before switching. Existing registrations cannot be linked
through this flow; joining as another person creates a distinct participant. Both profiles display buddy status and can unlink;
unlink does not change an existing group. Matching is still an admin action.
