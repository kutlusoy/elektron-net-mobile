# Elektron Net: `elektron-net-mobile` Full Node Wallet Plan

- **Version:** 0.24 (draft)
- **Date:** October 19, 2026
- **Audience:** Android/Kotlin developers, C++ developers working on `elektrond` builds, release engineers, security reviewers
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/validation.cpp` (`WriteAutomaticSnapshot()`, `PopulateAndValidateSnapshot()`), `src/init.cpp` (`MaybeActivateAutomaticSnapshot()`, `UpdateLocalSnapshotServices()`), `src/net_processing.cpp` (`MaybeRequestSnapshot()`), `src/wallet/wallet.cpp` (`ScanUTXOSet()`, `CreditUTXOFromChain()`, `AttachChain()`), `src/wallet/walletutil.cpp` (`GenerateWalletDescriptor()`), `src/kernel/chainparams.cpp` (treat these as ground truth)
- **Related repositories:** [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (light wallet, source of branding, address safety UX and strings), [`elektron-net-mempool`](https://github.com/kutlusoy/elektron-net-mempool) (explorer, deep links only)
- **See also:** [`guideline-escrow-psbt-and-tools.md`](./guideline-escrow-psbt-and-tools.md), [`guideline-entropy-and-randomness.md`](./guideline-entropy-and-randomness.md), [`guideline-expert-console.md`](./guideline-expert-console.md), [`guideline-ui-design-system.md`](./guideline-ui-design-system.md), [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md), [`guideline-wallet-integration.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/guideline-wallet-integration.md), [`AUDIT_PRUNING_SNAPSHOT.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/AUDIT_PRUNING_SNAPSHOT.md), [`BITCOIN_CORE_DIFF.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/BITCOIN_CORE_DIFF.md), [`CHANGELOG-slip44-coin-type.md`](https://github.com/kutlusoy/elektron-net/blob/main/doc-elektron/CHANGELOG-slip44-coin-type.md), [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)
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

**Out of scope for v1 (tracked in §17):** Lightning, hardware signer support, iOS, Tor/I2P, multi-wallet UI (all three were added later; fiat prices are an opt-in setting, see the UX guideline).

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
9. Expert mode with a console to the node of the phone (§3.6).

### 3.2 Resource profiles

Profiles map to `elektrond` startup arguments. The numbers below are starting hypotheses to be replaced by Phase 0 benchmark results.

| Profile | When active | Intent | Arguments (starting point) |
|---|---|---|---|
| **Foreground** | App visible, no background service | Lowest footprint, catch up when opened | `-dbcache=64 -maxconnections=8 -maxmempool=30 -blocksonly=1` |
| **Balanced** (default) | Foreground service while charging or battery above threshold, on unmetered network | Keeps tip current without noticeable drain | `-dbcache=128 -maxconnections=125 -maxmempool=50` |
| **Always-on** | Foreground service permanently, user opt-in | Best relay/serving behavior | `-dbcache=256 -maxconnections=24 -maxmempool=100` |
| **Initial sync boost** | Only during first IBD or snapshot tail sync, device RAM permitting | Shorten the heaviest phase | Temporary `-dbcache` raised to a fraction of free RAM, then restart into the active profile |

- `-blocksonly` in the Foreground profile reduces bandwidth but also stops mempool-based unconfirmed-incoming display. The UI MUST say so.
- All profiles use `-listen=1` only when on unmetered networks. Inbound connections behind carrier NAT will be rare, so no effort is spent on port mapping in v1.
- Every argument above is a standard Core option inherited by the fork. **Verify** each one against `src/init.cpp` of the Core revision resolved at build time (§11.1).
- All profiles disable port mapping with `-natpmp=0` (the option exists in `elektron-net@main` and defaults to on, there is no `-upnp` option any more). A team test build logged repeated `pcp: Could not connect to gateway` warnings, which is noise and a pointless network probe on a phone.

**Storage saver (`-fastprune`).** `-fastprune` is a debug-category option of the node (listed by `-help-debug`, hidden from `-help`). In `src/node/blockstorage.cpp` it caps a `blk` file at 64 KiB instead of 128 MiB and shrinks the preallocation chunk to 16 KiB. Pruning deletes whole `blk`/`rev` files only, so the retention window is enforced with a granularity of one file: with default files, a node keeps up to one extra 128 MiB file beyond the 137-day window, and on a young low-traffic chain the oldest file can stay in place for a very long time. With `-fastprune` the retained data follows the window closely. It does not change consensus or chain parameters, it only changes block file sizes (verified in `elektron-net@main`, `447c069`).

- Costs to measure in Phase 0: far more files (block data size divided by 64 KiB, plus one `rev` file each), more open/close and finalize/truncate work, longer directory scans at startup, more inodes on the device.
- Upstream risk: a debug-only option can change or disappear while the app always tracks current Core (§11.1). The contract check (§11.2) verifies that the option is accepted and that it still makes pruning observable. The supervisor passes it only when the build manifest says it is supported.
- Control: `NODE_FASTPRUNE_DEFAULT` in `core-source.env` (default `1`, decided October 18, 2026) is recorded in the build manifest and sets the app default. The user can opt out with the setting "Storage saver", which overrides the default at runtime. Emulator and CI regtest runs always use `-fastprune`.
- Switching it on or off on an existing datadir is expected to be safe because block file sizes are tracked per file. The contract check restarts a regtest node with the option toggled to confirm this.
- **Decided:** the Storage saver is on by default in all builds, with an opt-out in settings. The Phase 0 measurements still run: if the costs listed above turn out to be unacceptable on real devices, the default is revisited, but the opt-out stays.

### 3.3 Sections and navigation

The app has two sections. Both are one tap away from each other at all times.

| Section | Screens |
|---|---|
| **Wallet** | Home (balance, UTXO count, addresses with balance, Receive/Send with QR scan), Receive and address list, Send, History, Security and backup |
| **Node** | Status, Network telemetry, Pruning and snapshots, Peers, Live log, Controls, Recovery tools, Expert console (opt-in, §3.6) |

- A compact node status chip (sync state, peer count) sits in the Wallet header and opens the Node section. While the node is not synchronized, the Wallet shows a banner that balances and history may be incomplete, instead of a bare zero.
- Primary navigation is a bottom bar. The reviewed team screenshots use four tabs (Wallet, Network, History, Debug). The final grouping into the two sections above is a design decision for Phase 1, the requirement is only that every Node screen is reachable without digging through settings.
- Visual direction: calm and flat, translucent rounded cards on a quiet texture, one hero card per screen, no glow. The app ships no artwork. Five color themes (Teal, Blue, Rust, Red, Matrix) derived from the Osclass themes of `elektron-net-wp`, each in a dark and a light mode, one readable system font, and a contrast gate in CI. The full specification is [`guideline-ui-design-system.md`](./guideline-ui-design-system.md). The helmet artwork in the reviewed screenshots MUST NOT be copied into this repository.

### 3.4 Node transparency

The user can see what the node is doing whenever they want. Everything in the Node section is read from the local node and shown only while visible.

| Element | Source |
|---|---|
| Sync hero card (phase, "Synchronized", validated blocks, known headers, progress) | `getblockchaininfo` (`blocks`, `headers`, `verificationprogress`, `initialblockdownload`), snapshot phase from the supervisor (§5.1) |
| Peers (in/out counts, P2P listen state) | `getnetworkinfo` (`connections_in`, `connections_out`, `localservices`, `networkactive`) |
| Peer list (address, direction, user agent, ping, height, country) | `getpeerinfo`, country from the local MMDB (§8.6) |
| Chain telemetry (chain, difficulty, hashrate, mempool, disk) | `getmininginfo`, `getnetworkhashps`, `getmempoolinfo`, `getblockchaininfo` (`size_on_disk`) |
| Chain events (next halving date and block reward before and after, blocks to halving, difficulty epoch progress, average block time, next retarget and the projected difficulty change) | `getblockchaininfo` (`blocks`, `bestblockhash`), `getblockheader` (tip and epoch start time), `getblockhash`; the rules (halving every 2,102,400 blocks, 5 ELEK start reward, 2016 block retarget, 60 s spacing, 4x clamp) are constants verified against `GetBlockSubsidy` and the chain parameters |
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
- **English is the default language** (decided October 18, 2026): `values/strings.xml` is English, every other locale is an overlay, and the app falls back to English whenever the system language is not shipped or a string is missing. The shipped locale list lives in `app/shipped-locales.txt` and always starts with `en`. `tools/check-locales.py` fails the build if English is not first, if `locales_config.xml` differs from the list, or if a shipped locale lacks keys.
- **Locales:** English (source) plus the most widespread languages, in two tiers. Tier A at launch: German, Spanish, French, Italian (the four already used by the reviewed concept). Tier B: Simplified Chinese (`zh-Hans`), Traditional Chinese (`zh-Hant`), Arabic (`ar`, right-to-left), Japanese, Korean, Hindi, Brazilian Portuguese, Russian, Turkish, Indonesian, Vietnamese. A language ships only after a native speaker has reviewed the security texts (below). Until then it stays out of the release, so a partial or machine-only translation never carries a seed warning.
- **Current status (October 19, 2026):** all fourteen overlays (de, es, fr, it, pt-BR, ru, tr, id, vi, zh, ja, ko, ar, hi) are in the tree and shipped in debug and test builds as first drafts. The native-speaker review of the seed backup warnings, the trust model text and the Base58 collision warning stays a release gate for every language; a language that has not passed it MUST be removed from `shipped-locales.txt` and `locales_config.xml` before a store release. Traditional Chinese is not yet included.
- **Scripts:** amounts, block heights and addresses always use Latin digits and the node's address alphabet in every language. Addresses, hashes and console text are isolated as left-to-right runs inside right-to-left text. Non-Latin scripts use the system fonts (details in the design system guideline, §5).
- **Translation workflow:** plain pull requests with string resource files, plus an optional self-hosted tool later. The `.tx` directory of the node repository is Core's Transifex setup and is not reused.
- **In-app language switch** independent of the system language (Settings, Language), for example for a Turkish speaker living in Austria. The choice is stored in the app and applied by wrapping the activity context (`attachBaseContext`) with the chosen locale on every Android version, then the activity is recreated. The list shows each language in its own name and is checked against `shipped-locales.txt` by a unit test. Default is "System language", which falls back to English when the system language is not shipped. The `localeConfig` resource stays for the system's per-app language setting.
- **Formatting:** amounts, dates, times and numbers use the active locale (the Spanish screenshot shows a decimal comma). The unit labels (ELEK/lep) are never translated or reformatted in a way that changes the value. Layouts MUST survive text expansion (German and French are typically 30 percent longer) and system font scaling, and MUST support right-to-left locales (Arabic is in tier B).
- **Errors:** RPC and node errors are mapped by error code to localized messages for the cases the app knows (wallet locked, insufficient funds, fee too low, node not synced, and so on). The raw node message is available as a secondary details line, in English.
- **Terminology:** a per-language glossary fixes how terms such as UTXO, mempool, peer, pruning, checkpoint and snapshot are translated or kept in English, so the Node section reads consistently.
- **Review:** the seed backup warnings, the trust model text (§9) and the Base58 collision warning (§8.5) MUST be reviewed by a native speaker for every shipped language before a release. Machine translation is acceptable only as a first draft.
- **Tests:** pseudo-localization build to catch clipped or hard-coded text, screenshot tests for the longest locale, and a check that every string key exists in every shipped locale.
- Translation files are the one place in this repository that is not English (they hold the target languages by design).

### 3.6 Expert mode and node console

Expert mode gives advanced users a console to the node, comparable to the debug console of the desktop wallet. The full specification is in [`guideline-expert-console.md`](./guideline-expert-console.md). The essentials:

- Part of all builds, off by default. Enabling it needs a localized risk confirmation and device authentication once per session, and the session ends with the app process or after inactivity.
- The console sends JSON-RPC to the local node through its own low-priority lane of the app's RPC client. The mobile build ships no `elektron-cli`, and none is needed.
- Every command belongs to a tier: **Open** (read-only, runs immediately), **Confirm** (state-changing, one confirmation), **Danger** (moves funds or reveals keys, ships in the first release behind a second switch that the user turns on with a typed confirmation, plus a confirmation dialog per command) and **Blocked** (`stop`, `loadtxoutset`, test commands). The classification is derived from the node's `help` list (151 commands on `elektron-net@main`) plus the known hidden commands and lives in `native-build/contract/console-tiers.json`. The app tracks the current Core, so any command that is not classified defaults to Confirm, and the contract check reports unclassified commands.
- The console screen uses `FLAG_SECURE`, passphrase arguments are masked and never persisted, and every wallet unlocked through the console is relocked when the user leaves the console or the app goes to the background.
- Command history is persistent, encrypted at rest, with secrets masked, a retention of 90 days by default (off, 7, 30 or 90 days selectable) and a delete action.
- `backupwallet`, `restorewallet` and `dumptxoutset` are supported. File arguments are confined to the app's private `exports/` directory, existing files are never overwritten, and only wallets protected by a passphrase are backed up. `dumptxoutset` with `type=rollback` is blocked. Dumps made by hand are deleted after 7 days, the automatic checkpoint snapshots of the node are never touched. `createwallet` is refused without a passphrase.
- Command names, arguments and node output stay in English. All console chrome, the risk texts and the error hints are localized (§3.5).
- Phase 1 ships a read-only developer console in debug builds for the team. User-facing Expert mode comes in Phase 3.

### 3.7 Node-only mode and server mode (planned, next milestone)

Status: the first step is built: the first-run screen offers "Skip for now (node only)", the choice is remembered, the Wallet and History tabs then show a card with a button to create or restore a wallet at any time, and the node runs and the notification permission is requested as usual. Server access, hashed RPC users and the mining readiness are still planned.

**Goal.** The app can run as a plain Elektron Net node on Android: start it without ever creating a wallet, and optionally make the node reachable from other machines on the same network, for example as the backend of a mining pool or a solo miner (`getblocktemplate` and `submitblock`, see `mining-pool-integration.md` of the node repository).

**Two independent switches.**

1. *App mode:* "Wallet and node" (today's default) or "Node only". The first-run screen gets a third choice next to Create and Restore: "Use as node only". The choice can be changed later (Settings, Node), and a wallet can be added at any time without touching the synchronized data. In node only mode the Wallet and History tabs are replaced by a short card that explains the mode and offers to create a wallet, the Node tab becomes the home screen, and the foreground service, policy engine, telemetry, peers and log work as they do today.
2. *Server access:* off by default. When on, the node accepts RPC connections from the local network. It is a separate section of the Node settings and works in both app modes.

**Node only mode, technical points.**

- The wallet gate in the app (`walletReady` in `AppRoot`, in `MainActivity` and `NodeService`) becomes an app mode value. Starting the node and the foreground service no longer depends on a wallet.
- The wallet stays enabled in the node in every mode, so a wallet can be created or restored at any time without a restart, and node only mode never becomes an obstacle. `-disablewallet` is not used. Without a wallet the node simply has none loaded and holds no key material.
- The notification permission request and the battery guidance run in the first-run flow of both modes.
- Mining oriented defaults are offered as a profile, not forced: keep running in the background, stay awake only while charging, Wi-Fi only, thermal limits stay active.

**Server access, technical points.**

- *Bind and allow list.* Levels: Off (`127.0.0.1` only, today), Local network (bind to the Wi-Fi or Ethernet address of the phone, never `0.0.0.0` by default, `-rpcallowip` restricted to the detected private subnet, user can narrow it to single addresses). No option binds to a public address. Remote access from outside is described as "use a VPN such as WireGuard", not offered by the app.
- *Credentials.* The local app keeps using the cookie file. For external clients the app creates one or more RPC users with `-rpcauth=user:salt$hash` (HMAC-SHA256 as in Core's `share/rpcauth`), generates a long random password, shows it once together with a ready connection string and QR code, and stores only the salted hash. No `-rpcpassword` in plain text anywhere. Users can be listed, renamed and revoked, and "Revoke all" removes access in one step. The mandatory empty `-rpcpassword=` override (see the node config test) MUST be verified together with `-rpcauth` in a spike, because the node's default configuration also sets `rpcuser` and `rpcpassword`.
- *Method whitelist.* External users get `-rpcwhitelist` entries and `-rpcwhitelistdefault=1`, so they can only call what their role needs. Roles: *Mining* (`getblocktemplate`, `submitblock`, `getmininginfo`, `getblockchaininfo`, `getbestblockhash`, `getblockcount`, `getblockhash`, `getblockheader`, `getnetworkinfo`, `getmempoolinfo`, `getrawmempool`, `getdifficulty`, `validateaddress`, `getnetworkhashps`), *Read only* (the non-wallet read calls of the Open tier of the console classification), *Full* (everything except wallet and Blocked tier commands, Danger confirmation needed). Wallet and key calls are never available to external users. The role lists are derived from `native-build/contract/console-tiers.json` and verified by the contract check.
- *Transport.* RPC is plain HTTP, so the app says clearly that it is meant for a trusted LAN. Optional later: an RPC onion service or a TLS front, not in the first step.
- *Enable flow.* Enabling needs device authentication, a risk text (what is exposed, who can reach it), and shows a persistent banner while server access is on. Optional auto-off timer (1 hour, 24 hours, never). The node screen shows the connection card: reachable address(es), port, users, last connection time.
- *Android networking.* The address changes with DHCP and Wi-Fi switches, so the card reads the current address live and the service re-binds on a network change (restart of the node RPC bind needs a node restart, shown as a short "applying" state). Optional local discovery through Android NSD (`_elektron-rpc._tcp`), off by default. Inbound P2P is a separate switch that already exists (Allow inbound connections); the card shows the inbound peer count and whether the P2P port looks reachable. UPnP and NAT-PMP stay off, port forwarding is the user's decision and is explained, not automated.
- *Mining readiness checklist.* Synchronized (not in initial download), enough peers, a `getblocktemplate` self-test succeeds with a throwaway coinbase address, UTXO attestation fields present in the template. The card also generates the settings block for `elektron-net-pool` or for a GBT miner (host, port, user), without the password. In-node CPU mining on the phone is not offered (SHA-256d, battery); `generatetoaddress` stays available to the console on regtest and testnet.
- *Persistence and logging.* The RPC users and the access level live in an app-private config include file that the app writes before every node start (not on the command line, so `ps` never shows them). RPC users are never logged, the cleaned log export (§8.9) hides addresses and paths and also drops `rpcauth` lines.

**Security requirements.** S26: server access is off by default, binds only to local addresses, and never to a public interface. S27: no plain text RPC password is stored, external users are hashed `rpcauth` users with a method whitelist, and wallet and key calls are never exposed. S28: enabling needs device authentication and an explicit risk confirmation, and a visible banner shows while it is on. S29: node only mode holds no key material until the user creates a wallet, and adding a wallet needs no node restart.

**Tests.** Argument builder and config include writer (unit), `rpcauth` hash against Core's reference vectors, an integration test with the real regtest node: external user reaches only the whitelisted calls (`getblockcount` yes, `stop` and `getwalletinfo` no), cookie access keeps working, revoked user is refused, bind address is the configured one, and node only mode starts and later switches to wallet mode on the same data folder. Device lab: Wi-Fi change, doze, screen off, charging only, thermal pause, 24 hour soak as a pool backend.

**Open questions.** N1: RPC over a Unix socket or onion for remote use. N2: foreground service type and Play policy wording for an app that exposes a local server (distribution guideline). N3: a separate "node only" flavor or APK, or only the mode switch. N4: IPv6 and dual stack. N5: whether the Full role is worth shipping in v1 or only Mining and Read only. N6: a dedicated pool health view (blocks submitted, template latency).

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
6. Authenticate RPC with the node's cookie file (`-rpccookiefile` in the app-private datadir, recreated by the node at every start, deleted by the supervisor before a start). No RPC password exists, so nothing needs to be stored or wrapped by the Keystore.
7. Own the config file. On first launch the node writes a default `bitcoin.conf` into the datadir with the mainnet seed and `addnode` list (observed with `elektron-net@main`). That is fine for the mainnet flavor and keeps the maintained peer list. Every other chain fails on the next start (`-addnode` only applied on regtest when in a `[regtest]` section), and passing `-conf=` elsewhere does not stop the file from being generated, which then fails with "contains a bitcoin.conf which is ignored" unless `-allowignoredconf=1` is set. Therefore any non-mainnet flavor and every test pre-creates its own `bitcoin.conf` in the datadir before the first start, and the supervisor checks that the file matches the chain before launching.

#### Supervisor implementation notes

The supervisor core is plain Kotlin without Android dependencies (`app/src/main/java/org/elektronnet/mobile/node/`), so it runs in JVM unit tests and in an integration test against a real `elektrond`.

- **Arguments** come from one function (`NodeConfig.arguments()`): datadir, `-server=1`, loopback `-rpcbind`/`-rpcallowip`, `-rpcport`, `-rpccookiefile`, `-port`, `-printtoconsole=1`, `-natpmp=0`, `-listen`, the chain argument (`-testnet`, none for mainnet), the profile arguments of §3.2 and `-fastprune`. All of them are in the contract file.
- **States:** Stopped, Starting, Running, Stopping, Crashed(exit code, failure class). The node is Running when `getblockchaininfo` answers. Error `-28` (RPC in warm-up) and connection errors keep the state at Starting, any other RPC error also counts as ready.
- **Stop:** RPC `stop`, wait up to a bounded timeout (default 60 s, long enough for a snapshot write), then SIGTERM, then SIGKILL as the last step. An exit that the app requested, or exit code 0, is Stopped, anything else is Crashed.
- **Output:** stdout and stderr are drained on their own thread into a bounded ring buffer, so a full pipe never blocks the node.
- **Failure classes** are taken from node messages found in the source: "Disk space is too low", "Corrupted block database"/"Error opening block database", "Cannot obtain a lock on directory". Everything else is Unknown. The recovery action per class (§4.2 item 4) is wired in with the UI.
- **Config file:** for non-mainnet chains the supervisor writes a managed `bitcoin.conf` and, if it finds settings outside the chain's own section (for example the generated mainnet `addnode` lines), replaces it and keeps a `bitcoin.conf.bak` (§4.2 item 7).
- **Verified:** 61 JVM tests in total (see also §7 and §4.3): JSON, arguments, config file, failure classes, ring buffer, RPC client against a local HTTP server, supervisor against fake node scripts, the policy engine, and three integration tests against the host `elektrond` in regtest (supervisor start, query and graceful stop; controller applying policy decisions including a profile restart; runtime tick loop). Not yet verified: the same on Android (bionic, process limits, phantom process killer), which stays a Phase 0 item.

### 4.3 Foreground service and background execution

#### Foreground service implementation notes

The Android layer is thin and sits on the tested core: `NodeService` (foreground service), `NodeNotification`, `NodeHost` (process singleton holding controller, policy engine and runtime), `AndroidDeviceState` (battery, thermal status, network type, data saver, free storage) and `SettingsStore` (policy settings, data budget). **Written but not yet compiled or run on a device**: the sandbox has no Android toolchain, so the first Gradle build and a device test are open (Phase 0 and Phase 1 items).

- **Type:** `specialUse` with the required property text in the manifest, because `dataSync` has runtime limits on recent Android versions. Whether Play accepts it for a node is open question D5.
- **Loop:** one scheduled worker runs `NodeHost.tick()` (policy decision, node control, status poll) and reschedules itself: 1 s while the node starts or stops, 2 s with the app visible, 30 s in the background, 60 s once synchronized. An exception in a tick is retried after 30 s and does not end the service.
- **Notification:** low importance, ongoing, hidden on the lock screen, with the headline (stopped, starting, syncing with block and peer counts, synchronized, paused with the first reason, crashed) and the actions Pause or Resume and Stop. All texts are string resources.
- **Stop:** the Stop action sets `userStopped`, runs one tick (the policy then stops the node gracefully, §4.1), removes the notification and ends the service. Opening the app does not restart a node that the user stopped, the Start button does. When the system destroys the service, the node is shut down on a separate thread so the main thread is never blocked.
- **Permissions:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` (requested before the first start on Android 13 and later), `ACCESS_NETWORK_STATE`. No boot receiver, boot start stays opt-in and unimplemented.

#### Requirements

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
- Serving snapshots: a node holding `.dat` + `.hash` advertises `NODE_SNAPSHOT` automatically and uploads chunks to peers. On a phone this is a bandwidth cost. Decided October 18, 2026: phone nodes serve snapshots **only on Wi-Fi (unmetered) and while charging**, and never otherwise. The app MUST enforce this and expose a setting to turn serving off entirely.
  - Clean solution: a Core option or RPC to stop advertising `NODE_SNAPSHOT` at runtime (X2, now MUST).
  - Interim workaround, **to verify in Phase 0**: while the condition is not met the supervisor moves the `<height>-<hash>.dat` and `.dat.hash` pair from `snapshots/` to a hold directory in the datadir, and moves it back when Wi-Fi and charging apply again. Core's `UpdateLocalSnapshotServices()` has to be checked to confirm that the service bit follows the directory content, that a missing file pair does not trigger a rewrite loop and that the move cannot race with `WriteAutomaticSnapshot()`. The move is done only with the pair complete and hash-verified afterwards, and never during a snapshot write.
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
- **Policy engine** (`org.elektronnet.mobile.policy`, plain Kotlin, 19 unit tests): `PolicyEngine.evaluate(settings, device state, time)` returns one decision with `run`, `networkActive`, `profile`, `listen`, `serveSnapshots` and the reasons that explain it for the UI.
  - **Run** is false for a user stop, for free storage below the floor (default 2 GiB, resumes 256 MiB above it) and for disabled background operation while the app is hidden. A stopped node uses no network.
  - **Network active** (applied with `-networkactive` at start and `setnetworkactive` afterwards, both in the contract) is false for a user pause, no network, metered or roaming networks without consent, data saver, a reached monthly cellular cap, charging-only mode on battery, a low battery, severe thermal state and times outside the schedule. Pausing the network keeps the process and the chainstate warm and avoids restarts.
  - **Battery:** below 30 percent on battery the reduced profile (the Foreground arguments) is used, below 15 percent networking pauses. Resume at 20 and full profile at 35 percent (hysteresis). Charging clears both. **Thermal:** moderate switches to the reduced profile, severe pauses, light or better recovers.
  - **Listening** is on only on unmetered networks (and when inbound connections are allowed). **Snapshot serving** is allowed only on an unmetered network while charging with the network active, as decided in §5.2. The decision flag is produced and tested here, the enforcement (X2 or the hold-directory workaround) is still open.
  - **Restart control:** a change of profile or listen mode restarts the node, so such changes are held back for at least 10 minutes while the node runs.
  - **Cellular budget:** `getnettotals` deltas are counted per calendar month only while the active network is metered or roaming, and persisted across restarts. This is an approximation (the counter is per node, not per interface), which is enough for a user-set cap.
  - **Not yet in the engine:** an idle-stop timer for long pauses, the initial sync boost profile, and a snapshot-in-progress guard that delays a stop.
- Phase 0 benchmark metrics: mAh per day in each profile, MB per day down/up, sustained CPU, peak RSS, time to first sync on three reference devices. Profiles in §3.2 are adjusted from this data, not from guesses.

## 8. Wallet Design

### 8.1 Key custody

- The node's descriptor wallet holds and uses the private keys. Kotlin touches key material only during creation/recovery bootstrap.
- Mnemonic and seed are never written to preferences, files, logs or crash reports. Use `ByteArray`/`CharArray` and explicit zeroing, accept that the JVM cannot guarantee full erasure, and keep the exposure window to the creation screen.
- Randomness: a phrase has at least 128 bits of true entropy from the health-checked system generators or from the user's own dice, with no weaker fallback (`guideline-entropy-and-randomness.md`, S17).
- Wallet passphrase is chosen by the user, handled only by Core (`encryptwallet`/`walletpassphrase`). By default the app does not store it.
- **Biometric unlock (decided October 18, 2026: possible, opt-in, off by default).** The user can enable unlock by fingerprint or face in the security settings.
  - Enabling requires the passphrase once. The app wraps it with an AES-GCM key in the Android Keystore (hardware backed where available, StrongBox if present) that requires user authentication for every use (`setUserAuthenticationRequired(true)`, per-use validity, `BIOMETRIC_STRONG` only, no device credential fallback for the wrapped key).
  - Unlock uses `BiometricPrompt` with a `CryptoObject`, so the key is released only after a successful biometric check. The decrypted passphrase lives in a `CharArray`, is used for one `walletpassphrase` call (at most 10 s, S4) and is zeroed immediately.
  - The key is invalidated when a new fingerprint or face is enrolled (`setInvalidatedByBiometricEnrollment(true)`). The app then falls back to the passphrase and offers to re-enable.
  - The passphrase always works. Biometrics are a convenience, never the only way in. Disabling the feature deletes the Keystore key and the wrapped blob.
  - The wrapped blob is in app-private storage, excluded from backup (S2). Sending still goes through the PSBT review screen, the biometric check replaces only typing the passphrase.
  - Trade-off to state in the UI: anyone who can make the device accept their biometric can spend. Large balances should keep the feature off. Requirement S16.

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
- **Order changed (October 2026):** wallet creation and restore no longer wait for the node. The wallet is created as soon as the node answers (start-up phase counts as waiting), and for a restore the rescan steps 4 and 5 run afterwards when the node has caught up. The abort in step 1 for a missing `rescanutxoset` is replaced by a partial restore with a clear message, so a user can always create or restore a wallet at once.
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
- **Coin control (built):** the Addresses and coins page lists every coin with a Frozen switch (`lockunspent` with `persistent=true`, so frozen coins survive a restart and are skipped by automatic selection). In the Send amount step "Choose coins" restricts the payment to the chosen coins (`walletcreatefundedpsbt` with `inputs` and `add_inputs=false`). The review checks that the PSBT spends exactly the chosen coins, "Max" spends exactly those coins, and a too small selection fails instead of pulling in other coins.
- **Fee bump (built):** payments are replaceable by default. For an own pending payment the transaction details offer "Speed up": `psbtbumpfee`, then the same checks as for a new payment (every non-change output unchanged, the fee only grows, the new rate is within the tolerance), one unlock of at most 10 s, sign, finalize, broadcast. CPFP for incoming payments is still open.
- **Fee from the amount (built):** an optional switch in the fee step subtracts the fee from the entered amount (`subtractFeeFromOutputs`). The recipient then gets the amount minus the fee and the wallet pays exactly the entered amount. The review names the recipient's share, and the PSBT check expects exactly that share. "Max" always subtracts the fee.
- **Fee suggestions:** the node's `estimatesmartfee` comes first. When it has no data for a level (young chain), the configured block explorer fills the missing levels (§8.7). The node's relay and mempool minimum remain the floor, and the app never changes a fee the user has reviewed.
- Because the node is local, broadcast reaches the P2P network directly and is not routed through a server. Nothing is leaked to an indexer.

### 8.4.1 Payment URI (`elek:`)

Decided October 18, 2026: the scheme is `elek:`. Testnet uses `elektest:` (see the distribution guideline).

```text
elek:<address>[?amount=<decimal ELEK>][&label=<text>][&message=<text>]
```

- BIP21-style. `amount` is a decimal in ELEK with at most 8 decimals, no exponent, no sign, never in lep. Invalid amounts reject the whole URI.
- Parameters starting with `req-` that the app does not know make the URI invalid. Other unknown parameters are ignored.
- `label` and `message` are untrusted display text. They are length limited, percent-decoded once, stripped of control characters and Unicode bidirectional controls, and shown as plain text.
- Generated links are always lowercase (scheme and Bech32 address), also in QR codes, because uppercase links are not understood by many scanners and wallets. Parsing accepts any case. A note typed in Receive is sent as `message`, the amount is always in ELEK whatever the display unit is.
- The address must pass the same checks as a manually entered one (§8.5): Bech32 `be1...` is accepted, a Base58 address triggers the collision warning.
- A URI never sends anything by itself. It only fills the Send screen (pasting a link into the recipient field, scanning it or opening it fills address, amount and note alike), which goes through the full PSBT review (§8.4). QR codes in uppercase (alphanumeric mode) are accepted. The QR scanner and the intent filter feed the same parser.
- The parameter name `lightning` is reserved for a future invoice fallback (§8.4.2) and is ignored today.
- The parser lives in one pure Kotlin class with unit tests and a fuzz test, and its test vectors are shared with `elektron-net-electrum` (X10).

### 8.4.2 Lightning readiness

Lightning is not in v1 and no routing infrastructure exists yet. Decided: keep the path open and design as if the infrastructure existed, so that anyone in the community can build it.

- The wallet layer talks to a `PaymentBackend` interface with an on-chain implementation (Core descriptor wallet). A Lightning backend can be added without touching screens. A feature flag keeps it disabled and absent from the UI.
- Reserve in the UI model: a channel and balance concept next to the on-chain balance, an invoice input in the Send screen, and the `lightning` URI parameter.
- Reserve the Bech32 invoice prefix `lnbe` for Elektron Net invoices (to be confirmed against the lightning deployment guidelines in `elektron-net`).
- Funding and closing use PSBTs from the Core wallet, so channel funds also pass the review-before-sign flow.
- Lightning keys are separate from the on-chain keys. Because the app does not keep the recovery phrase after setup, a Lightning backend needs its own seed backup flow or a derivation that is shown once. This is a design item for the Lightning phase, not for v1.
- The node must not rely on anything that blocks this path: no hard-coded single-address assumptions in the wallet layer, and an app-level extension point for a Lightning daemon as a second child process under the same supervisor.
- Reference: the Lightning deployment guidelines in [`elektron-net`](https://github.com/kutlusoy/elektron-net/tree/main/doc-elektron).

### 8.5 Address safety (MUST)

Base58 prefixes equal Bitcoin mainnet's (`guideline-wallet-integration.md` §4.4). Therefore:

- Only Bech32 `be1...` receive addresses are generated and shown by default.
- Pasting or scanning a legacy Base58 address or importing a WIF key shows an explicit warning that the same string is valid on Bitcoin mainnet.
- Addresses with a foreign HRP are rejected with a specific message. The mainnet flavor rejects `bc`, `tb` and `bcrt`. The testnet flavor (HRP `tb`) rejects `bc`, `be` and `bcrt`.
- The `elektron-net-electrum` implementation of this UX is the reference for wording and behavior to keep both wallets consistent.

### 8.5.1 Advanced tools

Escrow (multisig wallets with M of N approvals), PSBT handling (check, sign, combine, broadcast), key sweep and message signing are specified in [`guideline-escrow-psbt-and-tools.md`](./guideline-escrow-psbt-and-tools.md). They build on standard node features and add requirements S18 to S22. Imported single private keys are deliberately not supported, because they are not covered by the recovery phrase.

### 8.6 Local data, labels and privacy

- Address labels are stored in the node wallet (`setlabel`) and shown in the coin and address lists. The address book is described in §8.9. Nothing is sent anywhere.
- GeoIP for peer country uses a local MMDB, no remote lookup.
- No analytics SDKs, no crash reporting that transmits data by default.

### 8.6.1 No address reuse (default)

- Every wallet is created with the node's `avoid_reuse` flag (`createwallet avoid_reuse=true`). Wallets created by earlier versions get the flag on first use (`setwalletflag`, checked before every send and when the receive screen opens).
- Receiving: the receive screen shows one address and switches to a fresh one as soon as it has received a payment. A new address can be requested at any time. Change always goes to a fresh internal address (descriptor wallet behavior).
- Sending: once an address has been spent from, it is marked used. Coins that arrive later on such an address are listed separately (`used` in `getbalances`, `reused` in `listunspent`), are not part of the spendable balance and are never picked by automatic selection or "send max". The wallet details show their sum with an explanation, the Coins list tags them, and the user can still spend them deliberately by selecting them.
- Spending order: when a payment is made without manual coin selection, the wallet first tries the smallest group of coins on a single reused address that covers the amount. The fee comes from the same coins, and the change goes to a fresh address, so the reused address is emptied and closed at once. If no such group is large enough, normal selection applies and reused coins stay out of it.
- Effect: payments cannot be linked through a shared address, and the partial-spend leak (one address funding several inputs) cannot happen unnoticed. Tested against a real node (`CoinControlIntegrationTest`).

### 8.7 Block explorer (optional web requests)

- The app can talk to one or more block explorers of the Elektron Net mempool software. The default is `https://mempool.elektron-net.org`. In Settings the user can add, edit, reorder and remove explorers (at most five, https only, plain http only for `localhost` and `.onion`). An empty list means the app makes no web request at all.
- **What is requested:** only `GET /api/v1/fees/precise` (decimal fee rates in lep/vB), falling back to `/api/v1/fees/recommended`, with a 5 second timeout and a 16 KB response limit, cached for 60 seconds. The first explorer that answers wins. Rates outside 0 to 10,000 lep/vB are discarded.
- **What is never sent:** addresses, keys, balances, transactions. The explorer can see the phone's IP address, which the Settings text says. The explorer is also used to build the "Open in explorer" link (`<explorer>/tx/<txid>`), which opens in the browser only when the user taps it.
- The explorer never replaces node data: it only fills fee levels the node cannot estimate.

### 8.8 Several wallets and removing a wallet

- The app keeps a list of its own wallets in the app preferences (name of the node wallet file and a user label). The first wallet is `elektron`, further ones `elektron_2`, `elektron_3` and so on. One wallet is active and used for balance, history, Receive, Send, coin control, signing and sweeping. Escrow wallets stay separate (`guideline-escrow-psbt-and-tools.md`).
- Every wallet has its own recovery phrase, passphrase, descriptors and receive address. Adding a wallet uses the same create or restore flow as the first run, without the network and notification steps, and the same deferred setup that waits for the node (§8.3).
- **Removing an unused wallet** (no transactions and no funds, checked with `getwalletinfo` and `getbalances`) is also possible without the passphrase, for example after a mistyped passphrase at creation. Used wallets always need the passphrase.
- **The list follows the node.** Once per node start the app compares its wallet list with the wallet folder of the node (`listwalletdir`): entries without a wallet on the node are dropped, and listed wallets that are not loaded are loaded. Wallets that the node has but the list lacks are never added automatically: the Wallets screen has a "Reload wallets" button that adds wallets named `elektron` or `elektron_<n>`, drops stale entries, loads unloaded ones and reports the counts. A wallet whose setup is still running is never dropped. New wallets take the first free name, checked against both the list and the node folder. Asking for the active wallet never creates an entry. Removing a wallet that no longer exists on the node needs no passphrase and only cleans the list.
- **Taken names and unloaded wallets.** If creating a wallet fails because the node already has a wallet of that name (for example a leftover of an earlier attempt), the setup takes the next free name, keeps the label and tries again, instead of failing with "a wallet already exists". A wallet that is on the node's disk but not loaded is loaded the first time a call needs it, so Receive, balance and history work for it. Cancelling a failed setup drops its list entry when the node has no such wallet. Opening the Wallets screen and switching the wallet compares the list with the node again.
- **Coins are re-read after a restore and after every node start.** When the node is in sync, the app calls `rescanutxoset` for every wallet once per start and for a freshly restored wallet. A node before v4.0.6 loses coins that were recovered from the UTXO set when it reloads a wallet file (the partial transaction was stored with a position that reads back as conflicted), so a restored or restarted wallet would show 0 until this scan runs. On v4.0.6 the call is harmless.
- **Restoring from a wallet file:** Wallets (and the Wallets screen) offer "Restore from wallet file". The system file picker hands over a `wallet.dat` backup, the app stages it in its private folder and calls `restorewallet` under the next free wallet name (an existing name is never overwritten). A backup that is already encrypted is added at once. A backup without a passphrase gets one first: the app asks for a new passphrase (at least 8 characters, repeated), calls `encryptwallet`, and only then registers the wallet. Cancelling unloads the wallet and deletes its files. A file that is not a wallet is rejected and leaves nothing behind. On a pruned node the restored wallet finds its old coins through the UTXO scan (`fix-report-utxo-recovery-after-pruning.md`).
- **Removing a wallet** needs the wallet's own passphrase and a confirmation that the user has its recovery phrase. The app checks the passphrase with a short unlock, unloads the wallet with `load_on_startup=false` and deletes its directory. Removing the last wallet returns the app to the first-run screens.

### 8.10 Units and amounts

- Display units (Settings, Unit for amounts): ELEK (default), mELEK (milli-Elektron), bit (micro-Elektron) and lepton. The factors are fixed: 1 ELEK = 1,000 mELEK = 1,000,000 bit = 100,000,000 lepton. Internally everything is an integer number of lepton. The decimals are 8, 5, 2 and 0, so every amount is exact in every unit.
- The chosen unit applies to balances, history, amounts in forms and input fields (the field label names the unit). Fees stay in lep/vB. CSV export and payment links always use ELEK.
- To prevent unit mistakes the Send screen shows the amount again in ELEK below the input and in the review whenever the unit is not ELEK.
- Input accepts only as many decimals as the unit allows and refuses everything else.

### 8.9 Wallet tools

- **CPFP:** for a payment that is still waiting in the mempool and pays to the wallet, the user can send a child payment that spends one of the waiting coins back to a fresh own address. The child fee is computed so that the whole package reaches the chosen target rate (`getmempoolentry` ancestor fee and size, 111 vB for the child), the PSBT is created with exact amounts, reviewed (one input, one own output, fee equal to the plan), signed under one short unlock and broadcast. RBF bump (§8.4) remains the tool for the sender side.
- **Address book:** contacts (name and address, up to 500) live in the app's private storage in `contacts.json`, are shared by all wallets, never reach the node and are validated with the same address check as the send screen. The send screen offers a picker.
- **Public descriptor export:** `listdescriptors` without private keys, shared as text. The export refuses to produce text if any descriptor contains private key material.
- **Encrypted wallet file:** `backupwallet` writes into the private `exports/wallets/` folder and the user copies it out through the Storage Access Framework. It is only offered for passphrase-protected wallets, the temporary copy is deleted right after saving and housekeeping removes leftovers after 7 days.
- **Cleaned node log:** copy and share of the node log pass through a sanitizer that replaces IP addresses, onion and I2P names, wallet addresses and device paths.
- **Chain data reset:** removes blocks, chainstate, indexes, peers, mempool and fee estimates from the data folder and restarts the sync. Wallet files, `datadir.version` and the configuration stay.
- **Incoming payment notification:** off by default. The service compares the newest transactions of the active wallet with the ones seen before (the first run only records them) and posts one notification with a hidden public version, so the amount does not show on the lock screen.

## 9. Security Requirements

| # | Requirement | Level |
|---|---|---|
| S1 | RPC bound to loopback (`-rpcbind=127.0.0.1`, `-rpcallowip=127.0.0.1`) with cookie authentication, the cookie file lives in the app-private datadir. No `-rpcuser` argument and no secret in `-rpcpassword`: the node writes a default `bitcoin.conf` with `rpcuser` and `rpcpassword` on its first start, which would disable the cookie, so the app passes an empty `-rpcpassword=` to keep cookie authentication | MUST |
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
| S12 | Expert mode is opt-in per session with a risk confirmation and device authentication. The console screen uses `FLAG_SECURE`, passphrase arguments are masked, and every wallet unlocked through the console is relocked when leaving it (`guideline-expert-console.md` §3, §6) | MUST |
| S13 | Console commands are classified (Open, Confirm, Danger, Blocked) and unclassified or hidden commands default to Confirm (`guideline-expert-console.md` §5) | MUST |
| S14 | Console file commands only use the app's private `exports/` directory, never overwrite an existing file, and wallet backups are made only for passphrase-protected wallets (`guideline-expert-console.md` §7) | MUST |
| S15 | Console history is encrypted at rest, masks secrets, never stores results of key-revealing commands, rotates after the retention period and can be deleted (`guideline-expert-console.md` §6) | MUST |
| S16 | Biometric unlock is opt-in, uses a Keystore AES-GCM key with per-use `BIOMETRIC_STRONG` authentication and `CryptoObject`, is invalidated on new biometric enrollment, never replaces the passphrase and is removable (§8.1). No other form of passphrase storage exists | MUST |
| S17 | Recovery phrases carry at least 128 bits of true entropy. Sources are the health-checked system generators or the user's dice (50 or more rolls for 12 words, 100 or more for 24), mixed with SHA-2. A failed health check creates no phrase, there is no fallback to a weaker source, and no weak randomness API appears in app code (`tools/check-rng.py`) | MUST |
| S18 | Escrow and PSBT screens block screenshots and overlay touches, use one unlock of at most 10 s per signature and always relock (`guideline-escrow-psbt-and-tools.md` §6) | MUST |
| S19 | Every signature of an imported or shared PSBT is preceded by a check view of the exact file with own change and external outputs marked and unusual fees warned | MUST |
| S20 | Escrow setup shows a check code and the first deposit address and tells all participants to compare them before any deposit | MUST |
| S21 | Sweep keys are never stored, logged or shown again, and the destination is always an own new address | MUST |
| S22 | Single imported private keys are not added to the wallet | MUST |
| S23 | Web requests go only to explorers the user can see and edit in Settings, only over https (plain http for localhost and onion), carry no addresses, keys or balances, and stop completely when the list is empty (§8.7) | MUST |
| S24 | Removing a wallet needs that wallet's passphrase and an explicit confirmation, and never touches another wallet (§8.8) | MUST |
| S25 | Address reuse protection is on for every wallet; coins on a reused address are never spent automatically (§8.6.1) | MUST |
| S26 | Server access is off by default, binds only to local addresses and never to a public interface (§3.7) | MUST |
| S27 | No plain text RPC password is stored, external users are hashed `rpcauth` users with a method whitelist, wallet and key calls are never exposed (§3.7) | MUST |
| S28 | Enabling server access needs device authentication and an explicit risk confirmation, and a visible banner shows while it is on (§3.7) | MUST |
| S29 | Node only mode holds no key material until the user creates a wallet, and adding a wallet needs no node restart (§3.7) | MUST |

| S30 | Cleartext traffic is refused app wide except to `127.0.0.1`, `localhost` and onion hosts (network security config), so the local RPC works and no other http request can leave the app | MUST |
| S31 | The only outbound request not tied to an explorer is the update check (off by default, once a day while the app is open when switched on in Settings, or started by hand). It asks `api.github.com` for the releases of this repository, sends no identifiers and is limited to 1 MB of reply | MUST |
**Trust model disclosure text (in-app and docs):** the node verifies every block since its snapshot base or since genesis, enforces the per-block UTXO attestation, and checks a downloaded snapshot against the committed hash. It cannot validate reorgs deeper than the snapshot base, and a fresh snapshot bootstrap trusts the on-chain attestation committed by proof of work. This is the network's documented model, and the app must not describe it as "full archival verification".

## 10. Repository Layout (proposal)

```text
elektron-net-mobile/
  app/                                  Android application module
    src/main/java/org/elektronnet/mobile/
      node/        supervisor, controller, argument builder, RPC client
      policy/      policy engine (network, battery, thermal, storage)
      runtime/     tick loop and status headline
      service/     foreground service, notification, device state
      wallet/      bootstrap, send flow, recovery, descriptors
      security/    keystore, secure window helpers
      ui/          Compose screens
    src/main/jniLibs/{arm64-v8a,x86_64}/libelektrond.so
    src/main/res/                       strings (English default, plus the shipped locales, §3.5)
    shipped-locales.txt                 shipped locale list, English first
    src/mainnet/, src/tnet/             flavor overrides (ID, name, chain, scheme, icon)
  native-build/                         reproducible NDK build of elektrond
    core-source.env                     Core repository URL, ref to track, NDK version
    fetch-core.sh                       resolves and fetches the current elektron-net source
    build.sh, install-core.sh, pin-ndk.sh, patches/
    contract-check.py, contract/        interface check against the built node (§11.2)
    docker/                             builder image and in-container build script
    tests/                              tests of the build scripts
  design/                               palettes.json (five themes), check-contrast.py, gen-theme.py
  tools/                                check-locales.py
  build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml
  doc-elektron/                         guidelines and reports (this file)
  .github/workflows/                    CI
```

- Application IDs (decided): `org.elektronnet.mobile` (mainnet) and `org.elektronnet.mobiletestnet` (testnet flavor), see [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md).
- Language of code, comments and docs: English. UI strings: English plus the shipped translations (§3.5).
- Code comments follow the repo conventions: minimal, only for non-obvious reasoning.

## 11. Native Build Plan

- `elektron-net/depends/hosts` currently contains `darwin`, `freebsd`, `linux`, `mingw32`, `netbsd`, `openbsd` and no Android host. An NDK cross-build therefore needs its own toolchain file and dependency handling (Boost, libevent, sqlite or the wallet DB backend in use, zeromq disabled, no Qt, no GUI). First spike: build `elektrond` only, wallet enabled, `-DBUILD_GUI=OFF`, for `aarch64-linux-android` API 28 and `x86_64`. **Verify** the CMake options on the fetched Core revision.
- Output MUST be linked with 16 KB page-size compatibility (`-Wl,-z,max-page-size=16384`) to satisfy current Android and store requirements for native code.
- Build is containerized and rootless. The output ownership problem seen with root-owned Docker output in the concept is avoided by running the container as the invoking user.
- The Core source is not pinned to a fixed tag. See §11.1.
- One native build serves both app flavors. Every app build, local and CI, produces the mainnet and the testnet APK (distribution guideline §4).
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

### 11.1a Tor and I2P helpers

`native-build/fetch-helpers.sh` resolves, on every build, the newest stable release tag of Tor and of i2pd and checks out that exact commit (details and the decisions about the I2P router and the Tor repository in [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md) §7 and §8). `native-build/helpers/build-helpers-in-container.sh` builds zlib, OpenSSL, libevent and Boost with pinned checksums (`native-build/helpers-source.env`), then `libtor.so` and `libi2pd.so` per ABI with 16 KB page alignment. The result and its manifest (`assets/core-manifest/helpers-<abi>.env`) ship inside the APK. `HELPERS=0` skips them.

### 11.2 Contract check

`native-build/contract-check.py` verifies that an `elektrond` binary still offers what the app depends on. The expectations live in `native-build/contract/contract.json`. Whenever the app starts using another RPC or startup argument, it is added there in the same change.

- **Host build:** the Android binary cannot run on the build machine. `build.sh host` therefore builds a native `elektrond` of the same Core revision and patch set in the builder image, and the check runs against it inside the container. The RPC surface is identical because the source is identical. What it cannot cover is the Android runtime (bionic, process limits), which stays an emulator item in Phase 0.
- **Gate:** `build.sh` builds the host binary and runs the check before any Android ABI. A pass is cached by a stamp of Core commit, patches, builder files and contract files. `CONTRACT_CHECK=0` skips it for debugging, and release builds refuse that.
- **A. Startup arguments:** every argument in `node_args` is listed by `-help-debug`.
- **B. Mainnet, offline** (`-connect=0`, no DNS seed, no listening): chain `main`, genesis hash `00000006b054...9dad`, protocol version at least 70017, user agent contains `Elektron`, `pruned` true, required fields in `getblockchaininfo` and `getnetworkinfo`, and for every RPC in `rpc` that `help` knows it and lists the argument names the app passes. An unknown command is detected explicitly, because `help` answers with plain text instead of an error. RPCs in `optional_rpc` only warn.
- **C. Regtest, functional** (with `-fastprune -natpmp=0`): a wallet is built from a fixed test master key with both coin types (`1370h` active, legacy `0h` imported inactive). The check verifies `bcrt1q` addresses on coin type 1370 and a locked wallet after import, then mines 700 blocks and verifies that block files roll over and the oldest is deleted, `pruneheight` advances, and exactly one checkpoint snapshot `<height>-<hash>.dat` with a `.dat.hash` sidecar exists for the latest multiple of the regtest checkpoint interval (100). It runs the full send flow (`walletcreatefundedpsbt`, `decodepsbt`, `walletpassphrase`, `walletprocesspsbt`, `finalizepsbt`, `sendrawtransaction`, `walletlock`, confirmation). It runs the recovery flow of §8.3 and verifies that the rescan from `pruneheight` restores only the retained part. If `rescanutxoset` exists it must restore the full balance, otherwise the check warns. Finally it restarts the node without `-fastprune` and mines on, which verifies that toggling the option on an existing datadir is safe.
- Test nodes pre-create an empty `bitcoin.conf` (§4.2, item 7).
- **Result on `elektron-net@main` (`447c069`, October 18, 2026):** 144 checks passed in about 8 seconds, one warning (`rescanutxoset` not present). The check also covers the commands the console needs and the console classification (`native-build/contract/console-tiers.json`, see `guideline-expert-console.md` §5): every command of the node's `help` list is classified (a warning in dev and nightly builds, a failure in release builds with `--strict-tiers`), argument rules and safety invariants are enforced (for example `sendtoaddress` is at least Danger, `stop` and `loadtxoutset` are Blocked), the console guards are verified (`createwallet` needs a passphrase, backups only for encrypted wallets, file paths, auto delete), and the file commands are exercised (`getwalletinfo.unlocked_until` identifies passphrase-protected wallets, `backupwallet` and `restorewallet` round trip keeps encryption and balance, `dumptxoutset` with `type=latest` writes a snapshot).

### 11.3 Node v4.0.6 (branch `4.0.6` of `elektron-net`, checked October 20, 2026)

Verified on a host build of the branch with our patch set: the `rescanutxoset` patch applies unchanged (`wallet.cpp` is the same as on `main`), the contract check passes (151 of 151, strict tiers) and all 285 unit and integration tests pass against that binary.

| Change in 4.0.6 | Meaning for the app |
|---|---|
| **Consensus: PoW limit retarget overflow fix**, height gated, mainnet activation at block **500,000** (testnet immediately) | Every node of a user must run 4.0.6 or newer before the mainnet tip reaches 500,000 (at about 60 s per block roughly 5 months after the tip was near 273,000), otherwise it follows the old rules and forks off. Release builds MUST ship 4.0.6 or newer well before that. The app should tell users with an older node build to update (update check, Settings). |
| Snapshot restart self-heal: a node that has lost its usable chain data restarts itself once with `-reindex` (`execv`, same PID, one-shot marker file) | Good for phones, where an interrupted snapshot is likely. The supervisor sees the same process, the wallet files are untouched. The guided chain data reset (§8.9) stays as the manual way out. |
| Default config file is now `elektron.conf` for fresh datadirs, an existing `bitcoin.conf` is preferred, RPC server is commented out in the generated template | No effect: the app creates `bitcoin.conf` itself before the first start and passes `-server=1`, the loopback bind, the cookie file and the empty `-rpcpassword=` explicitly. |
| macOS datadir note, documentation | None. |

The D1 and D2 defects of the UTXO recovery (`fix-report-utxo-recovery-after-pruning.md`, X11) are fixed on `elektron-net` branch `4.0.6` (wallet code only, activation height 500000 unchanged). They stay open for any older node source until `v4.0.6` is released.

**How to use it:** until the tag `v4.0.6` exists, run the release workflow by hand with `build_type` dev or nightly and `core_ref` `4.0.6`. Once the tag is pushed, tag the app `v4.0.6-m0.1.0` (version code 400060100, checked by `tools/version.py --selftest`). The release workflow then builds node `v4.0.6` through the normal tag flow (§11.1), the build patch `rescanutxoset.py` is a no-op on it (verified), and the contract check passes 151 of 151.

**Running the same app on v4.0.5:** the app only talks JSON-RPC, so a build from tag `v4.0.5-m0.1.0` works. The build patch then adds `rescanutxoset` and the D1 fix (verified to apply on `v4.0.5`). D2 stays open there: balances are right after a restore, but coins recovered only from the UTXO set cannot be spent through the normal send flow. A v4.0.5 node also forks off at mainnet height 500,000, so it is only a bridge.

**Release checklist for v4.0.6:**

- [ ] Tag `v4.0.6` in `elektron-net`
- [ ] Set `app/mobile-version.txt` as intended and tag the app `v4.0.6-m<wallet version>`
- [ ] Run the release workflow and confirm the manifest shows `v4.0.6` and the contract check passes
- [ ] Install on a device and run a seed restore on a pruned node

## 12. Cross-Repo Impact

| ID | Repo | Item | Required? |
|---|---|---|---|
| X1 | `elektron-net` | Upstream a `rescanutxoset` wallet RPC wrapping `CWallet::ScanUTXOSet()`; remove the mobile-only patch afterwards. Also doc it in `BITCOIN_CORE_DIFF.md` | MUST, without it a restored wallet cannot see coins from pruned blocks (verified by the contract check) |
| X11 | `elektron-net` | Key coins recovered by `ScanUTXOSet()` by their real outpoint instead of a fabricated txid. Done on `4.0.6`, including the `rescanutxoset` RPC (X1); the build patch becomes a no-op. Details and reproduction: [`fix-report-utxo-recovery-after-pruning.md`](./fix-report-utxo-recovery-after-pruning.md) | MUST, fixed on `elektron-net` branch `4.0.6` (open until `v4.0.6` is released and the release workflow uses it) |
| X2 | `elektron-net` | A clean runtime switch to stop advertising and serving `NODE_SNAPSHOT` (for example a `-snapshotserve=0` flag or an RPC, **verify existing args first**), needed because phones serve only on Wi-Fi while charging | MUST (the hold-directory workaround in §5.2 is the interim) |
| X3 | `elektron-net` | Persist snapshot download progress across restarts (audit §4.8, currently "accepted") so cellular and unstable mobile links do not re-download | MAY, strongly useful |
| X4 | `elektron-net` | Add an Android/NDK target (toolchain file and docs) so the mobile build does not carry a private fork of the build system | SHOULD |
| X5 | `elektron-net` | Wallet integration guideline: add the mobile full-node variant as a documented integration option and link this plan | MAY |
| X6 | `elektron-net-electrum` | Share address-safety wording, string translations and the dual coin type recovery behavior so both wallets stay consistent | SHOULD |
| X7 | `elektron-net-mempool` | Deep links from tx/address screens to the public explorer (opt-in, privacy warning, because it leaks the address to the explorer) | MAY |
| X8 | DNS seeder | Phone nodes will mostly be non-reachable. No change, but crawler statistics will show a larger share of unreachable peers | n/a |
| X9 | Pools/miners | None. Phone nodes do not mine in v1 | n/a |
| X10 | `elektron-net-electrum` | Adopt the `elek:` payment URI scheme (§8.4.1) so both wallets read and write the same links | SHOULD |
| X11 | `elektron-net` | Document `elek:` and the testnet scheme in the wallet integration guideline | MAY |

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
- [ ] Measure `-fastprune` cost and benefit on a real device (§3.2) and confirm that the default-on decision holds (§17, Q10)
- [ ] Decide `rescanutxoset` strategy (X1) and BIP-39 library approach (§2.2 #7)

**Exit:** go/no-go memo, updated profile table, updated storage numbers.

### Phase 1: Node host MVP

- [x] Gradle project skeleton: flavors, theme generator, contrast and locale gates, CI workflow. Debug build, unit tests and lint of both flavors are green in CI
- [x] First green Gradle build. The release workflow builds both APKs, signs them with the release key when the secrets exist and otherwise with a temporary test key. Creating the real release key and secrets is the maintainer's task
- [x] Node supervisor core (start/stop/drain/readiness/graceful stop/failure classes, start failures reported with details), verified against the host node in regtest
- [x] RPC with cookie authentication (no stored credentials). The node writes a default `bitcoin.conf` with `rpcuser` and `rpcpassword` on its first start, which would disable the cookie, so the app passes an empty `-rpcpassword=`
- [x] Sync dashboard: start phases from the node's warm-up message and log, progress bar with estimate, peers with country, network and telemetry cards, live log, warnings when the node is silent or finds no peers (§5.1 in part)
- [x] Datadir versioning (§5.4): schema marker that refuses data from a newer app. The guided reset is still open
- [ ] Localization: string resources only, parity and hard-coded-string gates are in place. German, Spanish, French and Italian translations are still open (§3.5)
- [x] Wallet and Node sections with the node transparency elements of §3.4
- [x] Console: built as the full Expert console of Phase 3 instead of a debug-only one (§3.6)
- [x] Snapshot bootstrap verified end to end on mainnet on a real phone: headers, snapshot activation and the tip were reached. Testnet is still open

### Phase 2: Wallet core

- [x] Create, backup quiz, restore (dual coin type, §8.2/8.3), with a deferred setup that works while the node is still syncing
- [x] Receive (QR, address list, labels)
- [x] Send with PSBT review, fee policy re-check, coin control, explorer fee fallback
- [x] Several wallets with switching, adding and removing (§8.8)
- [x] Escrow, PSBT tools, sweep keys, sign and verify messages
- [ ] Recovery spend of coins that are only in the UTXO set (PSBT path with real outpoints, `fix-report-utxo-recovery-after-pruning.md`)
- [ ] Recovery matrix passes (§14.2): create, restore and the UTXO rescan are tested on regtest, the device matrix is open

### Phase 3: Background operation and policy

- [x] Foreground service, notification, Stop action
- [x] Policy engine (network, battery, thermal, storage, schedule) with a settings screen: mobile data, roaming, monthly cap, charging only, night schedule, profile, incoming connections, snapshot serving, storage saver, free-space floor
- [ ] Profiles and benchmark-driven tuning
- [ ] Optional boot start, guided battery-optimization flow
- [ ] Evaluate RPC over Unix socket (§4.4)
- [x] Expert mode with console, all four tiers including Danger, persistent history and file commands (§3.6, `guideline-expert-console.md`). Screens are unbuilt on a device, the logic is tested against a real node

### Phase 4: Hardening and polish

- [x] Bump fee (RBF), address labels, history export as CSV, app lock with biometrics
- [x] CPFP for incoming payments, address book, public descriptor export, encrypted wallet file export, cleaned node log, guided chain data reset, optional incoming payment notification (§8.9). Screens are unbuilt on a device, the logic is tested against a real node
- [ ] Localization completion, accessibility
- [ ] Reproducible build, hash publication. The signed release pipeline exists
- [ ] Native speaker review of the security and trust model texts in every shipped language (§3.5)
- [ ] External security review and fixes, including the Expert console and its Danger tier

### Phase 4b: Node only mode and server access (next, §3.7)

- [ ] App mode switch and first-run choice "Use as node only", node start without a wallet, wallet creation at any time without a restart
- [ ] Server access: local network bind, allow list, `rpcauth` users with salted hashes, method whitelists per role, connection card, banner, auto-off timer
- [ ] Mining readiness checklist, pool settings block, `getblocktemplate` self-test
- [ ] Spike: `-rpcauth` together with the cookie and the empty `-rpcpassword=` override on the real node
- [ ] Tests and device lab runs listed in §3.7

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
6. **Expert console:** parser, classification completeness, gating of Blocked and Danger commands, secrets masking, relock behavior (`guideline-expert-console.md` §11).

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
| Third-party artwork in the UI (helmet in the reviewed screenshots) | Legal | No artwork at all, design only (§3.3, design system guideline) |
| `-fastprune` is debug-only upstream | Option breaks or disappears | Contract check, supervisor passes it only when supported (§3.2) |
| Expert console used for scams or typed by mistake | Fund loss, key exposure | Opt-in per session, risk confirmation, command tiers, Danger tier behind a second switch with typed confirmation and a dialog per command, external security review (§3.6) |
| Seed handling bugs in Kotlin bootstrap | Fund loss | Minimal audited BIP-39 code, test vectors, external review (S11) |
| Missed legacy coin type on recovery | Apparent fund loss | Dual scan as MUST, matrix test (§14.2) |
| Misdirected funds from Base58 collision with Bitcoin | Fund loss | Bech32-only default, warnings (§8.5) |
| Store policy limits (foreground service type, battery exemption permission) | Distribution constraints | Plan direct APK and F-Droid in parallel with Play |
| Datadir incompatibility after a protocol reset | User confusion | Datadir versioning and guided reset (§5.4) |
| Unknown licensing/attribution of reused concept code | Legal | Decide before any code is copied (§17, Q2) |

## 16. Checklist

- [ ] Phase 0 spikes completed and documented
- [ ] Open items D2 to D5 of the distribution guideline answered
- [x] Native build fetches current Core on every build and records commit SHA and binary hash in the manifest
- [x] Core contract check (§11.2) enforced in CI
- [x] Node supervisor with graceful stop, drain and health classification
- [ ] Both bootstrap paths (IBD in window, snapshot) verified
- [ ] Wallet create/recover with dual coin type verified against real seeds
- [x] PSBT review and fee re-check verified (against a real regtest node)
- [x] Bech32-only defaults and Base58 warnings implemented
- [x] Background service with policy engine and user-visible stop
- [ ] Security requirements S1 to S16 verified
- [ ] All user-visible text localized in the shipped locales (tier A at launch, tier B after native review), lint gate green
- [x] Wallet and Node sections implemented, live log (sanitized export is open)
- [ ] Expert console implemented per `guideline-expert-console.md` (implemented, security review open)
- [ ] Recovery matrix and resilience tests passed
- [ ] Upstream items X1 to X4 filed or resolved in `elektron-net`
- [ ] Release pipeline with signed artifacts and published hashes (pipeline built, release key and hash publication open)
- [x] Contrast gate (`design/check-contrast.py`) green in CI
- [x] Mainnet and testnet flavors build side by side

## 17. Open Questions and Decisions

All questions of the first drafts are answered. Decisions of October 18, 2026 are listed with where they are applied.

| # | Topic | Decision | Applied in |
|---|---|---|---|
| Q1 | Application ID | `org.elektronnet.mobile`, testnet `org.elektronnet.mobiletestnet` | §10, distribution guideline |
| Q2 | Origin and license of the reviewed concept code | Open. Nothing is copied until the license and attribution are clear, code is written fresh | §15 |
| Q3 | Biometric unlock | Possible, opt-in, off by default | §8.1, S16 |
| Q4 | Phone nodes serve snapshots | Yes, only on Wi-Fi and while charging | §5.2, X2 |
| Q5 | Distribution in v1 | Repository, direct APK, F-Droid, Google Play and any other channel. A new Google developer account is opened, signed APKs are required | distribution guideline |
| Q6 | Lightning | Open path, integrated as if the infrastructure existed, disabled in v1 | §8.4.2 |
| Q7 | URI scheme | `elek:` (testnet `elektest:`) | §8.4.1, X10 |
| Q8 | Release Core ref | Latest tagged release, dev and nightly track `main` | §11.1 |
| Q9 | Testnet flavor | Separate build with its own ID `org.elektronnet.mobiletestnet`. Chain is `testnet` (testnet3) with `-testnet`, P2P 18333, RPC 18332, HRP `tb`; upstream calls testnet3 deprecated, see the distribution guideline | distribution guideline |
| Q10 | `-fastprune` default | On by default, opt-out in settings | §3.2, `core-source.env` |
| Q11 | Ticker | ELEK only | all texts |
| Q12 | Languages | English plus the most widespread languages in two tiers (Chinese, Arabic, Japanese, Korean and more) | §3.5 |
| Q13 | Artwork | None, design only, five reduced color themes with dark and light mode, no glow, readability first | §3.3, design system guideline |
| Q14 | Expert console | Danger tier in the first release, device authentication once per session, persistent history with 90 days retention, console in all builds, file commands with passphrase-protected wallets only, manual dumps deleted after 7 days, `createwallet` forbidden without a passphrase | `guideline-expert-console.md` §1.1 |
| Q16 | License | MIT, copyright Elektron Net Developers, Ali Kutlusoy and Milio aka 1000mio, `LICENSE` in the repository and in the About screen | README §12 |
| Q15 | Node only and server mode | Planned as the next milestone: node without wallet, optional LAN RPC with hashed users and method whitelist, mining readiness | §3.7 |

Remaining open items live in the distribution guideline (§9, D2 to D5): the Play account type, the Play signing key choice, F-Droid native build and the foreground service type. D1 (testnet chain) is resolved: `testnet`.
