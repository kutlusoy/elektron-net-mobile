# Elektron Net: `elektron-net-mobile` Android Full Node Wallet

- **Version:** 0.28 (draft)
- **Date:** October 22, 2026
- **Audience:** Users, testers and developers of the Elektron Net Android wallet
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/wallet/wallet.cpp`, `src/validation.cpp`, `src/kernel/chainparams.cpp` (treat as ground truth for node behavior)
- **See also:** [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md), [`doc-elektron/guideline-ui-design-system.md`](./doc-elektron/guideline-ui-design-system.md), [`doc-elektron/guideline-distribution-and-signing.md`](./doc-elektron/guideline-distribution-and-signing.md), [`doc-elektron/guideline-tor-and-i2p.md`](./doc-elektron/guideline-tor-and-i2p.md), [`WHITEPAPER.md`](./WHITEPAPER.md), [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum), [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)

---

## 1. What this is

`elektron-net-mobile` is a non-custodial Android wallet for Elektron Net (ELEK) that runs its own complete node. There is no server, no Electrum backend and no third-party indexer in the path: the phone validates the chain itself and signs with keys that never leave the device.

**Status:** beta. The app has been tested in practice and the wallet runs stably. It is meant for everyday amounts, larger holdings belong in a cold wallet. The app runs the bundled node on a phone (headers, snapshot and tip verified on mainnet), creates and restores several wallets, receives, sends with coin control, fee bump and CPFP, keeps a per-wallet history and an address book, and has escrow, PSBT, sweep and message tools, an Expert console and draft translations in 15 languages. Open: the device test matrix (the screens are built in CI but not yet run on devices), native-speaker review of the translations, release keys and an external security review. The complete plan lives in [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md). This README describes the target behavior and the intended build flow. Items are implemented step by step following the roadmap in that document.

## 2. Features (target)

- Full validating node (`elektrond`) bundled in the app: header and block validation, per-block UTXO attestation checks, mempool, transaction relay, mandatory pruning.
- Automatic checkpoint snapshots and P2P snapshot bootstrap, as implemented by the Elektron Net node itself.
- Background operation as a foreground service with resource profiles, battery, network and storage limits. The user can pause or stop the node at any time, and sees what the node is doing (progress, peers, telemetry, optional live log).
- True entropy for the recovery phrase: at least 128 bits, health-checked system generators, no weak fallback, an optional step where you draw on the screen for extra randomness, and a mode where you roll your own dice (50 rolls for 12 words).
- Non-custodial wallet on the node's own descriptor wallet: native SegWit (`be1q...`) addresses, BIP-39 recovery phrase, SLIP-44 coin type 1370 plus legacy path recovery.
- Escrow wallets (M of N multisig, for example 2 of 3 with an arbiter), PSBT tools to check, sign, combine and send payment files, key sweep like in Electrum, and message signing and verification.
- Several wallets with a quick switch and removal only with that wallet's passphrase, review-before-sign transaction flow (PSBT), fee policy re-check before signing, optional "subtract fee from amount", coin control (choose and freeze coins), address labels, QR receive and scan.
- No address reuse by default: fresh receive and change addresses, and coins that arrive on an already spent address are kept out of automatic spending (shown separately, spendable on purpose).
- Complete transaction history per wallet, 10 per page newest first, with search across everything, filters and CSV export; speed up a waiting payment with fee bump (RBF) when you sent it, or with CPFP when it is incoming.
- Address book (local, shared by all wallets), public descriptor export, an encrypted wallet file export and a restore from a `wallet.dat` backup through the system file picker. A restored file without a passphrase gets one right away, and is discarded if you cancel.
- Tor and I2P next to IPv4 and IPv6, each switched on in Settings. Tor and a small I2P router run inside the app next to the node. Options cover the Tor routing (next to the normal connections or everything through Tor), running a Tor relay or exit relay with bandwidth and monthly limits, and I2P tunnel sharing. Small chips beside the "Synced" dot show the networks in use: IPv4, IPv6, Tor (purple), I2P (green), and the Node tab has a tile per switched-on network with its status, address, peers and ports. Ports that other programs already use are moved to the next free one. The node keeps the same Tor and I2P address until you create a new one in Settings. Details: [`doc-elektron/guideline-tor-and-i2p.md`](./doc-elektron/guideline-tor-and-i2p.md).
- Fee estimates come from the node with a fallback to a mempool explorer; the explorer list (default `https://mempool.elektron-net.org`) can be edited and is also used for transaction links.
- Node dashboard: sync phase, height, peers with country (offline DB-IP Country Lite lookup, addresses never leave the phone), disk usage, prune height, snapshot state.
- Simple everyday wallet following common mobile wallet conventions (balance, Receive, Send, history), with the node one tab away and explained in plain words, plus an opt-in Expert mode with a console to the node (four risk tiers, confirmation dialogs, encrypted history, file commands confined to a private folder).
- Storage saver (`-fastprune`) on by default with an opt-out; phone nodes serve snapshots only on Wi-Fi while charging.
- Optional biometric unlock with an adjustable lock delay, a temporary screenshot switch for tutorials, confirmation before the node is stopped or paused, `elek:` payment links, QR scan (CameraX and ZXing, no Google services) and receive, and an optional notification for incoming payments that hides the amount on the lock screen.
- Node dashboard also shows upcoming chain events: next halving with date and block reward, difficulty epoch progress and the projected difficulty at the next retarget.
- Node tools: sync progress, optional live node log with a cleaned copy and share (IP addresses, onion names, addresses and paths removed), peer details with country on tap, and a guided reset of the downloaded chain data that keeps your wallets.
- Amounts can be shown in ELEK, mELEK, bit or lepton (1 ELEK = 1,000 mELEK = 1,000,000 bit = 100,000,000 lepton), with the ELEK value repeated when you send. Payment links are lowercase and carry amount and message. The first start can skip the wallet and run the node only.
- Language follows the system and can be switched in Settings to any shipped language.
- Fifteen languages ship as drafts: English, German, Spanish, French, Italian, Chinese (Simplified), Arabic, Japanese, Korean, Hindi, Portuguese (Brazil), Russian, Turkish, Indonesian and Vietnamese. All except English await native-speaker review of the security texts before a store release.
- Calm design without glow: five color themes (Teal, Blue, Rust, Red and Matrix), each in a dark and a light mode, one readable system font for all themes, a contrast check in CI, no artwork. The ticker is ELEK.
- Separate testnet build (`org.elektronnet.mobiletestnet`, chain `testnet`) next to the mainnet app (`org.elektronnet.mobile`).
- English is the default language; the app follows the system language and falls back to English.
- Lightning is not part of v1, but the wallet layer keeps a pluggable path for it.
- Local-only operation: no analytics, no remote geolocation, no remote QR services.

