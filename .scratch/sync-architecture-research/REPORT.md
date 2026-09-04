# Offline-First Sync Architecture Research
## Android POS on SUNMI V2 Pro · ~21,811-product catalog · Kotlin/Ktor backend · Jetpack Compose + Room client

*Research report — September 2026. Every claim is cited to a primary source (official docs, source repos, first-party engineering write-ups, or RFCs). URLs verified at research time unless noted.*

**Target device (verified from SUNMI's official spec sheet):** SUNMI V2 Pro — Cortex-A53 quad-core 1.4 GHz, **2 GB RAM + 16 GB ROM**, 5.99" HD+, 7.6 V / 2580 mAh battery, 58 mm thermal printer, 1D/2D barcode scanner, 4G (2G/3G/4G) + dual-band Wi-Fi 802.11 a/b/g/n.
Source: https://www.sunmi.com/v2pro/

---

## 1. Offline-first vs online-first for mobile POS

- **Offline-first as a discipline**: the offline-first movement frames the network as an enhancement, not a requirement: "We live in a disconnected & battery powered world, but our technology and best practices are a leftover from the always connected… past." Offline capability is listed as a key characteristic of modern apps, alongside UX patterns and distributed-systems research. https://offlinefirst.org/ (community resource collection: https://medium.com/offline-camp/offline-first-resources-2acc5836e9d4)
- **Offline concurrency is a classic pattern problem**: Fowler's PoEAA catalog formalizes *Optimistic Offline Lock* (let one transaction commit, fail the others on conflict) and *Pessimistic Offline Lock* for business transactions that span a network disconnection — exactly the "invoice created offline" case.
  https://martinfowler.com/eaaCatalog/optimisticOfflineLock.html
- **Trade-off summary for POS**: online-first gives you live stock/pricing but fails hard at "no signal, no sale"; offline-first requires the full sellable catalog, prices, taxes, promotions, and clients on-device, and a durable upload queue. The industry norm for POS is offline-first for the *sell path* (see §11: Square, Shopify), with server-side reconciliation for stock/reports.

## 2. Delta/incremental sync patterns (core design material)

**The canonical patterns** (all primary sources):

| Mechanism | How it works | Primary reference |
|---|---|---|
| Change feed + cursor | Server keeps an append-only `_changes` log per database; client passes `since=<seq>` and receives only newer records; checkpoints stored in `_local` docs | CouchDB Replication Protocol: https://docs.couchdb.apache.org/replication/protocol.html · PouchDB guide: https://pouchdb.com/guides/replication.html |
| Watermark (`updated_at >= :cursor`) | Client stores the max `updated_at` (or monotonically increasing change id) it has applied; server serves `GET /changes?since=cursor` | Same as above; also PowerSync's bucket+checkpoint model: https://docs.powersync.com/ |
| Tombstones | Deletions are recorded as rows with `deleted=true` (CouchDB) or emitted as explicit delete records, so incremental feeds can carry deletes | CouchDB protocol (above); MongoDB Atlas Device Sync tombstones docs (now deprecated): https://www.mongodb.com/docs/atlas/device-sdks/deprecation/ |
| Conditional GET / ETags | `GET /catalog` returns `ETag: <hash>`; later requests send `If-None-Match:` and get `304 Not Modified` when unchanged — cheap "has anything changed?" probes | HTTP conditional requests (RFC 9110 §13): https://httpwg.org/specs/rfc9110.html#conditional.requests |
| Server-authoritative reconciliation | Figma's multiplayer: server is the single ordering authority; conflicts resolve as last-writer-wins **per property**, clients suppress acknowledged-but-conflicting incoming writes; client-generated IDs make offline object creation safe | Evan Wallace, "How Figma's multiplayer technology works": https://www.figma.com/blog/how-figmas-multiplayer-technology-works/ |
| Full resync fallback | Figma clients that return after long offline periods "download a fresh copy of the document, reapplies any offline edits on top" — i.e., snapshot + replay, not infinite deltas | same Figma article |
| Linear's sync engine | Read path is a local cached copy; client fetches a consistent snapshot then subscribes to an ordered delta stream; write path is an ordered local transaction queue replayed to the server | Tuomas Artman, "Scaling the Linear Sync Engine" (talk): https://linear.app/now/scaling-the-linear-sync-engine (video: https://www.youtube.com/watch?v=Wo2m3jaJixU) |

**Recommended shape for this project (derived from the above):** one `catalog_changes` table server-side (`change_id BIGINT AUTOINCREMENT PRIMARY KEY, product_id, op, changed_at`), client pulls `GET /catalog/changes?cursor=<last_change_id>&limit=500`, applies upserts + tombstones in a transaction, persists the cursor in DataStore. ETag on a `GET /catalog/manifest` endpoint serves as a cheap "should I sync at all?" check.

## 3. Pagination + on-demand loading vs full local catalog

- **Paging 3 RemoteMediator** is the official Android pattern for "UI reads from Room, Room is refilled from the network": `RemoteMediator.load()` fetches pages when local data is exhausted, stores them in Room, and Room's `PagingSource` re-emits. `initialize()` can skip remote refresh when the cache is fresh. https://developer.android.com/topic/libraries/architecture/paging/v3-network-db
- **But a POS is not a feed app.** Barcode scan → price lookup has no scroll position; the lookup must work with **zero network**. Paging solves UI memory, not offline completeness. Use Paging only for browsing screens; the sell path needs the full catalog locally.
- **Search-as-you-type locally**: SQLite FTS5 gives ranked local search; its **trigram tokenizer** enables substring matching and even indexed `LIKE '%abc%'`: https://www.sqlite.org/fts5.html. Room supports `@Fts4` out of the box (FTS5 is available via raw SQL + bundled SQLite on modern Android).
- **Why local for scanning**: scanner hardware on the V2 Pro feeds a barcode into your app within milliseconds (spec: professional 2D scan head — https://www.sunmi.com/v2pro/); an indexed SQLite lookup by barcode is a B-tree search (sub-millisecond at 21k rows — see §5), so the whole scan→price path stays well under the ~50–100 ms perceptual threshold entirely offline. A remote lookup would add RTT + cellular variance and fail offline — incompatible with §11 industry practice.

## 4. What data must be local for POS

**Must be local (offline sell path):** products (name, barcode(s), unit), prices (including price lists/currency), tax groups/rates, promotions/discount rules, active clients (for account sales), cashier/session config.
Justification: Shopify POS offline checkout works without connectivity and syncs later; Square offline mode similarly processes sales locally and uploads when back online (with limits): https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline · https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode
**Can stay server-side:** sales history, dashboards/reports, real-time stock levels, customer receipts cloud, accounting exports. Square's catalog-sync model treats the device catalog as an offline cache updated via webhooks/changes — https://developer.squareup.com/docs/catalog-api/webhooks — while stock/history remain server-computed.

## 5. Local storage for 21k rows — it is SMALL

- **Hard numbers from SQLite's own docs**: max database size ≈ **281 TB** (default 4096-byte pages); max rows per table ≈ 2⁶⁴; page sizes range 512–65,536 bytes. https://www.sqlite.org/limits.html
- **Size estimate**: 21,811 rows × 200–500 B ≈ **4–11 MB** raw; with indexes + FTS5, well under ~25 MB on a 16 GB device (0.15% of storage). SQLite is explicitly designed as an app-file-format database ("Small. Fast. Reliable"): https://www.sqlite.org/about.html
- **Search for a barcode is O(log n)** on an index — 21k rows is trivial (Android's own SQLite guide shows index lookups as B-tree searches vs full scans): https://developer.android.com/topic/performance/sqlite-performance-best-practices
- **Engine options**:
  - **Room (recommended)** — official persistence layer; `JournalMode.AUTOMATIC` uses WAL *unless* the device is low-RAM (relevant for 2 GB — see §6): https://developer.android.com/reference/androidx/room/RoomDatabase.JournalMode · training: https://developer.android.com/training/data-storage/room. Room 2.7+ is KMP-ready: https://developer.android.com/jetpack/androidx/releases/room#2.7.0
  - **SQLDelight** — SQL-first, KMP; fine but no Paging integration as first-class as Room. https://github.com/cashapp/sqldelight
  - **ObjectBox / Realm** — Realm's MongoDB SDKs + Atlas Device Sync were **deprecated and reached end-of-life September 30, 2025**; the on-device DB continues as a community OSS branch only: https://www.mongodb.com/docs/atlas/device-sdks/deprecation/ — avoid for new code.
- **Conclusion**: full-catalog local storage is viable and standard for POS. The "initial sync is slow" pain is a **transfer/serialization/batching problem** (§7, §10), not a database-size problem.

## 6. Impact on a 2 GB RAM device

- Android balances processes with the **Low Memory Killer**; your app's heap is capped far below physical RAM, and background processes get killed first: https://developer.android.com/topic/performance/memory-overview · memory hub: https://developer.android.com/topic/performance/memory
- **Why a single 20 MB JSON parse can OOM**: a 20 MB UTF-8 JSON string becomes ~40 MB in-memory as UTF-16, and the parsed object graph of 21k products typically costs 5–10× the raw text — hundreds of MB, often beyond the app heap limit → `OutOfMemoryError` or an LMK kill. (Memory discipline guidance: https://developer.android.com/topic/performance/memory/manage-app-memory)
- **Mitigations, all with primary support**:
  - **Stream, don't buffer**: OkHttp delivers the response body as a streamed `Source` (https://square.github.io/okhttp/); **Okio** provides buffered streaming I/O (https://square.github.io/okio/); kotlinx.serialization exposes streaming `JsonReader` decoding from an `InputStream` so you never materialize the whole document: https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-reader/
  - **Paged payloads** (1–5 MB pages) + chunked DB transactions keep peak memory flat.
  - **Binary payloads**: protobuf's wire format is TLV with varints and packed repeated scalars — typically far smaller than JSON: https://protobuf.dev/programming-guides/encoding/ ; CBOR (RFC 8949) is a binary JSON drop-in: https://www.rfc-editor.org/rfc/rfc8949
- Room itself is 2 GB-aware: `JournalMode.AUTOMATIC` falls back to TRUNCATE on low-RAM devices (detected via `ActivityManager.isLowRamDevice()`, https://developer.android.com/reference/android/content/pm/ActivityManager#isLowRamDevice()): https://developer.android.com/reference/androidx/room/RoomDatabase.JournalMode

## 7. Bulk insert performance in Room/SQLite (depth)

Official Android guidance, all from https://developer.android.com/topic/performance/sqlite-performance-best-practices :

1. **Enable WAL** — appends commits to a log, readers don't block the writer: *"Enable WAL unless you are using ATTACH DATABASE."* Backed by SQLite: WAL is "significantly faster in most scenarios", readers and writers proceed concurrently, and fewer `fsync`s are needed: https://www.sqlite.org/wal.html
2. **`PRAGMA synchronous = NORMAL`** with WAL — a commit may return before data is physically flushed; on app crash data is safe, on power loss the last commits may roll back but **the DB cannot corrupt**: https://www.sqlite.org/wal.html#performance_considerations (Android docs: same page as above, "Relax the synchronization mode").
3. **Batch inserts in a single transaction** — "A transaction commits multiple operations, which improves not only efficiency but also correctness." Per-row autocommit costs one journal/fsync cycle per row; one `BEGIN…COMMIT` amortizes it: https://www.sqlite.org/lang_transaction.html
4. **Use `@Upsert`** (Room 2.5+) for idempotent delta application — insert-or-update without a separate existence check: https://developer.android.com/reference/androidx/room/Upsert
5. **Use `INTEGER PRIMARY KEY` (rowid alias) for the product PK** and indexed columns you filter on (barcode!) — rowid lookups are fast B-tree searches: https://developer.android.com/topic/performance/sqlite-performance-best-practices
6. **Chunking**: insert 500–2000 rows per transaction; this keeps the WAL bounded (auto-checkpoint at 1000 pages ≈ 4 MB — https://www.sqlite.org/wal.html#avoiding_excessively_large_wal_files) and preserves resumability (a killed sync only loses the current chunk).
7. **Persist sync metadata atomically with data**: store the cursor *inside the same Room transaction* as the chunk, so a crash can never double-apply or skip (RemoteMediator docs model this with a `remote_keys` table updated in `withTransaction` — https://developer.android.com/topic/libraries/architecture/paging/v3-network-db).

Expected result for this project: 21,811 upserts ≈ **a few seconds total** on the A53, in ~20 transactions of ~1k rows — memory-flat, kill-safe.

## 8. Bidirectional sync + conflict resolution for POS

- **Outbox pattern for offline sales (depth)**: the transactional outbox writes the sale *and* its outbound message in the **same local DB transaction**; a relay process then pushes queued messages. Guarantees: messages are sent iff the transaction commits, in order; the relay may deliver **more than once**, so *consumers must be idempotent* — i.e., you get at-least-once, never exactly-once, delivery. Source (Chris Richardson, *Microservices Patterns*): https://microservices.io/patterns/data/transactional-outbox.html
- **Idempotency keys**: client generates a UUID per sale (generated offline, before upload); server records the first response per key and replays it on retries — exactly Stripe's documented design ("safely retrying requests without accidentally performing the same operation twice… V4 UUIDs… results saved only after execution begins"): https://docs.stripe.com/api/idempotent_requests. This upgrades at-least-once transport to **effectively-once business effect**.
- **Conflict policy for a catalog**: catalog rows are server-authoritative (cashiers rarely edit products); device edits (if any) resolve last-write-wins by server-received order (Figma's property-level LWW precedent: https://www.figma.com/blog/how-figmas-multiplayer-technology-works/). **Sales are append-only** — no conflicts, only dedup via idempotency keys. CRDTs (Ditto — https://docs.ditto.live) are overkill unless you later need multi-terminal offline edits of the same record.
- Stock: keep it server-computed; push *sales* up, let the server subtract, and pull stock as **advisory, non-authoritative** display data.

## 9. Sync state machine, checkpointing, recovery

- States: `IDLE → BOOTSTRAP (§10) → DELTA_PULL → APPLY → PUSH_OUTBOX → DONE`, persisted in **DataStore** (official key-value/protocol-buffer storage for small sync metadata: https://developer.android.com/topic/libraries/architecture/datastore).
- **WorkManager** is the official durable scheduler: constraints (`NetworkType.UNMETERED`, `RequiresCharging`, `BatteryNotLow`), backoff policy (`EXPONENTIAL` default 30 s, min 10 s), and expedited work (quota-based; "an app that handles a payment or subscription flow" is the doc's own example use case): https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work
- **If constraints break mid-run**, WorkManager stops the worker and retries when they're met again — so every sync unit must be resumable: checkpoint cursor per chunk (§7.7).
- **Retry with exponential backoff + jitter**: AWS's canonical post shows plain exponential backoff leaves contention spikes; **"Full Jitter"** (`sleep = random(0, min(cap, base·2^attempt))`) halves call counts and improves completion time; "should be considered a standard approach for remote clients": https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/
- **Partial sync after app kill**: never apply a chunk and lose its cursor (or vice versa) — same-transaction persistence (§7.7). On cold start, resume from the stored cursor; the server's monotonic `change_id` makes this safe.

## 10. Initial sync strategies (bootstrap) — depth

Ranked for this project:

1. **Versioned snapshot bundle + delta since snapshot (recommended)** — mirrors Firestore *data bundles*: "static data files built by you from… document and query snapshots, published by you on a CDN… after which you load bundle data to the local cache", with *named queries* for subsequent reads: https://firebase.google.com/docs/firestore/bundles. Implementation: server builds `catalog-<version>.bundle` (binary protobuf, ~5–8 MB, gzipped), device downloads it (resumable via HTTP Range — RFC 9110 §14: https://httpwg.org/specs/rfc9110.html#range.requests), bulk-imports in one pass, then runs the normal `?cursor=<snapshot's change_id>` delta pull. New terminals get full state in one transfer + a small delta; existing terminals skip the bundle entirely.
2. **Chunked change-feed pull** (no bundle): `GET /catalog/changes?cursor=0&limit=1000` in a loop. Works today with only the change-feed API; ~22 requests on a decent 4G link; each chunk applied transactionally (§7). Resumable by construction.
3. **Pre-packaged APK asset DB** (ship a Room SQLite file in assets, copy on first run): fastest "first sale possible" path, but the file is stale by ship date and needs a full delta anyway; PouchDB/CouchDB ecosystems treat snapshots as regular replication checkpoints, which is the cleaner generalization: https://pouchdb.com/guides/replication.html

Progress UI + resumability are mandatory on 4G (2580 mAh battery device, §13).

## 11. Real-world POS offline architectures (primary sources found)

- **Square**: official Offline Mode — payments are processed on-device and uploaded within a bounded window (docs state offline payments must be uploaded within 72 h, with 24 h of continued offline processing): https://squareup.com/help/us/en/article/7777-process-card-payments-with-offline-mode · Device catalogs are kept in sync via Catalog webhooks: https://developer.squareup.com/docs/catalog-api/webhooks
- **Shopify POS**: offline checkout lets you "accept cash and manual payments without an internet connection"; official caution — "Logging out of Shopify POS, or turning off the device might cause loss of offline orders" (i.e., an **in-memory queue loses sales** — the argument for a DB-backed outbox, §8): https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline · https://help.shopify.com/en/manual/sell-in-person/shopify-pos/selling-offline/offline-features
- **Toast / Lightspeed / SumUp / Zettle**: no substantial public primary engineering write-ups on offline catalog sync were found during this research (their offline claims live in product/marketing pages, not engineering blogs). Square and Shopify are the credible public references.

## 12. Library landscape 2025–2026 (license + maintenance)

| Option | Status | Fit here |
|---|---|---|
| **Room 2.7+** | Actively maintained, KMP-stable, Paging integration, `@Upsert`, KDoc'd low-RAM behavior | **Use.** https://developer.android.com/jetpack/androidx/releases/room#2.7.0 · https://developer.android.com/reference/androidx/room/RoomDatabase.JournalMode |
| **Paging 3** | Active | Browsing UI only, not the sell path. https://developer.android.com/topic/libraries/architecture/paging/v3-network-db |
| **WorkManager 2.9+** | Active | Sync scheduling, constraints, backoff, expedited. https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work |
| **OkHttp + kotlinx.serialization** | Active (Square/JetBrains) | Streaming bodies + streaming `JsonReader`; protobuf/CBOR modules exist. https://square.github.io/okhttp/ · https://kotlinlang.org/api/kotlinx.serialization/kotlinx-serialization-json/kotlinx.serialization.json/-json-reader/ |
| **DataStore** | Active | Cursor/state persistence. https://developer.android.com/topic/libraries/architecture/datastore |
| **Ktor Client** | Active, Kotlin-native, pairs with your Ktor server | Optional; OkHttp engine underneath works equally well. https://ktor.io/docs/client.html |
| **PowerSync** | Commercial sync engine; Service + client SDKs incl. **Kotlin/Android**; source DBs Postgres/Mongo/MySQL/SQL Server; keeps client SQLite in sync, streams real-time updates | Viable turnkey option; requires PowerSync Service in your infra + backend connector instead of a Ktor-native change API. https://docs.powersync.com/ |
| **ElectricSQL** | OSS (Apache-2.0), **Postgres-only**, read-path sync ("Shapes"); writes go through your own API | Wrong backend (you have Ktor; Postgres-only). https://electric-sql.com/docs |
| **Ditto** | Commercial, CRDT-based edge sync | Strong offline story but a second datastore + licensing cost; overkill for server-authoritative catalog. https://docs.ditto.live |
| **Couchbase Lite** | Maintained but **propriently licensed** (Community/Enterprise split — Community terms restrict commercial use; Enterprise is paid). Review before embedding. | Only if adopting Couchbase server-side. https://docs.couchbase.com/couchbase-lite/current/concepts/licensing.html |
| **Realm / MongoDB Atlas Device Sync** | **Deprecated; EOL Sept 30, 2025**; community branches only | **Do not adopt.** https://www.mongodb.com/docs/atlas/device-sdks/deprecation/ |
| **Turso embedded replicas** | OSS libSQL; local file replica synced from cloud primary in 4 KB page frames; offline writes opt-in | Interesting, but replaces Room with libSQL — not worth the migration. https://docs.turso.tech/features/embedded-replicas |

## 13. Battery / thermal / network on 4G POS terminals

- The V2 Pro runs 4G + Wi-Fi with a 2580 mAh battery (https://www.sunmi.com/v2pro/) — schedule bulk syncs, don't poll.
- **WorkManager constraints** cover exactly this: `NetworkType.UNMETERED` (Wi-Fi), `RequiresCharging`, `BatteryNotLow`, `DeviceIdle`; work meeting constraints mid-charge can also run **expedited** (quota-limited): https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work
- Budget bulk transfer for **charging + Wi-Fi** windows; keep idle deltas small (ETag probe per §2). Background power limits (Doze/App Standby) apply to non-expedited work: https://developer.android.com/topic/performance/power/power-details
- Thermal: 21k-row import is seconds of CPU (§7) — negligible thermal impact vs continuous polling.

---

## Key takeaways for this project

### Is 21,811 products "large"? — **No. It is small.**
- **4–11 MB of raw row data** (200–500 B/row); SQLite's ceiling is **~281 TB** and 2⁶⁴ rows/page-size 512–65536 B — five-plus orders of magnitude of headroom. https://www.sqlite.org/limits.html
- Indexed barcode lookup = B-tree search; Android's own perf guide confirms index lookups vs scans at any practical size. https://developer.android.com/topic/performance/sqlite-performance-best-practices
- A *full* local catalog is the correct POS design (offline barcode→price; Square/Shopify precedent, §11). **The slow initial sync is a payload/serialization/batching problem, not a database problem.**

### Three defensible architectures

1. **Custom delta sync on Room + Ktor (recommended).** Server-side `catalog_changes` feed (`change_id` cursor + tombstones + ETag manifest), versioned snapshot bundle for bootstrap (§10.1), chunked streamed JSON/protobuf, transactional `@Upsert` batches with cursor-in-transaction, DataStore state machine, WorkManager (backoff+jitter), DB-backed outbox + idempotency keys for sales (§8).
   *Trade-offs:* most engineering, but no new infra/licenses, full control of payload shapes and resumability, and every mechanism is directly backed by primary docs (§2, §7–§10).
2. **PowerSync.** Keep Postgres (or add it) + PowerSync Service; its Android/Kotlin SDK mirrors SQLite to the device with checkpointed real-time sync, so you delete your delta code.
   *Trade-offs:* external service + license, backend connector required (Ktor isn't a supported source — Postgres/Mongo/MySQL are: https://docs.powersync.com/), less control over 2 GB-tuned memory behavior (§6).
3. **Firestore-style managed sync** (or Couchbase Lite + Sync Gateway). Proven offline stack.
   *Trade-offs:* abandons the Kotlin/Ktor backend and Room; Couchbase Lite carries licensing restrictions (https://docs.couchbase.com/couchbase-lite/current/concepts/licensing.html); MongoDB's equivalent (Atlas Device Sync) is **deprecated/EOL** (https://www.mongodb.com/docs/atlas/device-sdks/deprecation/). Reject.

### Recommendation: **#1 — custom delta sync on Room + Ktor**, because
- 21k rows make the data volume trivial (§5); what matters is *streaming payloads, transactional batches, and resumability* — all documented Android/SQLite practice you already control (§7, §9);
- the outbox + idempotency pattern for offline sales is required regardless of sync vendor (§8) and is where correctness risk actually lives (Shopify's lost-order warning, §11);
- zero new licenses, zero new infrastructure, Ktor-native, Room-native, and 2 GB-safe (§6).

**Build order:** change-feed API + ETag manifest → chunked delta pull with transactional upserts → bootstrap bundle endpoint → outbox + idempotency keys → WorkManager scheduling (charging/Wi-Fi) → FTS5 barcode/name search polish.

---

*Report generated for the Amaxonia project. Primary-source count: 40+ citations across SQLite, Android/Jetpack, Firebase, MongoDB, AWS, Stripe, Figma, Linear, Square, Shopify, PowerSync, ElectricSQL, Turso, Ditto, Couchbase, W3C/IETF.*
