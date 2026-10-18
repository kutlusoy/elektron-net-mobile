# Elektron Net: `elektron-net-mobile` Distribution, Signing and Flavors

- **Version:** 0.2 (draft)
- **Date:** October 18, 2026
- **Audience:** Release engineers, Android developers, maintainers who operate store accounts and signing keys
- **Reference implementation:** `app/build.gradle.kts` product flavors and `native-build/` in this repository (to be created), [F-Droid Build Metadata Reference](https://f-droid.org/docs/Build_Metadata_Reference/), [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/)
- **Related repositories:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) (node source), [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md), [`guideline-ui-design-system.md`](./guideline-ui-design-system.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Decisions

| Topic | Decision |
|---|---|
| Mainnet application ID | `org.elektronnet.mobile` |
| Testnet application ID | `org.elektronnet.mobiletestnet`, separate build (flavor) |
| Channels in v1 | GitHub repository and releases, direct APK, F-Droid, Google Play, and any other channel that accepts a signed APK |
| Google Play account | The existing developer account is inactive. A new, separate account is opened for Elektron Net. |
| Signing | Signed release APKs are required. One release key, one fingerprint, published. |
| Testnet chain | `testnet` (testnet3, started with `-testnet`), decided October 18, 2026. Not testnet4. |
| Payment URI | `elek:` for mainnet. Testnet scheme see §3. |
| Biometric unlock | Possible, opt-in (plan §8.1) |

## 2. Flavors

Two product flavors from one code base. The flavor changes identity and chain only, never the security behavior.

| Item | `mainnet` | `testnet` |
|---|---|---|
| `applicationId` | `org.elektronnet.mobile` | `org.elektronnet.mobiletestnet` |
| App name | Elektron Net | Elektron Testnet |
| Node chain argument | none (main) | `-testnet` |
| Datadir | `files/node` | `files/node-testnet` (inside the testnet app's own private storage) |
| Own `bitcoin.conf` | yes | yes (never shared) |
| Ports | 8333 P2P, 8332 RPC (RPC on loopback only) | 18333 P2P, 18332 RPC (RPC on loopback only) |
| Address prefix (Bech32 HRP) | `be` | `tb` |
| URI scheme | `elek:` | `elektest:` (recommended) |
| Visual | default themes | permanent TESTNET bar, own icon color (design system §6) |
| Distribution | all channels | GitHub releases and direct APK. F-Droid and Play optional later. |

- **MUST:** the two apps can be installed side by side on one phone. They do not share data, keys, the Keystore alias or ports. Because the P2P port may clash when both nodes run, the testnet build uses the testnet port by chain and the supervisor handles a port-in-use error with a clear message.
- **MUST:** the mainnet APK contains no testnet code path that can be switched on at runtime, and vice versa. The chain is a build constant, so a user cannot be tricked into the wrong chain by a setting.
- **MUST:** `BuildConfig.CHAIN`, the applicationId and the URI scheme are the only values that differ. Everything else is shared code, so tests on one flavor cover the other.
- The Core is built once per ABI from the same source (§4) and serves both flavors, the chain is chosen by the start argument. The contract check covers mainnet and regtest today, a testnet case is a to-do.
- Values verified in `elektron-net@main` (`src/kernel/chainparams.cpp`, `src/chainparamsbase.cpp`): testnet3 uses P2P port 18333, RPC port 18332, HRP `tb`, data subdirectory `testnet3`. The flavor constants live in `app/build.gradle.kts` and are exposed as `ChainConfig`.
- **Upstream risk:** the node help text says testnet3 is deprecated and will be removed in an upcoming release, testnet4 (`-testnet4`, P2P 48333, RPC 48332, also HRP `tb`) is the successor. Because the chain is one build constant, a later switch is a change of a few values in the flavor definition. To do: add `-testnet` to the startup arguments of the contract check so that the day it disappears from `-help-debug` is a build error.
- The HRP `tb` is the same as on Bitcoin testnet. The testnet flavor therefore rejects `bc`, `be` and `bcrt` addresses with a specific message, and the mainnet flavor rejects `tb` (plan §8.5). Testnet coins have no value, so a mix-up with Bitcoin testnet cannot lose money, but the warning text still says which network an address belongs to.

## 3. Payment URI Schemes

- Mainnet: `elek:` (registered in the manifest as an intent filter for VIEW with category BROWSABLE and DEFAULT).
- Testnet: `elektest:` is recommended, so a testnet link never opens the mainnet app and never gets confused with it. If both are installed, each handles only its own scheme.
- The grammar and the safety rules are in the plan, §8.4 (Payment URI).

## 4. Build Reproducibility

- **MUST:** every release has a pinned Core tag (plan §11.1), a pinned NDK and image (plan §11), and the manifest with the resulting hashes. The release notes quote the Core tag and commit SHA.
- **SHOULD:** the APK build is reproducible: same source commit, same pinned toolchain, same unsigned APK bytes (apart from the signature). Reproducibility is the precondition for F-Droid's reproducible-build verification and gives users a way to check the binary.
- Native libraries are built inside the pinned Docker image from the fetched Core source. Gradle consumes the resulting `libelektrond.so` per ABI. No prebuilt binary is committed.
- 16 KB page size alignment is verified for each library at build time (already part of `native-build/build.sh`).

## 5. Signing

- One long-lived **release key** (RSA 4096 or EC P-256, stored offline, hardware token recommended). Its SHA-256 fingerprint is published in the repository and on the website.
- **MUST:** the same key signs the GitHub release APKs and the F-Droid-compatible build, so an update from any channel works with any other channel where Android allows it. If F-Droid signs with its own key, users cannot move between F-Droid and other channels without reinstalling. That is acceptable only if documented. Preferred is F-Droid reproducible builds with the upstream signature.
- **Google Play:** use Play App Signing. Generate the app signing key locally and upload it on enrollment (this keeps the same signing key as the direct APK) or accept a Play-generated key and document that Play and direct builds then differ in signature. Decide before the first upload, because the choice cannot be changed afterwards. Recommendation: upload the own key so that one fingerprint is valid everywhere.
- **MUST:** signing happens only on the release machine or in a protected release job. The key is never in the repository, never in CI logs, never in a plain CI secret without protection rules.
- **MUST:** each release publishes SHA-256 sums of all APKs, signed with the maintainer's signing key (detached signature), plus the signing certificate fingerprint.
- Rotating the key follows Android's APK signature scheme v3 key rotation, planned in advance.
- The debug and nightly builds use a separate debug key and a different applicationId suffix (`.debug`, `.nightly`) so they never replace a release install.

## 6. Channels

### 6.1 GitHub repository and releases (primary source)

- Tagged releases carry the signed APK per flavor, checksums, signature, the build manifest and release notes.
- Nightly builds (`main` Core) are published separately as pre-releases, never as the latest release.

### 6.2 Direct APK

- Linked from the website with the SHA-256 and the signing fingerprint next to the download button. The app's About screen shows its own signing fingerprint so users can compare.
- Users must allow installs from the browser or file manager. The install instructions explain that and warn against APKs from other sources.

### 6.3 F-Droid

- Needs an `fdroiddata` metadata entry with source repository, build recipe and tag. The tag is pinned per version.
- **MUST for F-Droid:** all dependencies are open source, no Google Play Services, no Firebase, no proprietary ML Kit. QR scanning therefore uses an open library (for example ZXing core with CameraX) and not the bundled ML Kit barcode scanner.
- The build recipe must fetch the Core source at the pinned release tag. Because F-Droid builds from source on its own servers, the native build must run without the Docker image (or F-Droid must accept it). This is a Phase 0 spike item: confirm that F-Droid's build environment can run the pinned NDK build and the `fetch-core.sh` step, and that network access during build is acceptable for the pinned Git fetch. If not, a vendored, hash-verified source archive of the release tag is attached to the release and used by the recipe.
- F-Droid may flag non-free network services and anti-features; this app has none (no analytics, no trackers). The ELEK node connects only to the Elektron Net P2P network.

### 6.4 Google Play

- A new developer account is created for Elektron Net. Whether it is a personal or an organization account matters: reports say new personal accounts need a closed test with at least 12 opted-in testers for 14 days before they can publish to production, and organization accounts are exempt. Verify the current rule in the Play Console help before planning the schedule.
- The app's category is Finance or Tools. Play policy for cryptocurrency apps and for background services needs a check at submission time. Items to prepare: a privacy policy URL (the app collects nothing), the Data safety form (no data collected, no data shared), foreground service type declaration (`dataSync` or `specialUse` with a justification, see plan §4.3), and the declaration that the app is a non-custodial wallet.
- An app that runs a 24/7 node can look unusual to reviewers. Prepare a short review note explaining what the node does, that the service is user-controlled and that it can be stopped at any time.
- Target API level must follow Play's current requirement at submission time.
- The mainnet and testnet apps are separate Play listings. The testnet app is optional for Play.

### 6.5 Other channels

Any channel that accepts a signed APK (alternative stores, Obtainium style update from GitHub releases, self-hosted repository) is allowed. The rule is the same everywhere: the signature matches the published fingerprint, and the manifest hashes match the release notes.

## 7. Android Developer Verification

Google has announced that apps installed on certified Android devices must come from verified developers, including apps outside Play. Reported timeline: regional enforcement from September 2026 (Brazil, Indonesia, Singapore, Thailand), wider rollout from 2027, and the registration of package name and signing key.

- **MUST (release engineering):** register the developer identity and the package names (`org.elektronnet.mobile`, `org.elektronnet.mobiletestnet`, plus nightly if needed) and the signing key as soon as the program is available to the maintainers, and re-check the rules before every release. Reported dates come from third-party coverage and can change, so confirm against the official Android developer documentation.
- Install through `adb` stays available for developers and testers.
- This applies to every channel, so it does not change the build, only the release checklist.

## 8. Updates

- The app does not update itself and does not call home. Updates come through the channel the user installed from.
- The About screen shows the app version, Core tag and commit, manifest hash and signing fingerprint.
- **SHOULD:** an opt-in "check for new release" action that opens the GitHub releases page in the browser, no background check.
- Datadir versioning and migration on update: plan §5.4.

## 9. Open Questions

| # | Question | Recommendation |
|---|---|---|
| D1 | Which testnet? | **Resolved:** `testnet` (testnet3). Watch the upstream deprecation notice (§2). |
| D2 | Play account type: personal or organization? | Organization (company or association) avoids the closed-test waiting rule if one is available; otherwise plan 14 days of closed testing with at least 12 testers. |
| D3 | Own signing key uploaded to Play App Signing, or Play-generated key? | Upload the own key, one fingerprint everywhere. |
| D4 | Can F-Droid build the native part from source in its environment? | Phase 0 spike, fallback is a hash-verified source archive per release. |
| D5 | Foreground service type accepted by Play for a node? | Prepare the `dataSync` / `specialUse` justification and test in a closed track early. |

## 10. Checklist

- [ ] Flavors `mainnet` and `testnet` with separate IDs, datadirs, schemes
- [ ] Release key generated offline, fingerprint published
- [ ] Reproducible build check in CI
- [ ] Signed APKs, checksums and detached signatures in each release
- [ ] F-Droid build recipe and metadata drafted, no proprietary dependencies
- [ ] New Play account opened, privacy policy, Data safety form, service declaration prepared
- [ ] Developer verification registration done for all package names
- [ ] About screen shows Core tag, commit, manifest hash and signing fingerprint