**Next milestone (planned, not built):** a node only mode that starts without a wallet, and an optional server access for the local network (hashed RPC users, method whitelist, mining readiness) so a phone can serve as a pool or miner backend. See plan section 3.7.

## 3. How it works

```text
Android app (Kotlin, Jetpack Compose)
        |  JSON-RPC, 127.0.0.1 only, authenticated
        v
elektrond (native, bundled as libelektrond.so)
        +-- P2P Elektron network (IPv4, IPv6, optionally Tor and I2P)
        +-- chainstate, pruned block data, snapshots
        +-- descriptor wallet, encrypted keys, signing
libtor.so, libi2pd.so (optional helpers, started by the app next to the node)
        +-- Tor: SOCKS and control on 127.0.0.1
        +-- I2P router: SAM on 127.0.0.1
```

The app supervises the node process and presents the wallet. Private keys live in the node's encrypted wallet, not in app code. After wallet creation or recovery the app does not hold the recovery phrase.

Elektron Net prunes every node to the last 197,280 blocks (about 137 days) by design. A restored wallet therefore recovers its full balance from the current UTXO set, but transaction history older than the retention window does not exist on the network. Details: [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md) and the plan, section 8.3.

## 4. Node source: always current

The node is not stored in this repository. On every build, the build scripts fetch the current source from the canonical repository [`kutlusoy/elektron-net`](https://github.com/kutlusoy/elektron-net), build `elektrond` for the target ABI, and run a contract check against the result. The resolved commit SHA and the SHA-256 of the produced binary are written to a build manifest that ships inside the app and is shown on the node screen.

Which ref is fetched depends on the build type:

| Build type | Core source |
|---|---|
| `dev`, `nightly` | `main` of `elektron-net` |
| `release` | the latest tagged release of `elektron-net` (never `main`) |

The ref can be overridden for debugging or for reproducing an older build:

```bash
BUILD_TYPE=release ./native-build/build.sh all
CORE_REF=<branch|tag|sha> ./native-build/build.sh all
```

Full specification: plan section 11.1.

Tor and the I2P router (`i2pd`) are built the same way: `native-build/fetch-helpers.sh` resolves the newest stable release tag of each on every build, and the helper build cross-compiles them for the NDK with pinned dependency checksums. `HELPERS=0 ./native-build/build.sh all` builds the app without them. See [`doc-elektron/guideline-tor-and-i2p.md`](./doc-elektron/guideline-tor-and-i2p.md).

## 5. Supported platforms

| Item | Value |
|---|---|
| Android | minSdk 28, targetSdk 36 |
| Release ABI | `arm64-v8a` |
| Emulator/CI ABI | `x86_64` |
| UI | Kotlin, Jetpack Compose |

32-bit ARM is not supported.

## 6. Building

The storage saver default is set by `NODE_FASTPRUNE_DEFAULT` (default `1`). Prerequisites: `git`, `docker` (rootless), `python3`, `sha256sum`, Android Studio or a Gradle/JDK setup, Android NDK as defined in `native-build/core-source.env`.

```bash
# 1. Fetch the current elektron-net source and build the native node (emulator + phone)
./native-build/build.sh all

# 2. Build the app: every build produces both flavors, mainnet and testnet
gradle wrapper --gradle-version 8.14.3   # once, creates ./gradlew
./gradlew assembleDebug                  # or only assembleMainnetDebug / assembleTnetDebug

# 3. Install without wiping node data
adb install -r app/build/outputs/apk/mainnet/debug/app-mainnet-debug.apk
adb install -r app/build/outputs/apk/tnet/debug/app-tnet-debug.apk   # optional, installs next to mainnet
```

The native node is built once per ABI and serves both flavors, so the testnet build adds almost no build time. CI builds and tests both flavors on every change.

Do not clear app data if you want to keep the synchronized node and wallet database. Clearing data forces a full resync. Funds remain recoverable from the recovery phrase.

### Releases

Signed APKs are published under Releases as "Elektron Mobile v<version>", built by `.github/workflows/release.yml` with the release key from a pushed tag or a manual run (test builds come from the manual `prerelease` workflow, see [`doc-elektron/guideline-release-signing.md`](./doc-elektron/guideline-release-signing.md)), with the node built fresh from `elektron-net` (see [`doc-elektron/guideline-distribution-and-signing.md`](./doc-elektron/guideline-distribution-and-signing.md) §6.1). Each release has the mainnet APK, the testnet APK and `SHA256SUMS.txt`. What each wallet version added is listed in [`doc-elektron/CHANGELOG-Release-m0.1.0.md`](./doc-elektron/CHANGELOG-Release-m0.1.0.md) and the files for the later versions next to it. Versions look like `v4.0.4-m0.1.0`: the Elektron Net node version, then `m` and the wallet version, so the wallet can be updated without a new node (distribution guideline §8.1). Test builds without a release key are signed with one fixed public test key (`app/test-signing`), so they can update each other and keep their data. A pre-release (test build) and a release never update each other: the signatures and application IDs differ, so switching means a fresh install and a new setup. Export the wallet file or keep the recovery phrase first. Update a pre-release with a newer pre-release and a release with a newer release.

## 7. Repository structure

```text
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml   Gradle project
app/                       Android application
  src/main/java/org/elektronnet/mobile/
    node/                  node supervisor, controller, RPC client, peers, telemetry, Tor and I2P settings and helper processes
    policy/                policy engine (network, battery, thermal, storage)
    runtime/               tick loop and status headline
    service/               foreground service, notifications, settings, node host
    wallet/                bootstrap, send flow, coin control, bump and CPFP, exports, address book
    console/               Expert console logic (parser, tiers, file guard, history)
    explorer/, geo/        explorer list and fee fallback, offline country lookup
    ui/                    Compose screens, theme and view models
  src/main/jniLibs/        built libelektrond.so, libtor.so and libi2pd.so per ABI (generated)
native-build/              fetch and build of elektrond, Tor and i2pd (generated output not committed)
design/                    palettes.json, check-contrast.py, gen-theme.py, logo/ and gen-logo.py
tools/                     check-locales.py, check-rng.py, fetch-geoip.sh, version.py
doc-elektron/              guidelines and reports
.github/workflows/         CI
```

## 8. Security summary

- The only web request outside the explorer list is the update check you start yourself in Settings. Cleartext HTTP is refused app wide except to the loopback node, localhost and onion hosts. Node RPC is bound to loopback and authenticated with a cookie file in the app-private data directory that the node recreates at every start; no password is stored anywhere.
- Wallet unlock is short-lived and always followed by a lock.
- Sensitive screens block screenshots and recent-app previews.
- Android backup of app data is disabled.
- Only Bech32 `be1...` receive addresses are used by default. Legacy Base58 addresses are byte-identical to Bitcoin mainnet, so the app warns before using them.
- This is beta software. It has been tested in practice and the wallet runs stably, so it can be used for everyday amounts. Keep larger holdings in a cold wallet until an independent security review is done. Always keep the recovery phrase offline.

The full requirement list is in plan section 9.

## 9. Documentation

| Document | Content |
|---|---|
| [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md) | Architecture, roadmap, security, build and test plan |
| [`doc-elektron/guideline-escrow-psbt-and-tools.md`](./doc-elektron/guideline-escrow-psbt-and-tools.md) | Escrow, PSBT tools, key sweep, message signing |
| [`doc-elektron/guideline-entropy-and-randomness.md`](./doc-elektron/guideline-entropy-and-randomness.md) | Entropy sources, health checks, dice mode, static RNG check |
| [`doc-elektron/guideline-expert-console.md`](./doc-elektron/guideline-expert-console.md) | Expert mode and node console |
| [`doc-elektron/guideline-ux-and-screens.md`](./doc-elektron/guideline-ux-and-screens.md) | Everyday user flows, navigation, screens, wording, usability tests |
| [`doc-elektron/guideline-ui-design-system.md`](./doc-elektron/guideline-ui-design-system.md) | Themes, effects, legibility rules, typography, logo |
| [`doc-elektron/guideline-distribution-and-signing.md`](./doc-elektron/guideline-distribution-and-signing.md) | Flavors, channels, signing, F-Droid, Play |
| [`WHITEPAPER.md`](./WHITEPAPER.md) | The idea, architecture, privacy model and full feature list of the app |
| [`doc-elektron/guideline-tor-and-i2p.md`](./doc-elektron/guideline-tor-and-i2p.md) | Tor and I2P: design, node arguments, relay options, indicator, build, tests |
| [`elektron-net` docs](https://github.com/kutlusoy/elektron-net/tree/main/doc-elektron) | Node behavior, pruning and snapshot audit, wallet integration |

## 10. Credits

- **Elektron Net Developers:** development of the app and the node integration.
- **Ali Kutlusoy, X [@kutlusoy](https://x.com/kutlusoy):** founder of Elektron Net and lead of this app.
- **Milio aka 1000mio, X [@spiroflex](https://x.com/spiroflex) 🇪🇸 (Spain):** co-author and the originator of the idea for this successor app.

## 11. Links

- Website: https://elektron-net.org
- X: @elektronnet, @kutlusoy
- Telegram: @elektronnet
- Node: https://github.com/kutlusoy/elektron-net

## 12. License

Released under the [MIT License](./LICENSE). Copyright (c) 2026 Elektron Net Developers, Ali Kutlusoy (@kutlusoy) and Milio aka 1000mio (@spiroflex). The app is called "Elektron" (mainnet) and "Elektron (T)" (testnet). This project builds and bundles software from Elektron Net and uses third-party Android and Kotlin libraries, each under its own license. Review the upstream licenses before redistribution.
