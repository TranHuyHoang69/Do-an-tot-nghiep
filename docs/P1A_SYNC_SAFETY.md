# P1-A sync safety

This document records the earlier SAFE phase. The approved composite-key implementation,
Room v3 migration, new verification results and rollout gates are documented in
[P1A_V3_MIGRATION.md](P1A_V3_MIGRATION.md). Statements below about migration awaiting
approval and Room remaining at v2 describe that earlier phase only.

Scope: local, backward-compatible sync hardening. No production Firebase writes,
rules deployment, database reset, schema/version change, or legacy ID rewrite.
The worktree already contained P0 implementation and unrelated IDE/Firebase files.

## SAFE changes

- New default categories use deterministic account-scoped local IDs. `stableId`
  remains the business/translation key. Seeding checks every stable ID, including
  archived defaults, and preserves every existing local/cloud ID. Login use cases
  no longer seed before the first successful server pull. Category INSERT uses
  ABORT, so a primary-key collision cannot replace another account's row.
- CloudPull no longer treats a pending default CREATE as a replaceable placeholder.
  Failed collections cannot prune local rows; pending CREATE/UPDATE/DELETE survive.
- Logout invalidates the in-process session and cancels immediate work before any
  credential-provider suspension. Drawer uses SignOutUseCase. UserRepository logout
  retains Room data and no longer cancels a newly logged-in account's work.
- SyncSessionGuard checks UID and session generation. Generation changes on explicit
  auth transitions, including A -> logout -> A. SyncManager rechecks after waiting
  for its mutex and between stages; CloudPull checks again inside its Room transaction.
- All five uploaders use PendingUploadRunner. Before dispatch, stale local snapshots
  are skipped. After await, acknowledgement/failure/delete compares the full entity
  snapshot inside a Room transaction. A same-millisecond payload/operation change
  cannot be acknowledged or deleted by an old upload. Failure retains the pending
  operation; cancellation is propagated without marking FAILED.
- SQL UID/timestamp/operation guards remain defense in depth. Failures across a
  collection propagate, including suppressed permanent errors. Upload diagnostics
  contain exception type and Firebase code, not financial fields or document IDs.
- Reminder lookup used by the receiver rejects other owners/deleted rows. Rescheduling
  rechecks the active UID before scheduling, including after suspended local updates.

Immediate scheduling retains APPEND_OR_REPLACE: KEEP alone loses an edit triggered
after a running worker's pending-list snapshot. The singleton SyncManager mutex
serializes immediate and periodic work. Queued requests carry no UID/data; each worker
resolves the authenticated account at execution. Explicit logout cancels the immediate
chain; periodic work remains and resolves the current account. Queue coalescing needs
a durable dirty-generation/follow-up design, not just changing this policy to KEEP.

## REQUIRES APPROVAL: legacy category collisions

The current categories primary key is only `localId`. If two existing cloud accounts
both contain legacy default UUIDs, a device cannot store both categories. Pull fails
closed on the owner collision; scoped IDs for NEW rows do not repair this case.
No automatic rename, overwrite, deduplication, or cloud migration is implemented.

Proposed non-destructive migration, subject to approval:

1. Export/test the current v2 schema and build migration fixtures containing multiple
   accounts, archived defaults, pending/failed/deleted operations and orphan references.
2. Change ONLY categories' key to `(userId, localId)` in a new Room database version.
   Create a replacement table with the same columns/defaults and copy every row,
   including all sync metadata, in a single migration transaction. Recreate indexes
   and verify row counts and contents before the table swap. Never discard pending rows.
3. Require UID in every category read, update, archive, merge and join. Identity for
   translation remains stableId; persisted identity remains `(userId, localId)`.
   Audit DeleteCategoryUseCase and all selectors/grouping for owner-qualified lookup.
