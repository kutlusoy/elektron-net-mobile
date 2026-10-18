# Elektron Net: `elektron-net-mobile` Full Node Wallet Plan

- **Version:** 0.5 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, C++ developers working on `elektrond` builds, release engineers, security reviewers
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/validation.cpp` (`WriteAutomaticSnapshot()`, `PopulateAndValidateSnapshot()`), `src/init.cpp` (`MaybeActivateAutomaticSnapshot()`, `UpdateLocalSnapshotServices()`), `src/net_processing.cpp` (`MaybeRequestSnapshot()`), `src/wallet/wallet.cpp` (`ScanUTXOSet()`, `CreditUTXOFromChain()`, `AttachChain()`), `src/wallet/walletutil.cpp` (`GenerateWalletDescriptor()`), `src/kernel/chainparams.cpp` (treat these as ground truth)
- **Related repositories:** [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (light wallet, source of branding, address safety UX and strings), [`elektron-net-mempool`](https://github.com/kutlusoy/elektron-net-mempool) (explorer, deep links only)
- **See also:** [`guideline-wallet-integration.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/guideline-wallet-integration.md), [`AUDIT_PRUNING_SNAPSHOT.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/AUDIT_PRUNING_SNAPSHOT.md), [`BITCOIN_CORE_DIFF.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/BITCOIN_CORE_DIFF.md), [`CHANGELOG-slip44-coin-type.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/CHANGELOG-slip44-coin-type.md), [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Scope

`elektron-net-mobile` is an Android wallet that ships and operates its own complete Elektron Net node. The phone is a sovereign participant of the network, not a client of a server.

**In scope (v1):**

- Full validating node (`elektrond`) bundled in the APK, running as a managed local process.
- All node duties that the desktop/Linux node performs: header sync, block download and validation, UTXO attestation checks on every block, mempool and transaction relay, mandatory pruning, automatic checkpoint snapshots, P2P snapshot bootstrap, snapshot serving where resources allow.
- Non-custodial wallet on top of the node's own descriptor wallet (keys live in Core, not in Kotlin).
- Background operation as an opt-out feature with explicit resource limits. The user can stop the node at any time.

**Out of scope for v1 (tracked in §17):** Lightning, hardware signer support, iOS, Tor/I2P, multi-wallet UI, fiat prices.

**Why a full node instead of an Electrum-style client:** the light-client path requires an external Electrum-protocol server and a per-header SPV difficulty implementation including the Stoic Awakening rules (see `guideline-spv-header-difficulty-verification.md`). The app's own full node validates everything itself, removes the server trust assumption, and needs neither. No server infrastructure of any kind is required to run the app. The cost is storage, battery and bandwidth, which this plan budgets explicitly (§6, §7).

## 2. Findings From Source Review

Verified against `kutlusoy/elektron-net@main` documentation and the reviewed concept document (an existing alpha Android implementation based on Core v4.0.5; this plan does not pin that version, see §11.1). Items marked **verify** MUST be confirmed against source before implementation starts.

### 2.1 Chain properties that shape the mobile design

| Property | Value | Consequence for the app |
|---|---|---|
| Block time | 60 s, about 1,440 blocks per day | Constant validation work; steady-state load is higher than a 10-minute chain |
| Mandatory pruning | `MANDATORY_PRUNE_DEPTH` = 197,280 blocks (about 137 days), no archival mode | Storage is bounded by design. Phone nodes are first-class citizens, not degraded nodes |
| Planning estimate for the retained window | `ELEKTRON_MANDATORY_PRUNE_WINDOW_GB` = 10 (`src/qt/guiconstants.h`) | Treat as an upper planning bound, measure real numbers in Phase 0. A team test build on an emulator (October 16, 2026, tip 269,354, pruning from block 197,281) showed 29.6 MiB of blockchain data. Whether chainstate and snapshot files are included in that figure is not confirmed |
| UTXO attestation | Every block at height > 0 carries `OP_RETURN <height> <hash>`; MuHash incremental from height 137,000 | Validation cost per block is bounded by coins touched, which is what makes mobile validation feasible |
| Automatic snapshots | `.dat` + `.hash` in `datadir/snapshots/` at each checkpoint (mainnet 197,280) | Matches the requested "automatic snapshots" feature natively. The app supervises, it does not reimplement |
| Snapshot bootstrap | `MaybeRequestSnapshot()` triggers when `header_height - tip_height > 197280`, otherwise normal IBD inside the window | First-run UX has two paths and the app MUST support both (§5.1) |
| P2P | Port 8333, protocol 70017, magic `e1ec7a6e`, DNS seed `seed.elektron-net.org`, `NODE_SNAPSHOT` = service bit 12 | Standard peer discovery works from a phone. Inbound reachability is unlikely behind carrier NAT |
| Units | ELEK / lep, 1 ELEK = 10^8 lep | All UI and fee display use lep/vB |
| Addresses | Bech32 HRP `be` (`be1q...`, `be1p...`); Base58 prefixes identical to Bitcoin mainnet | See §8.5 |
| Derivation | SLIP-44 coin type 1370 for wallets created from v4.0.4; legacy wallets used `0'` | Recovery MUST scan both (§8.3) |
| Wallet on pruned node | `AttachChain()` runs `ScanUTXOSet()` via `Chain::forEachCoin()` when block rescan is impossible; `MaybeRescanUTXOSetAfterSnapshot()` re-runs it after snapshot activation | Balance recovery is native. History before the window is unrecoverable by design |
| Reorgs | Reorgs deeper than the latest snapshot base are not supported (accepted design trade-off, audit §4.5) | Must be disclosed in the app's trust model text |

### 2.2 Deltas between the reviewed concept and this plan

The concept document is a good proof that the architecture works. These are the points where this plan intentionally differs or tightens:

| # | Concept behavior | Plan decision | Reason |
|---|---|---|---|
| 1 | Derives only `m/84'/1370'/0'` | MUST also import the legacy `m/84'/0'/0'` descriptors (and scan them) | `guideline-wallet-integration.md` §3.1 makes the dual scan a MUST. A seed from a pre-4.0.4 wallet would otherwise show a silent zero balance |
| 2 | Custom native patch adding `rescanutxoset` RPC, applied by a Python patcher at build time | Propose the RPC upstream in `elektron-net` (small wrapper over `CWallet::ScanUTXOSet()`), keep the patcher only as a bridge. The RPC does not exist in `elektron-net@main`, see §8.3 | A permanently patched Core drifts from the reference node and hurts auditability. Cross-repo item X1 (§12) |
| 3 | Fixed descriptor range `0..999` per branch | Use a ranged descriptor with a tracked next index and automatic range top-up | A fixed window silently loses funds beyond index 999 and over-derives for everyone else |
| 4 | RPC credentials via `-rpcuser/-rpcpassword` style arguments | Prefer `-rpcauth` (salted hash) or the cookie file inside the private data dir | Avoids plaintext secrets in the process argument list |
| 5 | Fee presets Economy 0.1, Normal 2, Priority 5 lep/vB hard-coded | Derive presets from `estimatesmartfee` and the node's `mempoolminfee`/`minrelaytxfee`, keep manual override with policy re-check before signing | Hard-coded values go stale as the fee market develops. The re-check-before-sign behavior of the concept is kept |
| 6 | "Pruned node" treated as a possible state | Treated as the only state | Mandatory pruning has no archival mode. Rescan logic always starts from `pruneheight` |
| 7 | Web3j for BIP-39/BIP-32 bootstrap | Replace with a minimal audited BIP-39 implementation and Core-side derivation where possible (**verify** whether `sethdseed` + `importdescriptors` can be driven from entropy without Kotlin-side xprv handling) | Fewer dependencies in the most sensitive code path |
| 8 | `FLAG_SECURE` "where applicable" | MUST be set on every screen that can show seed, passphrase, or unsigned transaction details | Removes judgment calls from review |
| 9 | Alpha upgrade path "do not clear app data" | Datadir carries a version marker; incompatible chain/protocol changes trigger a controlled reset with wallet re-import from seed | `BITCOIN_CORE_DIFF.md` §8 documents that v4.0 made old datadirs incompatible. The app must survive the next such event gracefully |

## 3. Product Definition

### 3.1 User-facing capabilities

1. Create wallet (12 words default, 24 words optional), restore wallet, show seed backup flow with verification quiz.
2. Receive: QR, address list, `be1q` default, labels, address reuse avoidance.
3. Send: PSBT review-before-sign, fee presets plus advanced, coin control, QR scan, address validation with Base58 collision warning.
4. Node dashboard: sync phase, height, peers, bandwidth, disk usage, prune height, last snapshot, mempool, version.
5. Node controls: Start/Stop, resource profile, network policy (Wi-Fi only, charging only, data cap), storage cap, snapshot options.
6. Backup/restore: seed (primary), encrypted wallet file export via Storage Access Framework (secondary), descriptor export (watch-only).
7. Diagnostics: bounded in-app log window, export of sanitized log.
8. Two sections, **Wallet** and **Node**, with a live view of what the node is doing (§3.3, §3.4), fully localized with an in-app language switch (§3.5).

### 3.2 Resource profiles

Profiles map to `elektrond` startup arguments. The numbers below are starting hypotheses to be replaced by Phase 0 benchmark results.

| Profile | When active | Intent | Arguments (starting point) |
|---|---|---|---|
| **Foreground** | App visible, no background service | Lowest footprint, catch up when opened | `-dbcache=64 -maxconnections=8 -maxmempool=30 -blocksonly=1` |
| **Balanced** (default) | Foreground service while charging or battery above threshold, on unmetered network | Keeps tip current without noticeable drain | `-dbcache=128 -maxconnections=16 -maxmempool=50` |
| **Always-on** | Foreground service permanently, user opt-in | Best relay/serving behavior | `-dbcache=256 -maxconnections=24 -maxmempool=100` |
| **Initial sync boost** | Only during first IBD or snapshot tail sync, device RAM permitting | Shorten the heaviest phase | Temporary `-dbcache` raised to a fraction of free RAM, then restart into the active profile |

- `-blocksonly` in the Foreground profile reduces bandwidth but also stops mempool-based unconfirmed-incoming display. The UI MUST say so.
- All profiles use `-listen=1` only when on unmetered networks. Inbound connections behind carrier NAT will be rare, so no effort is spent on port mapping in v1.
- Every argument above is a standard Core option inherited by the fork. **Verify** each one against `src/init.cpp` of the Core revision resolved at build time (§11.1).
- All profiles disable port mapping with `-natpmp=0` (the option exists in `elektron-net@main` and defaults to on, there is no `-upnp` option any more). A team test build logged repeated `pcp: Could not connect to gateway` warnings, which is noise and a pointless network probe on a phone.

**Storage saver (`-fastprune`).** `-fastprune` is a debug-category option of the node (listed by `-help-debug`, hidden from `-help`). In `src/node/blockstorage.cpp` it caps a `blk` file at 64 KiB instead of 128 MiB and shrinks the preallocation chunk to 16 KiB. Pruning deletes whole `blk`/`rev` files only, so the retention window is enforced with a granularity of one file: with default files, a node keeps up to one extra 128 MiB file beyond the 137-day window, and on a young low-traffic chain the oldest file can stay in place for a very long time. With `-fastprune` the retained data follows the window closely. It does not change consensus or chain parameters, it only changes block file sizes (verified in `elektron-net@main`, `447c069`).

- Costs to measure in Phase 0: far more files (block data size divided by 64 KiB, plus one `rev` file each), more open/close and finalize/truncate work, longer directory scans at startup, more inodes on the device.
- Upstream risk: a debug-only option can change or disappear while the app always tracks current Core (§11.1). The contract check (§11.2) verifies that the option is accepted and that it still makes pruning observable. The supervisor passes it only when the build manifest says it is supported.
- Control: `NODE_FASTPRUNE_DEFAULT` in `core-source.env` (default `0`) is recorded in the build manifest and sets the app default. A user setting "Storage saver" overrides it at runtime. Emulator and CI regtest runs always use `-fastprune`.
- Switching it on or off on an existing datadir is expected to be safe because block file sizes are tracked per file. The contract check restarts a regtest node with the option toggled to confirm this.
- Whether release builds enable it by default is decided after the Phase 0 measurements (§17, Q10).

### 3.3 Sections and navigation

The app has two sections. Both are one tap away from each other at all times.

| Section | Screens |
|---|---|
| **Wallet** | Home (balance, UTXO count, addresses with balance, Receive/Send with QR scan), Receive and address list, Send, History, Security and backup |
| **Node** | Status, Network telemetry, Pruning and snapshots, Peers, Live log, Controls, Recovery tools |

- A compact node status chip (sync state, peer count) sits in the Wallet header and opens the Node section. While the node is not synchronized, the Wallet shows a banner that balances and history may be incomplete, instead of a bare zero.
- Primary navigation is a bottom bar. The reviewed team screenshots use four tabs (Wallet, Network, History, Debug). The final grouping into the two sections above is a design decision for Phase 1, the requirement is only that every Node screen is reachable without digging through settings.
- Visual direction taken from the screenshots: dark theme with cyan accent, rounded cards, Elektron logo, one hero card per screen. The screenshots show background artwork of a helmet that looks like third-party intellectual property. It MUST NOT be copied into this repository. Artwork is created or licensed separately (§17, Q13).

### 3.4 Node transparency

The user can see what the node is doing whenever they want. Everything in the Node section is read from the local node and shown only while visible.

| Element | Source |
|---|---|
| Sync hero card (phase, "Synchronized", validated blocks, known headers, progress) | `getblockchaininfo` (`blocks`, `headers`, `verificationprogress`, `initialblockdownload`), snapshot phase from the supervisor (§5.1) |
| Peers (in/out counts, P2P listen state) | `getnetworkinfo` (`connections_in`, `connections_out`, `localservices`, `networkactive`) |
| Peer list (address, direction, user agent, ping, height, country) | `getpeerinfo`, country from the local MMDB (§8.6) |
| Chain telemetry (chain, difficulty, hashrate, mempool, disk) | `getmininginfo`, `getnetworkhashps`, `getmempoolinfo`, `getblockchaininfo` (`size_on_disk`) |
| Pruning and checkpoint (retained window, retained from height, last snapshot, snapshot serving) | `getblockchaininfo` (`pruned`, `pruneheight`), tip height, `snapshots/` directory listing, `NODE_SNAPSHOT` in `localservices` |
| Transaction history | wallet RPCs, with an explorer link as an opt-in setting (X7) |
| Live log | node stdout through the bounded ring buffer (§4.2) |
| Recovery tools (UTXO set scan card with status) | wallet RPCs, `rescanutxoset` once available (X1) |

- **Live log:** off by default, switched on by the user, with a visible indicator that it is active. It shows a bounded window of recent lines (new tip, new block header, peer events, snapshot events, warnings). When it is off, nothing from the UI side is captured, but the supervisor still drains the node output.
- **Log hygiene:** the node log can contain addresses and transaction ids. Copy and export default to a sanitized form with addresses and txids masked, and the user must confirm to export the raw log. Log lines themselves stay in English (technical text, not translated).
- **Polling cost:** the Node section polls fast while visible. When hidden, only the minimum needed for the notification and policy engine runs (§4.2, §7).
- The Node section also hosts the controls from §3.1 item 5, so a user who sees a problem can act on it in the same place.

### 3.5 Internationalization

Multi-language support is a core requirement, not a late polish item.

- **MUST:** no user-visible text in code. All text lives in Android string resources, with plurals, positional format arguments and no concatenated sentences. A CI lint gate turns missing translations and hard-coded strings into errors for the supported locales.
- **Locales at launch:** English (source), German, Spanish, French, Italian (the five already used by the reviewed concept). More languages are an open question (§17, Q12).
- **In-app language switch** independent of the system language, using Android per-app language preferences (`LocaleManager` on Android 13 and later, AndroidX `AppCompatDelegate.setApplicationLocales` on older versions), declared with a `localeConfig` resource. Default follows the system locale.
- **Formatting:** amounts, dates, times and numbers use the active locale (the Spanish screenshot shows a decimal comma). The unit labels (ELEK/lep) are never translated or reformatted in a way that changes the value. Layouts MUST survive text expansion (German and French are typically 30 percent longer) and system font scaling, and MUST support right-to-left locales if one is added.
- **Errors:** RPC and node errors are mapped by error code to localized messages for the cases the app knows (wallet locked, insufficient funds, fee too low, node not synced, and so on). The raw node message is available as a secondary details line, in English.
- **Terminology:** a per-language glossary fixes how terms such as UTXO, mempool, peer, pruning, checkpoint and snapshot are translated or kept in English, so the Node section reads consistently.
- **Review:** the seed backup warnings, the trust model text (§9) and the Base58 collision warning (§8.5) MUST be reviewed by a native speaker for every shipped language before a release. Machine translation is acceptable only as a first draft.
- **Tests:** pseudo-localization build to catch clipped or hard-coded text, screenshot tests for the longest locale, and a check that every string key exists in every shipped locale.
- Translation files are the one place in this repository that is not English (they hold the target languages by design).

## 4. Architecture

```text
+------------------------------------------------------------+
| Android app (Kotlin, Jetpack Compose)                      |
|  UI | Wallet service | Node supervisor | Settings/Policy   |
+---------------+--------------------+-----------------------+
                | JSON-RPC           | process control, files
                | 127.0.0.1 only     |
+---------------v--------------------v-----------------------+
| libelektrond.so (native executable from nativeLibraryDir)  |
|  P2P | validation | chainstate | pruning | snapshots       |
|  descriptor wallet (keys, signing)                          |
+------------------------------------------------------------+
        app-private storage: <filesDir>/elektron/
```

### 4.1 Process model

- `elektrond` runs as a child process of the app, started from `nativeLibraryDir` (the concept proves this works). The APK MUST declare native libraries as extracted (`android:extractNativeLibs="true"`, Gradle `packaging.jniLibs.useLegacyPackaging = true`), otherwise the file is not executable on disk.
- Process separation is kept deliberately: a node crash must not take down the wallet UI, and Core's shutdown semantics stay unmodified.
- **Risk to resolve in Phase 0:** Android 12+ phantom process handling and OEM task killers can terminate child processes of an app independent of the app's own process state. A spike MUST measure survival of the child process under: screen off for hours, low memory pressure, app swiped from recents, vendor battery managers. Decision gate: if child-process survival is not acceptable, move to loading the daemon into an Android `:node` service process (separate OS process declared in the manifest) instead of a forked child.
- Graceful shutdown: the supervisor calls RPC `stop`, waits for exit with a timeout, and only then escalates to SIGTERM. Kill without `stop` is an expected event on Android and Core's LevelDB-based stores must be tested against it (§14.3).

### 4.2 Node supervisor responsibilities

1. Start/stop with the correct argument set per profile and datadir.
2. Always drain stdout/stderr (a full pipe blocks Core). Keep a bounded ring buffer, not unlimited logs.
3. Health: poll `getblockchaininfo`, `getnetworkinfo`, `getpeerinfo`, `getmempoolinfo` at an adaptive interval (fast in foreground, slow in background).
4. Detect and classify failure: crashed, killed, corrupted chainstate, disk full, incompatible datadir. Each has a defined recovery action (restart, `-reindex-chainstate` offer, snapshot re-bootstrap, reset with wallet re-import).
5. Enforce policy: network type, battery, charging state, storage cap, user pause.
6. Persist `RPC` credentials encrypted with Android Keystore (AES-256-GCM, non-exportable key).
7. Own the config file. On first launch the node writes a default `bitcoin.conf` into the datadir with the mainnet seed and `addnode` list (observed with `elektron-net@main`). That is fine for the mainnet flavor and keeps the maintained peer list. Every other chain fails on the next start (`-addnode` only applied on regtest when in a `[regtest]` section), and passing `-conf=` elsewhere does not stop the file from being generated, which then fails with "contains a bitcoin.conf which is ignored" unless `-allowignoredconf=1` is set. Therefore any non-mainnet flavor and every test pre-creates its own `bitcoin.conf` in the datadir before the first start, and the supervisor checks that the file matches the chain before launching.

### 4.3 Foreground service and background execution

- Background operation MUST use a foreground service with a persistent notification showing sync state and a Stop action.
- Service type: Android 14+ requires a declared `foregroundServiceType`. `dataSync` has runtime limits on recent Android versions, so a long-running node needs a type that allows continuous operation (for example `specialUse` with the required property declaration). **Verify** current platform rules and store policy before choosing. Direct APK and F-Droid distribution have fewer constraints than Play.
- Doze and App Standby restrict background network access. A battery-optimization exemption flow MAY be offered as a guided, explained, user-initiated setting. The app MUST work without it, with reduced background activity, and say so honestly in the UI.
- WorkManager is used only for opportunistic catch-up (charging + unmetered) when the service is not running, never as the primary keep-alive.
- Boot start is opt-in. Never auto-start on boot by default.

### 4.4 Security boundary on localhost

Any app on the device can attempt TCP connections to `127.0.0.1`. Therefore:

- RPC MUST require authentication (`-rpcauth` or cookie), bound to `127.0.0.1`, `-rpcallowip=127.0.0.1`.
- Wallet RPCs that move funds MUST additionally require the wallet passphrase to be unlocked. The app never leaves the wallet unlocked outside the signing window.
- **Hardening option for Phase 3:** evaluate a Unix domain socket for RPC if the Core revision resolved at build time supports it (**verify**), which would remove the TCP surface entirely.
- P2P port is not exposed on cellular by default (§3.2).

## 5. Node Lifecycle and Sync

### 5.1 First-run decision tree

```text
Start
  |
  v
Fetch headers (peers via DNS seed)
  |
  +-- header_height - 0 <= 197280 --> normal IBD inside retention window
  |
  +-- header_height - tip > 197280 --> snapshot bootstrap
          getutxosnapshot (to NODE_SNAPSHOT peers first)
          utxosnapshot -> getsnapshotdata chunks (1 MB)
          .dat + .hash required, content hash verified
          activation, then tail sync to tip
```

The UI MUST show distinct phases (this is already a strength of the concept): node startup, peer discovery, header sync, snapshot download (with bytes and ETA), snapshot verification/activation, block validation, synchronized. The headline progress value MUST NOT show a misleading 0% during header sync.

**Current chain state (from a team test build, October 16, 2026):** the tip was at 269,354, which is beyond the first checkpoint at 197,280. By the documented trigger (`header_height - tip_height > 197280`), a fresh install is therefore already on the snapshot path, and the plain IBD path only applies to returning nodes that fell behind by less than the retention depth. Phase 1 test priority goes to the snapshot bootstrap.

### 5.2 Snapshot handling on a phone

- Snapshots are produced and cleaned up by Core (`WriteAutomaticSnapshot()` keeps only the current checkpoint's files). The app does not copy, move or edit them.
- Snapshot writing is an I/O and `cs_main` heavy moment. The supervisor SHOULD show a transient "checkpoint snapshot in progress" state and MUST NOT kill the node during it. A graceful stop request during that window waits for completion up to a bounded timeout.
- Snapshot download resume: audit §4.8 documents that chunk tracking is lost on restart and bandwidth is wasted. On mobile data this matters. The default policy is to run the snapshot download only on unmetered networks and to ask once before using cellular. A Core-side improvement (persistent received-range tracking) is proposed as cross-repo item X3.
- Serving snapshots: a node holding `.dat` + `.hash` advertises `NODE_SNAPSHOT` automatically and uploads chunks to peers. On a phone this is a bandwidth cost. The app MUST expose a setting for it (default: on only for unmetered + charging). Whether Core can switch this off cleanly without a source change is **to verify**. If not, see X2.
- Disk guard: before a snapshot write or download the supervisor checks free space against the current snapshot size plus margin and refuses with a clear message instead of failing mid-write.

### 5.3 Reaching "fully synced" and staying there

- Tip following needs one block validation per minute. The Balanced profile is sized so this is sustainable on a charging or well-charged device.
- After long offline periods the node either catches up inside the window or, if it fell behind by more than the retention depth, re-bootstraps from a newer snapshot via the logic Core already contains (audit §3.2, `HasFallenBehindPruneHorizon()`). The app only needs to surface this state and avoid interpreting it as an error.

### 5.4 Datadir versioning

- The app stores `datadir.version` (app schema + chain/protocol generation) next to the data.
- On mismatch (for example a future genesis restart as in v4.0): stop node, show an explanation, offer reset, then re-import wallet from seed. Never auto-delete a datadir that contains a wallet without a verified seed backup confirmation flag.

## 6. Storage Plan

| Item | Notes |
|---|---|
| Blocks + undo within window | Bounded by the prune depth. Real size to be measured against the live chain in Phase 0 |
| Chainstate (LevelDB) | Grows with UTXO set. Dominates RAM sensitivity through `-dbcache` |
| `snapshots/` | One current checkpoint pair at a time |
| Wallet db | Small |
| Peers/GeoIP/logs | Small. Logs bounded |

- The app MUST require and display a storage budget. Default hard stop: free space below a configured floor pauses the node and notifies the user.
- Target devices: 64-bit ARM (`arm64-v8a`) only for release. 32-bit ARM is not supported (address-space and memory limits for the chainstate). `x86_64` is built for emulator/CI only.
- Internal storage only for the datadir (app-private, excluded from backup). SD card/adoptable storage is not supported in v1 because of performance and removal risk.

## 7. Power, Data and Thermal Policy

- Policy inputs: charging state, battery percentage, thermal status, network type (unmetered/metered/roaming), data saver, user schedule.
- Defaults: node runs fully when charging on Wi-Fi; runs in reduced mode on battery above 30%; pauses block download below 15% or on thermal throttling; never uses cellular for snapshot/IBD without explicit consent.
- A monthly cellular data cap setting is enforced by the supervisor using `getnettotals` deltas plus Android traffic stats, and by `-maxuploadtarget` (**verify**) for upload.
- Phase 0 benchmark metrics: mAh per day in each profile, MB per day down/up, sustained CPU, peak RSS, time to first sync on three reference devices. Profiles in §3.2 are adjusted from this data, not from guesses.

## 8. Wallet Design

### 8.1 Key custody

- The node's descriptor wallet holds and uses the private keys. Kotlin touches key material only during creation/recovery bootstrap.
- Mnemonic and seed are never written to preferences, files, logs or crash reports. Use `ByteArray`/`CharArray` and explicit zeroing, accept that the JVM cannot guarantee full erasure, and keep the exposure window to the creation screen.
- Wallet passphrase is chosen by the user, handled only by Core (`encryptwallet`/`walletpassphrase`). It is not stored by the app.
- **Open decision (§17, Q3):** an opt-in biometric unlock that stores the passphrase wrapped by a Keystore key requiring user authentication. This is a convenience/security trade-off and stays off by default.

### 8.2 Descriptors

```text
Current  (coin type 1370): wpkh(<key>/84h/1370h/0h/0/*)   receive
                           wpkh(<key>/84h/1370h/0h/1/*)   change
Legacy   (coin type 0):    wpkh(<key>/84h/0h/0h/0/*)      receive
                           wpkh(<key>/84h/0h/0h/1/*)      change
```

- New wallets: current set is active, legacy set is imported inactive for receive but tracked for spending (mirrors the dual-track model of `contrib/wallettools/migrate-cointype.py`).
- Taproot (`tr(.../86h/1370h/...)`) is optional for v1. Default is native SegWit `be1q`.
- Range handling: ranged descriptors with `next_index` tracking and top-up when the unused look-ahead drops under a threshold. No fixed 0..999 import.

### 8.3 Creation and recovery flow

```text
Create:
  entropy -> BIP-39 -> descriptors (current + legacy) -> createwallet(blank, descriptors, encrypted)
  -> walletpassphrase (temporary) -> importdescriptors -> walletlock
  failure at any step: remove only the incomplete wallet, allow retry

Recover:
  gate: node synchronized to tip (or snapshot active and tail synced)
  1. help rescanutxoset - abort BEFORE wallet creation if unsupported
  2. createwallet(blank, passphrase) + walletpassphrase (long enough for steps 3 and 4)
  3. importdescriptors (current + legacy) with timestamp "now"
  4. rescanblockchain <pruneheight>            (retained blocks: history inside the window)
  5. rescanutxoset                             (CWallet::ScanUTXOSet, all currently unspent coins)
  6. walletlock (in a finally path), verify balance and that the address look-ahead reached an unused tail
```

- Recovery MUST be re-runnable from the UI without touching chainstate or restarting sync.
- Findings from the contract check on `elektron-net@main` (`447c069`, regtest with `-fastprune`): `importdescriptors` with `timestamp: 0` fails per descriptor on a pruned node (error -1, "Rescan failed ... pruning"), although the wallet is left with a partial balance. Use `"now"` and the explicit rescan. `rescanblockchain` fails with -13 while the wallet is locked, so the wallet must stay unlocked for the whole scan, and the scan can run for minutes on a phone. A reload (`unloadwallet` then `loadwallet`) does not restore coins from pruned blocks: the restored test wallet showed 114.6 of 1,400.9. `rescanutxoset` does not exist in `elektron-net@main`, so X1 is a hard prerequisite for recovery.
- Use the `checksum` field of `getdescriptorinfo` and append it as `#checksum`. The `descriptor` field it returns has the private keys stripped and cannot be imported.
- The recovery unlock is the one exception to the 10 second rule (S4). It is bounded to the scan, requires the app in the foreground with the screen kept on, shows progress, and `walletlock` runs in a `finally` path.
- Honest UX text: balances are restored fully. Transaction history older than the retention window (about 137 days) does not exist anywhere on the network.
- Wallet loaded mid-IBD can show a transient zero balance until snapshot activation triggers `MaybeRescanUTXOSetAfterSnapshot()`. The UI MUST show "waiting for sync" instead of "0".

### 8.4 Sending

Review-before-sign PSBT flow, kept from the concept:

```text
walletcreatefundedpsbt -> decodepsbt -> validate (destination, amount, fee, change on /1/*)
-> show review (lep, ELEK, vsize, lep/vB, total deducted)
-> re-validate PSBT and fee policy -> walletpassphrase (<= 10 s)
-> walletprocesspsbt -> finalizepsbt -> sendrawtransaction -> walletlock (always, in finally)
```

- Policy re-check (`getnetworkinfo`, `getmempoolinfo`) immediately before unlock. If it changed, abort and re-review. The app never silently changes a user fee.
- `walletlock` is also requested when the app leaves the foreground.
- Coin control by address, UTXO list with labels, RBF on by default, CPFP/bump fee screen in Phase 4.
- Because the node is local, broadcast reaches the P2P network directly and is not routed through a server. Nothing is leaked to an indexer.

### 8.5 Address safety (MUST)

Base58 prefixes equal Bitcoin mainnet's (`guideline-wallet-integration.md` §4.4). Therefore:

- Only Bech32 `be1...` receive addresses are generated and shown by default.
- Pasting or scanning a legacy Base58 address or importing a WIF key shows an explicit warning that the same string is valid on Bitcoin mainnet.
- Addresses with HRP `bc`/`tb`/`bcrt` are rejected with a specific message.
- The `elektron-net-electrum` implementation of this UX is the reference for wording and behavior to keep both wallets consistent.

### 8.6 Local data, labels and privacy

- Labels and address book live in the app database (encrypted at rest with a Keystore-wrapped key), never sent anywhere.
- GeoIP for peer country uses a local MMDB, no remote lookup.
- No analytics SDKs, no crash reporting that transmits data by default.

## 9. Security Requirements

| # | Requirement | Level |
|---|---|---|
| S1 | RPC bound to loopback with authentication, credentials encrypted via Keystore | MUST |
| S2 | `android:allowBackup="false"` and no data extraction rules that include the datadir | MUST |
| S3 | `FLAG_SECURE` on seed, passphrase, send-review and signing screens | MUST |
| S4 | Unlock window at most 10 s, `walletlock` in a `finally` path. The recovery rescan is the only exception (§8.3) | MUST |
| S5 | PSBT re-validation immediately before unlock | MUST |
| S6 | No mnemonic or passphrase in logs, notifications, clipboard (or auto-clear clipboard after a short timeout for addresses) | MUST |
| S7 | Native binary integrity: Core commit SHA resolved at build time and SHA-256 of `libelektrond.so` recorded in a build manifest, verified by the app at startup | MUST |
| S8 | Touch-filtering against overlay attacks on confirmation buttons (`filterTouchesWhenObscured`) | SHOULD |
| S9 | Rooted/instrumented device detection as a warning only | SHOULD |
| S10 | Reproducible builds and signed releases with published hashes | SHOULD |
| S11 | Independent security review before any mainnet release with a "stable" label | MUST |

**Trust model disclosure text (in-app and docs):** the node verifies every block since its snapshot base or since genesis, enforces the per-block UTXO attestation, and checks a downloaded snapshot against the committed hash. It cannot validate reorgs deeper than the snapshot base, and a fresh snapshot bootstrap trusts the on-chain attestation committed by proof of work. This is the network's documented model, and the app must not describe it as "full archival verification".

## 10. Repository Layout (proposal)

```text
elektron-net-mobile/
  app/                                  Android application module
    src/main/java/org/elektronnet/mobile/
      node/        supervisor, argument builder, RPC client, policy engine
      wallet/      bootstrap, send flow, recovery, descriptors
      security/    keystore, secure window helpers
      ui/          Compose screens
    src/main/jniLibs/{arm64-v8a,x86_64}/libelektrond.so
    src/main/res/                       strings (en, de, es, fr, it)
  native-build/                         reproducible NDK build of elektrond
    core-source.env                     Core repository URL, ref to track, NDK version
    fetch-core.sh                       resolves and fetches the current elektron-net source
    build.sh, install-core.sh, pin-ndk.sh, patches/
    contract-check.py, contract/        interface check against the built node (§11.2)
    docker/                             builder image and in-container build script
    tests/                              tests of the build scripts
  doc-elektron/                         guidelines and reports (this file)
  .github/workflows/                    CI
```

- Application ID: `org.elektronnet.mobile` (as used by the concept; **confirm** in §17, Q1).
- Language of code, comments and docs: English. UI strings: English plus translations for the existing five locales.
- Code comments follow the repo conventions: minimal, only for non-obvious reasoning.

## 11. Native Build Plan

- `elektron-net/depends/hosts` currently contains `darwin`, `freebsd`, `linux`, `mingw32`, `netbsd`, `openbsd` and no Android host. An NDK cross-build therefore needs its own toolchain file and dependency handling (Boost, libevent, sqlite or the wallet DB backend in use, zeromq disabled, no Qt, no GUI). First spike: build `elektrond` only, wallet enabled, `-DBUILD_GUI=OFF`, for `aarch64-linux-android` API 28 and `x86_64`. **Verify** the CMake options on the fetched Core revision.
- Output MUST be linked with 16 KB page-size compatibility (`-Wl,-z,max-page-size=16384`) to satisfy current Android and store requirements for native code.
- Build is containerized and rootless. The output ownership problem seen with root-owned Docker output in the concept is avoided by running the container as the invoking user.
- The Core source is not pinned to a fixed tag. See §11.1.
- Patches: only what cannot be upstreamed yet (currently the `rescanutxoset` bridge, cross-repo item X1). The patch set and its SHA-256 are part of the build manifest so a stale binary is never reused.
- Test the unmodified Core unit tests that matter for a mobile build where feasible (snapshot, prune, wallet recovery functional tests listed in `BITCOIN_CORE_DIFF.md` §5.7) on the host build of the same fetched Core revision, and run a regtest integration suite against the x86_64 binary on an emulator.

### 11.1 Core source: always current, resolved on every build

Requirement: the app always ships the node from the canonical repository [`kutlusoy/elektron-net`](https://github.com/kutlusoy/elektron-net). No copy of Core is vendored in this repository, and no old checkout is reused.

- `native-build/fetch-core.sh` runs at the start of every build (local and CI). It resolves the tracked ref to a commit SHA with `git ls-remote` (see the ref policy below), then clones or fetches that exact commit into a build cache and checks it out clean. A cached tree that does not match the freshly resolved SHA is discarded.
- The native build checks the manifest of the previous output (`Core SHA`, patch set SHA-256, NDK version). The binary is rebuilt whenever any of them differs, so a stale `libelektrond.so` can never be reused (this keeps the safety property of the reviewed concept).
- **Ref policy by build type** (`BUILD_TYPE` in `core-source.env`, set by CI, default `dev`):
  - `dev` and `nightly` builds track Core `main`.
  - `release` builds MUST use the latest tagged release of `kutlusoy/elektron-net` and MUST NOT build from `main`. `fetch-core.sh` lists tags with `git ls-remote --tags`, keeps final version tags only (no pre-release suffixes such as `-rc`), sorts them by version and takes the highest. The tag is resolved to its commit SHA (the peeled commit for annotated tags). If no eligible tag exists the release build fails.
  - Tag signature: if upstream signs release tags, `fetch-core.sh` SHOULD verify the signature against a trusted key stored in this repository (**verify** whether upstream signs tags).
  - `CORE_REF` (branch, tag or SHA) overrides the policy for debugging and for reproducing an older build. A release build with an override is allowed only when the override is an existing release tag, so a published release always corresponds to a real upstream release.
- The build manifest records: Core repository URL, resolved commit SHA, commit date, patch set SHA-256, NDK version, builder image digest, SHA-256 of each produced `libelektrond.so`. The manifest is embedded in the APK and shown in the app's About/Node screen. This is what keeps "always current" auditable and reproducible after the fact.
- Third-party dependencies of Core (Boost, libevent and so on) are taken from the fetched Core's own `depends` definitions and their hashes, not re-specified here.
- **Contract check (build gate):** every build runs the contract check of §11.2 against a host build of the same revision. A failing contract fails the build with a message naming the missing or changed item, which turns upstream changes into immediate build errors instead of runtime surprises.
- **Chain-generation guard:** the contract check also reads the mainnet genesis hash from the binary and compares it with the expected one. If it differs from the one recorded in the app's datadir marker schema, the build still succeeds but the app's datadir versioning (§5.4) will trigger the controlled reset flow on first start.
- **Patch drift:** because the base moves on every build, a patch that no longer applies cleanly MUST fail the build loudly. It is never skipped or force-applied. Landing X1 upstream removes the only planned patch.
- Nightly CI builds against Core `main` and additionally against the latest release tag, so breaking upstream changes are visible before a release is cut.
- Consequence: a release contains the node of the latest upstream release at build time, never unreleased `main` code. A new upstream release flows into the next app release build automatically, so release builds MUST be cut only from a green contract check, and the tag name and resolved SHA are part of the release notes.

### 11.2 Contract check

`native-build/contract-check.py` verifies that an `elektrond` binary still offers what the app depends on. The expectations live in `native-build/contract/contract.json`. Whenever the app starts using another RPC or startup argument, it is added there in the same change.

- **Host build:** the Android binary cannot run on the build machine. `build.sh host` therefore builds a native `elektrond` of the same Core revision and patch set in the builder image, and the check runs against it inside the container. The RPC surface is identical because the source is identical. What it cannot cover is the Android runtime (bionic, process limits), which stays an emulator item in Phase 0.
- **Gate:** `build.sh` builds the host binary and runs the check before any Android ABI. A pass is cached by a stamp of Core commit, patches, builder files and contract files. `CONTRACT_CHECK=0` skips it for debugging, and release builds refuse that.
- **A. Startup arguments:** every argument in `node_args` is listed by `-help-debug`.
- **B. Mainnet, offline** (`-connect=0`, no DNS seed, no listening): chain `main`, genesis hash `00000006b054...9dad`, protocol version at least 70017, user agent contains `Elektron`, `pruned` true, required fields in `getblockchaininfo` and `getnetworkinfo`, and for every RPC in `rpc` that `help` knows it and lists the argument names the app passes. An unknown command is detected explicitly, because `help` answers with plain text instead of an error. RPCs in `optional_rpc` only warn.
- **C. Regtest, functional** (with `-fastprune -natpmp=0`): a wallet is built from a fixed test master key with both coin types (`1370h` active, legacy `0h` imported inactive). The check verifies `bcrt1q` addresses on coin type 1370 and a locked wallet after import, then mines 700 blocks and verifies that block files roll over and the oldest is deleted, `pruneheight` advances, and exactly one checkpoint snapshot `<height>-<hash>.dat` with a `.dat.hash` sidecar exists for the latest multiple of the regtest checkpoint interval (100). It runs the full send flow (`walletcreatefundedpsbt`, `decodepsbt`, `walletpassphrase`, `walletprocesspsbt`, `finalizepsbt`, `sendrawtransaction`, `walletlock`, confirmation). It runs the recovery flow of §8.3 and verifies that the rescan from `pruneheight` restores only the retained part. If `rescanutxoset` exists it must restore the full balance, otherwise the check warns. Finally it restarts the node without `-fastprune` and mines on, which verifies that toggling the option on an existing datadir is safe.
- Test nodes pre-create an empty `bitcoin.conf` (§4.2, item 7).
- **Result on `elektron-net@main` (`447c069`, October 18, 2026):** 101 checks passed in about 6 seconds, one warning (`rescanutxoset` not present).

## 12. Cross-Repo Impact

| ID | Repo | Item | Required? |
|---|---|---|---|
| X1 | `elektron-net` | Upstream a `rescanutxoset` wallet RPC wrapping `CWallet::ScanUTXOSet()`; remove the mobile-only patch afterwards. Also doc it in `BITCOIN_CORE_DIFF.md` | MUST, without it a restored wallet cannot see coins from pruned blocks (verified by the contract check) |
| X2 | `elektron-net` | If no clean switch exists: an option to stop advertising/serving `NODE_SNAPSHOT` (for example a `-snapshotserve=0` style flag, **verify existing args first**) | SHOULD |
| X3 | `elektron-net` | Persist snapshot download progress across restarts (audit §4.8, currently "accepted") so cellular and unstable mobile links do not re-download | MAY, strongly useful |
| X4 | `elektron-net` | Add an Android/NDK target (toolchain file and docs) so the mobile build does not carry a private fork of the build system | SHOULD |
| X5 | `elektron-net` | Wallet integration guideline: add the mobile full-node variant as a documented integration option and link this plan | MAY |
| X6 | `elektron-net-electrum` | Share address-safety wording, string translations and the dual coin type recovery behavior so both wallets stay consistent | SHOULD |
| X7 | `elektron-net-mempool` | Deep links from tx/address screens to the public explorer (opt-in, privacy warning, because it leaks the address to the explorer) | MAY |
| X8 | DNS seeder | Phone nodes will mostly be non-reachable. No change, but crawler statistics will show a larger share of unreachable peers | n/a |
| X9 | Pools/miners | None. Phone nodes do not mine in v1 | n/a |

## 13. Phased Roadmap

### Phase 0: Feasibility spikes (decision gates)

- [ ] Cross-compile `elektrond` for `aarch64-linux-android` and `x86_64`; run `-regtest` on an emulator
- [ ] Child-process survival test (screen off, memory pressure, OEM battery managers) and decision: forked child vs `:node` service process (§4.1)
- [ ] Foreground service type and distribution channel decision (§4.3)
- [ ] Measure retained-window size, chainstate size, snapshot size against the live chain
- [ ] First-sync benchmark (IBD path and snapshot path, depending on current chain height vs 197,280) on three reference devices (low, mid, high RAM)
- [ ] Power and data baseline per profile (§7)
- [ ] Verify every Core argument and RPC listed in this plan against the Core revision resolved at build time
- [x] Implement `fetch-core.sh` (§11.1)
- [x] Implement the contract check on a host build (§11.2). Still open: the same smoke test on an Android x86_64 emulator
- [ ] Measure `-fastprune` cost and benefit on a real device (§3.2) and decide the release default (§17, Q10)
- [ ] Decide `rescanutxoset` strategy (X1) and BIP-39 library approach (§2.2 #7)

**Exit:** go/no-go memo, updated profile table, updated storage numbers.

### Phase 1: Node host MVP

- [ ] Gradle project, signing config, CI
- [ ] Node supervisor (start/stop/drain/health/graceful stop)
- [ ] Keystore-wrapped RPC credentials, `-rpcauth` or cookie
- [ ] Sync dashboard with all phases from §5.1
- [ ] Datadir versioning (§5.4)
- [ ] Localization scaffolding from the first screen on: string resources only, per-app language, lint gate, five locales (§3.5)
- [ ] Wallet and Node sections with the node transparency elements of §3.4
- [ ] Snapshot bootstrap verified end to end on testnet

### Phase 2: Wallet core

- [ ] Create, backup quiz, restore (dual coin type, §8.2/8.3)
- [ ] Receive (QR, address list, labels)
- [ ] Send with PSBT review, fee policy re-check, coin control
- [ ] Recovery matrix passes (§14.2)

### Phase 3: Background operation and policy

- [ ] Foreground service, notification, Stop action
- [ ] Policy engine (network, battery, thermal, storage, schedule)
- [ ] Profiles and benchmark-driven tuning
- [ ] Snapshot serving settings (X2) and disk guard
- [ ] Optional boot start, guided battery-optimization flow
- [ ] Evaluate RPC over Unix socket (§4.4)

### Phase 4: Hardening and polish

- [ ] Bump fee / CPFP, address book, descriptor export, encrypted wallet file export
- [ ] Localization completion, accessibility
- [ ] Reproducible build, signed release pipeline, hash publication
- [ ] Native speaker review of the security and trust model texts in every shipped language (§3.5)
- [ ] External security review and fixes

### Phase 5: Public beta on mainnet with funds warning, then release candidate

- [ ] Test-phase telemetry-free feedback channel (Telegram @elektronnet, issue tracker)
- [ ] Release notes and trust model documentation published on the website

## 14. Test Strategy

### 14.1 Layers

1. **Unit:** argument builder, policy engine, fee derivation, PSBT validation, address validation (including `bc1`/Base58 rejection and warnings), descriptor construction against known vectors.
2. **Native:** Core's own unit/functional tests for snapshot, pruning and wallet recovery on the host build of the same fetched Core revision.
3. **Contract check (every build, §11.2) and emulator integration (CI):** x86_64 `elektrond` in `-regtest` with `-fastprune` (checkpoint depth 100 on regtest, so snapshot cycles are observable), wallet create/send/recover flows driven through the app's wallet layer.
4. **Localization:** pseudo-locale run, longest-locale screenshots, string key parity across locales (§3.5).
5. **Device lab (manual, release gate):** real arm64 phones, soak tests, interruption tests.

### 14.2 Recovery matrix (MUST pass before beta)

| Scenario | Expected |
|---|---|
| Fresh seed, new wallet | Zero balance, new addresses on coin type 1370 |
| Seed from a pre-4.0.4 wallet with funds on coin type 0 | Funds found via legacy descriptors |
| Seed with funds beyond index 999 | Found via range top-up |
| Funds in blocks already pruned | Current UTXOs recovered, history absent, honest UX text |
| Restore while node still syncing | "Waiting for sync", no false zero, auto re-scan after snapshot activation |
| Wrong passphrase, interrupted creation, app killed mid-bootstrap | Incomplete wallet removed or resumed, no partial state exposed |
| Datadir wiped (Clear storage) | Re-sync and recovery from seed restores balance |

### 14.3 Resilience

- Kill the node with SIGKILL during block connect, during snapshot write, during snapshot download. Chainstate must recover or the supervisor must offer the correct repair path.
- Airplane mode toggles, network type changes, storage nearly full, battery saver, thermal throttling.
- Long soak: 7 days in Balanced profile on two devices, tip lag and drain recorded.

## 15. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Android kills the native child process in background | Node unreliable | Phase 0 spike, fallback to `:node` service process, honest "best effort in background" UX |
| Battery drain perceived as unacceptable | Users uninstall | Profiles, charging-first default, measured budgets, easy Stop |
| Storage pressure on low-end devices | Node stalls | Storage floor, clear errors, size measurements published |
| Always-current Core breaks the build or the RPC contract | Failed builds, broken app | Contract check in the build (§11.2), upstream Android target (X4), nightly CI against Core `main`, failed contract blocks release |
| Private patches diverge from Core | Audit burden | Upstream X1, keep patch SHA in manifest |
| Translation errors in security texts | Users misread backup or fee warnings | Native speaker review gate (§3.5), machine translation only as draft |
| Third-party artwork in the UI (helmet in the reviewed screenshots) | Legal | Own or licensed artwork only (§17, Q13) |
| `-fastprune` is debug-only upstream | Option breaks or disappears | Contract check, supervisor passes it only when supported (§3.2) |
| Seed handling bugs in Kotlin bootstrap | Fund loss | Minimal audited BIP-39 code, test vectors, external review (S11) |
| Missed legacy coin type on recovery | Apparent fund loss | Dual scan as MUST, matrix test (§14.2) |
| Misdirected funds from Base58 collision with Bitcoin | Fund loss | Bech32-only default, warnings (§8.5) |
| Store policy limits (foreground service type, battery exemption permission) | Distribution constraints | Plan direct APK and F-Droid in parallel with Play |
| Datadir incompatibility after a protocol reset | User confusion | Datadir versioning and guided reset (§5.4) |
| Unknown licensing/attribution of reused concept code | Legal | Decide before any code is copied (§17, Q2) |

## 16. Checklist

- [ ] Phase 0 spikes completed and documented
- [ ] Open questions in §17 answered
- [ ] Native build fetches current Core on every build and records commit SHA and binary hash in the manifest
- [ ] Core contract check (§11.2) enforced in CI
- [ ] Node supervisor with graceful stop, drain and health classification
- [ ] Both bootstrap paths (IBD in window, snapshot) verified
- [ ] Wallet create/recover with dual coin type verified against real seeds
- [ ] PSBT review and fee re-check verified
- [ ] Bech32-only defaults and Base58 warnings implemented
- [ ] Background service with policy engine and user-visible stop
- [ ] Security requirements S1 to S11 verified
- [ ] All user-visible text localized in the five launch locales, lint gate green
- [ ] Wallet and Node sections implemented, live log with sanitized export
- [ ] Recovery matrix and resilience tests passed
- [ ] Upstream items X1 to X4 filed or resolved in `elektron-net`
- [ ] Release pipeline with signed artifacts and published hashes

## 17. Open Questions

1. Final application ID and package namespace: keep `org.elektronnet.mobile` or use a project-owned domain such as `org.elektron-net`-derived naming?
2. Origin and license of the reviewed concept implementation: may its code be reused in this repository, and under which attribution? If only the architecture is reused, the code is written fresh.
3. Biometric unlock: allow an opt-in Keystore-wrapped passphrase, or keep "passphrase never stored" as an absolute rule?
4. Should phone nodes serve snapshots by default (network health, `NODE_SNAPSHOT` supply) or only when charging and unmetered?
5. Distribution channels for v1: direct APK, F-Droid, Google Play, or a staged combination?
6. Is Lightning in scope after v1? Per `guideline-wallet-integration.md` it needs routing/trampoline infrastructure that does not exist yet and a different wallet stack than the Core descriptor wallet.
7. URI scheme for payment requests (BIP21-style `elektron:` or similar): not yet defined in the network documentation.
8. ~~Should release builds track Core `main` or the latest tagged release?~~ **Resolved in v0.3:** release builds use the latest tagged release, dev and nightly builds track `main` (§11.1).
9. Is a testnet build flavor (separate app ID, `tb`-style HRP handling) wanted from day one for safer public testing?
10. Should release builds enable `-fastprune` ("Storage saver") by default, or keep it as an opt-in setting? Decide after the Phase 0 measurements (§3.2).
11. Ticker spelling: the node and SLIP-44 registration use ELEK (`CURRENCY_UNIT` in the node), the reviewed screenshots show ELK. Which one does the app display?
12. Which languages beyond the five launch locales, and which translation workflow (the node repository contains a `.tx` directory from Core's Transifex setup, an alternative is a self-hosted tool or plain pull requests)?
13. Which artwork and branding assets may be used in the UI (the helmet artwork in the reviewed screenshots appears to be third-party material)?
