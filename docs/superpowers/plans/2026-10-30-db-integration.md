# DB integration Implementation Plan

> **For agentic workers:** Use the supplied server/web/vehicle division and verify each contract boundary. Steps use checkbox syntax for tracking.

**Goal:** Connect both existing apps to the same durable visit, proposals, completion and ratings using the supplied majung-db repository.
**Architecture:** Vendor canonical SQLite schema and example facilities at upstream commit 67ad83e1cef5a3c706920a9a3adc7c425007f30c. Fastify owns transactions, version checks and capability tokens; web and vehicle consume the shared API and receive live snapshots.
**Tech Stack:** Existing Node 22.13+, node:sqlite, Fastify, WebSocket, React, Kotlin Compose.
**Spec:** User's 2026-10-30 request and contracts/openapi.yaml. Implementation choices are agent decisions under permission to adjust DB configuration.

## Global Constraints
- Preserve existing databases and user demo artifacts; never copy secrets to source or memory.
- The upstream facility coordinates are examples, not verified real facilities.
- Real Pleos navigation/hardware remains dependent on unavailable SDK/hardware.
- No new service provider or dependency required.

## Review Focus
- Separate passenger/driver authority; capability tokens required for all visit reads/writes.
- Stale edits return 409 and repeated request IDs do not repeat mutations.
- Other-facility/forbidden access points cannot become confirmed places.
- Restart preserves visits, completion, ratings and history.
- Losing the socket must not leave clients permanently stale.

### Task 1: Server and canonical database
Files: apps/server/src/db, modules/visit, realtime, packages/shared/src/index.ts, contracts/openapi.yaml, apps/server/test/visit.test.ts.
- [x] Write/run failing API tests for two-role lifecycle, conflicts, auth, persistence.
- [x] Vendor upstream SQL, initialize safely, implement token snapshots and transactional writes.
- [x] Match schema enums, stage events, candidates, proposal uniqueness and destination history.
- [x] Run server tests and shared/server typecheck. Seven tests pass, including socket delivery, existing canonical DB extension, expiry, direct parking/dropoff changes and restart persistence.

### Task 2: Web integration (independent owner)
Files: apps/web/src/{api,realtime,data/store,pages,main.tsx}.
- [x] Replace implicit simulation with token API state and live/poll synchronization.
- [x] Send ready/proposal/respond/cancel actions, explicit loading/error states and completed redirect.
- [x] Remove implicit demo state; invalid /v/demo links show the normal missing-visit error.
- [x] Build and browser verify passenger flow.

### Task 3: Vehicle integration (independent owner)
Files: apps/vehicle/.../{data/remote,ui,domain}.
- [x] Load actual facilities, create visit, display passenger-token QR, synchronize same visit.
- [x] Wire driver proposal/respond/completion/rating/history and preserve before/after place names.
- [x] Build/test Android app and verify errors do not destroy pending work.

### Task 4: Full verification and delivery (root)
- [x] Run npm typecheck/build, server tests, Gradle tests/assembleDebug.
- [x] Verify web + vehicle against one server, database restart and persisted history/rating.
- [x] Document execution, upstream provenance, data/hardware limitations and update project memory.


## Verification evidence — 2026-10-30

- Implementation delegated to GPT-6.1 Sol for server, web and vehicle, with a separate Sol reviewer.
- Upstream SQL scenarios execute successfully: A version 6; B versions 6/3; C versions 5/3. Three vendored SQL files compare byte-for-byte with the pinned original.
- `npm run typecheck`, `npm run build`, server tests 7/7 and web tests 7/7 pass. Android `assembleDebug testDebugUnitTest` passes 14/14 tests; final APK rebuilt after the one-line generic rating title change. No new dependencies.
- Actual Android emulator and a mobile browser shared one server-created visit: facility selection → QR → passenger READY → passenger north entrance proposal → driver acceptance → arrival → completion → rating 5 with a reason → both clients display the stored result. Restarted Android displays the recorded rating.
- Actual parking flow: outdoor → underground entrance direct change, app force-stop/restart restores active visit, B3 completion and rating 3 saved. Server stop/restart preserves both completed visits, scores and B3 through HTTP reads; SQLite integrity_check returns ok.
- Browser checks cover both proposal directions, rejection/cancellation, real 409 conflicts and explicit retry, WebSocket-disabled polling, refresh recovery, expired link/proposal, GPS permission denial, and no automatic completion after arrival. Browser GPS coordinates were injected for testing, not a physical GPS accuracy check.
- LAN HTTP browser verification caught unavailable crypto.randomUUID. The getRandomValues request-ID fix has a regression test and passes readiness/proposal/respond/cancel on an insecure LAN origin. Independent scoped re-review: addressed, no additional important findings.
- Other integration fixes: Android default ViewModel factory startup, existing canonical DB extension rows, non-pickup direct point changes, arrival return without duplicate phase mutation, latest proposal-result dismissal, all-mode ratings, and Leaflet transition cleanup.
- Test DB was isolated under /tmp. User demo artifacts and existing DB files were not changed. Tokens/credentials are omitted from this record.

## Delivery limits

Upstream facilities are example data. Physical phone/vehicle GPS, Pleos navigation and real AAOS restrictions remain unverified. HTTPS is required for ordinary phone GPS sharing. No external hosting, production deployment or remote repository push was performed. Legacy monorepo databases are preserved and rejected instead of silently migrated. Local execution and backup commands are in README.md.