4. Transactions.categoryLocalId, budgets.categoryLocalId and
   recurring_transactions.categoryLocalId keep their exact strings. Their userId
   identifies which category row is referenced. No current FK requires rebuilding
   these tables, but joins and validation must always include userId. Budget's
   `(userId, categoryLocalId, effectiveMonth, effectiveYear)` index stays unchanged.
5. Preserve `users/{uid}/categories/{legacyId}` and ALL existing transaction/budget/
   recurring documents. The cloud schema already partitions by UID. Pull can then
   restore the second account's colliding default without any cloud move or rewrite.
6. Verify pre/post migration counts, pending metadata and per-owner reference
   resolution, including A -> B -> A and remote legacy IDs on two devices. Missing
   historical rows must be reported, not guessed or silently reassigned.

Affected code/data: CategoryEntity, CategoryDao, AppDatabase, database DI migration
registration, CloudPullService, CategoryRepositoryImpl, category use cases/selectors,
transaction/budget/recurring category validation and joins. Other Room primary keys
and Firestore document IDs need not change for this targeted migration.

Rollback: migration failure rolls back its SQLite transaction. Before rollout, create
a consistent, access-controlled backup/export including WAL state and pending metadata.
After a successful upgrade, use a forward-fix build that understands the composite key;
do not install the old v2 app against the upgraded database or merge two owners into
one key. A controlled recovery must preserve writes made after the backup. Because
cloud documents stay unchanged, no cloud rollback is required. Do not auto-restore
a stale backup over current pending data.

Mixed old/new app versions can still seed different default IDs concurrently before
seeing each other's cloud data. Consolidating those records and their references is
also approval-gated; the present code does not infer a canonical replacement ID.

## DEFERRED / limits

- P1-B: concurrent offline edits, stale edits after remote deletion, cloud clock skew,
  server revisions/tombstones and conflict resolution. Snapshot comparison protects
  local acknowledgement, not ordering of writes from different devices.
- P1-C: simultaneous budget creation and recurring template/cursor concurrency.
  The deterministic occurrence-ID algorithm is unchanged.
- Full snapshot equality covers same-millisecond differences in stored fields. It
  cannot identify an ABA mutation that returns to an identical entity. A durable
  local revision/cloud version protocol needs a separately approved migration.
- A Firestore write already handed to the SDK may still commit after coroutine/auth
  cancellation. The code stops subsequent dispatch/acknowledgement, preserves pending,
  and keeps the original UID path. It cannot revoke a server-accepted write.
- APPEND_OR_REPLACE may accumulate work during many offline edits. Periodic work can
  wait on the mutex. Durable coalescing and queue-load tests remain P2.
- Partial CloudPull currently merges successful collections then throws; SyncManager's
  reminder reconciliation does not run on that exception path. An auth event or a
  later successful sync reconciles alarms. Dedicated partial-sync alarm recovery remains.
- Fresh-account defaults now depend on a successful server pull. On failure the app
  retains existing local categories but cannot seed a fresh set until retry succeeds.

## Verification

