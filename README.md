# Elektron Net: `elektron-net-mobile` Android Full Node Wallet

- **Version:** 0.7 (draft)
- **Date:** October 18, 2026
- **Audience:** Users, testers and developers of the Elektron Net Android wallet
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/wallet/wallet.cpp`, `src/validation.cpp`, `src/kernel/chainparams.cpp` (treat as ground truth for node behavior)
- **See also:** [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md), [`doc-elektron/guideline-ui-design-system.md`](./doc-elektron/guideline-ui-design-system.md), [`doc-elektron/guideline-distribution-and-signing.md`](./doc-elektron/guideline-distribution-and-signing.md), [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum), [`WHITEPAPER.md`](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)

---

## 1. What this is

`elektron-net-mobile` is a non-custodial Android wallet for Elektron Net (ELEK) that runs its own complete node. There is no server, no Electrum backend and no third-party indexer in the path: the phone validates the chain itself and signs with keys that never leave the device.

**Status:** planning phase. The complete plan lives in [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md). This README describes the target behavior and the intended build flow. Items are implemented step by step following the roadmap in that document.

## 2. Features (target)

- Full validating node (`elektrond`) bundled in the app: header and block validation, per-block UTXO attestation checks, mempool, transaction relay, mandatory pruning.
- Automatic checkpoint snapshots and P2P snapshot bootstrap, as implemented by the Elektron Net node itself.
- Background operation as a foreground service with resource profiles, battery, network and storage limits. The user can pause or stop the node at any time.
- Non-custodial wallet on the node's own descriptor wallet: native SegWit (`be1q...`) addresses, BIP-39 recovery phrase, SLIP-44 coin type 1370 plus legacy path recovery.
- Review-before-sign transaction flow (PSBT), fee policy re-check before signing, coin control, QR receive and scan.
- Node dashboard: sync phase, height, peers, disk usage, prune height, snapshot state.
- Two sections, Wallet and Node, plus an opt-in Expert mode with a console to the node.
- Storage saver (`-fastprune`) on by default with an opt-out; phone nodes serve snapshots only on Wi-Fi while charging.
- Optional biometric unlock, `elek:` payment links, QR scan and receive.
- Many languages from the start: English, German, Spanish, French, Italian, then Chinese, Arabic, Japanese, Korean, Hindi, Portuguese, Russian, Turkish, Indonesian, Vietnamese after native review.
- Cyberpunk design with neon and glow, ten color themes from the Osclass theme, no artwork, readability first. The ticker is ELEK.
- Separate testnet build (`org.elektronnet.mobiletestnet`, chain `testnet`) next to the mainnet app (`org.elektronnet.mobile`).
- English is the default language; the in-app language switch lists only reviewed translations.
- Lightning is not part of v1, but the wallet layer keeps a pluggable path for it.
- Local-only operation: no analytics, no remote geolocation, no remote QR services.

## 3. How it works

```text
Android app (Kotlin, Jetpack Compose)
        |  JSON-RPC, 127.0.0.1 only, authenticated
        v
elektrond (native, bundled as libelektrond.so)
        +-- P2P Elektron network
        +-- chainstate, pruned block data, snapshots
        +-- descriptor wallet, encrypted keys, signing
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

## 5. Supported platforms

| Item | Value |
|---|---|
| Android | minSdk 28 (target to be confirmed in the plan, Phase 0) |
| Release ABI | `arm64-v8a` |
| Emulator/CI ABI | `x86_64` |
| UI | Kotlin, Jetpack Compose |

32-bit ARM is not supported.

## 6. Building (planned flow)

The storage saver default is set by `NODE_FASTPRUNE_DEFAULT` (default `1`). Prerequisites: `git`, `docker` (rootless), `python3`, `sha256sum`, Android Studio or a Gradle/JDK setup, Android NDK as defined in `native-build/core-source.env`.

```bash
# 1. Fetch the current elektron-net source and build the native node (emulator + phone)
./native-build/build.sh all

# 2. Build the app: every build produces both flavors, mainnet and testnet
gradle wrapper --gradle-version 8.14.3   # once, creates ./gradlew
./gradlew assembleDebug                  # or only assembleMainnetDebug / assembleTestnetDebug

# 3. Install without wiping node data
adb install -r app/build/outputs/apk/mainnet/debug/app-mainnet-debug.apk
adb install -r app/build/outputs/apk/testnet/debug/app-testnet-debug.apk   # optional, installs next to mainnet
```

The native node is built once per ABI and serves both flavors, so the testnet build adds almost no build time. CI builds and tests both flavors on every change.

Do not clear app data if you want to keep the synchronized node and wallet database. Clearing data forces a full resync. Funds remain recoverable from the recovery phrase.

## 7. Repository structure (planned)

```text
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml   Gradle project
app/                       Android application
  src/main/java/org/elektronnet/mobile/
    node/                  node supervisor, RPC client, policy engine
    wallet/                bootstrap, send flow, recovery, descriptors
    security/              keystore and secure window helpers
    ui/                    Compose screens
  src/main/jniLibs/        built libelektrond.so per ABI (generated)
native-build/              fetch and build of elektrond (generated output not committed)
design/                    palettes.json, check-contrast.py, gen-theme.py
tools/                     check-locales.py
doc-elektron/              guidelines and reports
.github/workflows/         CI
```

## 8. Security summary

- Node RPC is bound to loopback and authenticated with a cookie file in the app-private data directory that the node recreates at every start; no password is stored anywhere.
- Wallet unlock is short-lived and always followed by a lock.
- Sensitive screens block screenshots and recent-app previews.
- Android backup of app data is disabled.
- Only Bech32 `be1...` receive addresses are used by default. Legacy Base58 addresses are byte-identical to Bitcoin mainnet, so the app warns before using them.
- This is pre-release software. Do not store significant funds before an independent security review. Always keep the recovery phrase offline.

The full requirement list is in plan section 9.

## 9. Documentation

| Document | Content |
|---|---|
| [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md) | Architecture, roadmap, security, build and test plan |
| [`doc-elektron/guideline-expert-console.md`](./doc-elektron/guideline-expert-console.md) | Expert mode and node console |
| [`doc-elektron/guideline-ui-design-system.md`](./doc-elektron/guideline-ui-design-system.md) | Themes, effects, legibility rules, typography |
| [`doc-elektron/guideline-distribution-and-signing.md`](./doc-elektron/guideline-distribution-and-signing.md) | Flavors, channels, signing, F-Droid, Play |
| [`elektron-net` docs](https://github.com/kutlusoy/elektron-net/tree/main/doc-elektron) | Node behavior, pruning and snapshot audit, wallet integration |

## 10. Links

- Website: https://elektron-net.org
- X: @elektronnet, @kutlusoy
- Telegram: @elektronnet
- Node: https://github.com/kutlusoy/elektron-net

## 11. License

This project builds and bundles software from Elektron Net and uses third-party Android and Kotlin libraries. Review the upstream licenses before redistribution. The license of this repository is not yet decided (see the plan, section 17, Q2).