`RoomSyncSafetyTest` uses a fresh in-memory Room database, Robolectric and mocked
Firestore Tasks. It does not connect to production or validate deployed rules.
`SyncWorkerDelegateTest` covers execution/error policy with a fake orchestrator.
`DefaultCategoryIdsTest` covers deterministic new-ID generation. Build/test results
from the final unit-test run: `testDebugUnitTest` PASS, 32 tests, zero failures/errors
(19 RoomSyncSafetyTest, 9 SyncWorkerDelegateTest, 3 DefaultCategoryIdsTest, 1 existing
ExampleUnitTest). The Room tests also exercise all five repository upload methods
through failure/retry, and rollback when the account changes inside acknowledgement.
`assembleDebug` also passed (11 seconds). `git diff --check` passed. Existing Kotlin
compiler-option warnings remain; no compilation or test failures remain in these runs.
There were no devices attached to ADB. Two-phone behavior and production rules were
not tested in this session. Test dependencies use Robolectric 4.16.1 and Mockito 5.14.2;
see [Robolectric setup](https://robolectric.org/getting-started/) for the test runtime.

## P1-A file inventory

Paths below are relative to `app/src/main/java/com/example/moneymatev2/`, except
where stated. Pre-existing P0 and IDE/Firebase configuration changes are not counted
as newly implemented P1-A work.

| Files | P1-A change |
| --- | --- |
| data/remote/sync/SyncSessionGuard.kt (new) | UID/session generation guard and AuthSessionVersion |
| data/remote/sync/PendingUploadRunner.kt (new) | Full-snapshot transactional acknowledgement/failure/delete |
| data/remote/sync/SyncErrors.kt (new) | Permanent error classification and sanitized diagnostics |
| data/remote/sync/SyncWorkerDelegate.kt, SyncWorker.kt | Session validation, cancellation and error reporting |
| data/remote/sync/CloudPullService.kt | Preserve pending defaults; session/merge checks; sanitized failures |
| data/remote/sync/SyncTrigger.kt, core/di/SyncModule.kt | Immediate-work cancellation; preserve trailing sync requests |
| data/repository/TransactionRepositoryImpl.kt, CategoryRepositoryImpl.kt, BudgetRepositoryImpl.kt, ReminderRepositoryImpl.kt, RecurringTransactionRepositoryImpl.kt | Guard all upload paths; scoped new defaults; reminder ownership checks |
| data/local/dao/TransactionDao.kt, CategoryDao.kt, BudgetDao.kt, ReminderDao.kt, RecurringTransactionDao.kt | Guard failed state; ABORT category insert; pending status on delete/archive |
| data/repository/AuthRepositoryImpl.kt, UserRepositoryImpl.kt | Synchronous session invalidation and work cancellation; preserve local rows |
| domain/usecase/SyncManager.kt | Verify session across mutex and stage boundaries |
| domain/usecase/auth/SignInUseCase.kt, SignInWithGoogleUseCase.kt, SignUpUseCase.kt | Remove pre-pull default seeding |
| domain/usecase/reminder/RescheduleRemindersUseCase.kt | Check active account around scheduling |
| ui/components/DrawerViewModel.kt | Use shared SignOutUseCase |
| app/build.gradle.kts (repo-relative) | JVM Room test dependencies/configuration |
| app/src/test/.../RoomSyncSafetyTest.kt, SyncWorkerDelegateTest.kt, DefaultCategoryIdsTest.kt | Regression tests described above |
| docs/P1A_SYNC_SAFETY.md (repo-relative) | Migration proposal, verification and residual risks |

AuthSyncSessionManager and SignOutUseCase were traced but needed no additional P1-A
source edits. No database entity, database version, production rule, or migration
registration was changed in this phase.

Manual checks on two phones (same updated build):

1. Seed a fresh account on both devices; verify four defaults, stable IDs and no duplicate
   documents. Repeat login/seed. Separately test legacy cloud accounts; expect the
   documented collision limitation until migration is approved.
2. Offline A: create/edit a transaction. Sign out and log into B, then return to A.
   Verify A's pending rows and category mapping remain, with no B rows in A's UI.
3. Interrupt upload, edit the same row, reconnect. Confirm newer local data stays
   pending until uploaded and appears once on the other phone. Repeat with deletion.
4. Switch accounts during sync and rapidly logout/login to the same account. Verify
   subsequent uploads use only the active UID, and stale sessions do not acknowledge.
5. Check A's reminder alarms stop after switching to B; a queued A alarm must not
   display A's reminder. Re-login A and verify active reminders are scheduled again.
6. Create a row during another upload and verify a trailing immediate run sends it.
7. Repeat A -> cloud -> B and B -> cloud -> A for all five collections; then repeat
   offline/reconnect. Inspect sanitized SYNC logs for incomplete/permanent failures.
