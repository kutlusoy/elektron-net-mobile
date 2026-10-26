# Elektron Net: `elektron-net-mobile` Security Review by Grok (xAI)

- **Version:** 1.0 (record of an external review, text unchanged)
- **Date:** October 25, 2026
- **Audience:** Maintainers, auditors and contributors
- **Reviewer:** Grok (xAI), AI-assisted static analysis
- **Scope:** Full static review of the source tree of the wallet at the time of release m0.1.5. It is an AI review, not a certified audit and not a replacement for the open independent human security review.
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) at release m0.1.5, answered in [`CHANGELOG-Release-m0.1.7.md`](./CHANGELOG-Release-m0.1.7.md), section "Security review follow-up"
- **See also:** [`security-review-2026-10-25-grok.md`](./security-review-2026-10-25-grok.md), [`security-review-2026-10-25-gpt.md`](./security-review-2026-10-25-gpt.md), [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

The text below is the review as received. Only the long dash character was replaced by a hyphen to follow the style of this repository.

---

- **Date:** 2026-10-25
- **Reviewer:** Grok (xAI) - independent static analysis
- **Repository:** https://github.com/kutlusoy/elektron-net-mobile
- **Related node:** https://github.com/kutlusoy/elektron-net
- **Commit / status reviewed:** main branch, early alpha (approximately October 2026)
- **Version context:** Wallet draft 0.28, node v4.0.x series
- **Audience:** Human and AI reviewers, auditors, contributors
- **Purpose:** Baseline review for further independent assessment

---

## 1. Executive Summary

Elektron Net Mobile is a non-custodial Android wallet that runs a full validating Elektron Net node on the device. It does not use Electrum servers, third-party indexers, or remote balance/history services. The phone validates headers, blocks, and per-block UTXO attestations itself and signs with keys that never leave the device.

The mandatory 137-day pruning is a network-wide consensus rule, not a mobile-specific compromise. Every node on the network (desktop, server, or phone) follows the same pruning depth. The mobile node is therefore a first-class, consensus-compatible full node.

Because the pruning window is fixed, node storage remains bounded and predictable on any platform. Block history is limited to the sliding 137-day window; the UTXO set grows slowly with net output creation. Project models indicate roughly 5–8 GB after 10 years, and the same design keeps storage compact even on a 100-year horizon. This is a deliberate protocol property that makes full nodes practical on ordinary devices, including phones, for the long term.

**Overall assessment:** Defensive foundations (RPC isolation, entropy design, console safeguards, Android sandbox basics) are stronger than most mobile wallets. The primary structural limitation is that the node, Tor, and I2P run as child processes under the same Android app UID. A compromise of the app process yields access to keys, the RPC cookie, and wallet data. This is typical for on-device full-node wallets but must be stated clearly.

The project itself marks the software as early alpha and notes that an independent security review, device testing matrix, and release signing are still open. This review agrees with that characterization.

---

## 2. Scope and Methodology

### In scope
- Android application source (Kotlin / Jetpack Compose)
- `AndroidManifest.xml`, network security config, data extraction rules
- Node process supervision, RPC client, configuration arguments
- Wallet creation / recovery path, descriptor import, passphrase handling
- Entropy generation (system, dice, touch)
- Expert console (FileGuard, masking, risk tiers)
- Resource policy engine
- Documentation and whitepaper claims relevant to trust model

### Out of scope
- Dynamic analysis / runtime testing on devices
- Fuzzing of native binaries (`elektrond`, Tor, I2P)
- Formal cryptographic proof review of the node itself
- Supply-chain audit of third-party native libraries beyond high-level notes
- Penetration testing

### Methodology
- Static code review of the cloned repository
- Cross-check against project guidelines (entropy, console, mobile full-node plan, whitepaper)
- Comparison against common mobile wallet architectures (Electrum-style, custodial, light-client SPV, typical non-custodial wallets)
- Focus on key management, process isolation, network exposure, leakage paths, and user-facing security controls

### Parameters / criteria applied
- Trust model honesty (does the app claim more validation power than it has?)
- Key and seed exposure windows
- Process and data isolation (app UID vs. stronger boundaries)
- Authentication and binding of RPC
- Entropy quality and failure behavior
- Default privacy posture (address reuse, screenshots, logging)
- User-controlled security options that can be weakened
- Consistency with the network’s pruning and attestation model

---

## 3. Comparison with Typical Mobile Wallets

| Dimension | Typical mobile wallets | Elektron Net Mobile |
|-----------|------------------------|---------------------|
| Validation | Light client, Electrum server, or third-party indexer | Own full validating node on device |
| Trust in remote party for balance/history | Yes (server or indexer) | No |
| Pruning / history depth | Varies; often full history via server or optional pruning | Mandatory ~137-day pruning (network consensus, identical to desktop nodes). Storage remains bounded (~5–8 GB projected in year 10; compact even after decades) |
| Address reuse default | Frequently allowed or weakly discouraged | `avoid_reuse` enforced by default; reused coins tracked separately |
| Entropy | Often plain `SecureRandom`; occasional weak fallbacks | Health-checked system + `/dev/urandom`, domain-separated mixing, dice mode, no weak fallback, build-time RNG check |
| Network privacy | Clearnet or optional Tor | Tor + I2P as first-class options, optional relay/exit with limits |
| Expert / node access | Rare or heavily restricted | Full local RPC console with risk tiers, FileGuard, secret masking |
| Server dependency | Usually required for operation | None |
| Process isolation | Single app process (common) | Single app UID; node/Tor/I2P as child processes |

**Key differentiator:** Most mobile wallets outsource chain truth to a server. This app does not. The cost is local resource use (battery, storage, bandwidth) and the shared-process isolation model common to on-device nodes.

---

## 4. Architecture and Trust Model

- The node binary is launched via `ProcessBuilder` under the app’s UID.
- RPC is bound to `127.0.0.1` only, authenticated with a cookie file in the app-private data directory. The cookie is deleted and recreated on each start. No password is stored.
- Network security config permits cleartext only for loopback and onion hosts.
- `NodeService` is `exported="false"`.
- Android backup is disabled; data extraction rules exclude all domains.
- Sensitive screens apply `FLAG_SECURE` (screenshot / recents protection), with an optional user toggle.
- Mandatory pruning and UTXO attestation are protocol features. The app’s documentation correctly describes the limits (validation from snapshot base or genesis; cannot fully revalidate reorgs deeper than the snapshot base).
- Storage model: only the last ~197,280 blocks (137 days) plus the current UTXO set and indexes are retained. The UTXO set grows approximately linearly with net new outputs; combined with the fixed pruning window this keeps total node storage compact and predictable on every platform, including mobile, for decades or longer.

The phone runs a genuine full node under the network’s consensus rules. It is not a degraded light client.

---

## 5. Positive Findings

1. **RPC exposure is tightly controlled.** Loopback-only binding + ephemeral cookie is the correct baseline.
2. **Entropy design is above average.** Health checks, domain separation, zeroing attempts, dice mode with statistical checks, and a build-time prohibition on weak RNGs.
3. **Console is defensively designed.** Path traversal / symlink / outside-root protection (FileGuard), secret masking in command history, Danger tier requiring typed confirmation and delay, automatic `walletlock` on exit.
4. **Privacy defaults are sound.** Address reuse is actively avoided; reused outputs are surfaced separately.
5. **Resource policy is thoughtful.** Battery hysteresis, data caps, charging-only, schedule windows, and thermal considerations make continuous full-node operation more practical.
6. **Trust-model language is honest.** Documentation does not claim full archival verification or stronger isolation than exists.
7. **Build reproducibility notes and contract checks** (RPC surface / node behavior) reduce some classes of integration surprise.
8. **Long-term storage is bounded by design.** The fixed 137-day pruning window plus slow UTXO-set growth keeps node data compact on every platform. Project estimates of ~5–8 GB after 10 years imply that even after 100 years a full node remains practical for ordinary devices, including phones. This is a protocol-level property, not a mobile workaround.

---

## 6. Findings

### High

#### H1 - Shared process / UID isolation boundary
**Description:** The node, Tor, and I2P run as child processes of the Android application. There is no separate `android:process`, no additional SELinux domain, and no container.  
**Impact:** Compromise of the app process (memory corruption, malicious code execution via JNI or future extensions, accessibility abuse, etc.) grants access to the RPC cookie, wallet files, unlocked keys, and in-memory seed material.  
**Note:** This is the normal model for most on-device full-node wallets. It is still the largest structural limitation relative to hardware wallets or strongly isolated designs.  
**Recommendation:** Consider a separate process for the node with a narrow IPC surface if feasible; document the boundary clearly for users and auditors. Hardware-backed keystore for master key material would raise the bar further but is a larger change.

#### H2 - Weak passphrase policy
**Description:** Passphrase acceptance is primarily a minimum-length check. No complexity requirements, common-password list, or strength estimator is applied.  
**Impact:** Users can encrypt the wallet with easily guessable passphrases.  
**Recommendation:** Add a stronger policy (length + character-class requirements and/or a zxcvbn-style estimator plus a common-password blocklist).

### Medium

#### M1 - Seed / mnemonic exposure window in the app process
**Description:** BIP-39 seed derivation and descriptor import occur in the application process before the node encrypts the wallet. Sensitive arrays are zeroed on a best-effort basis.  
**Impact:** Memory dump or debugger access during wallet creation/recovery can capture the seed.  
**Recommendation:** Minimize the window; consider keeping sensitive material only inside the node process where possible. Continue zeroing and avoid logging.

#### M2 - Update check without cryptographic verification of binaries
**Description:** The app queries the GitHub tags API and directs the user to download. There is no in-app verification of the downloaded APK signature.  
**Impact:** Compromise of the GitHub account or a malicious release could distribute a backdoored APK.  
**Recommendation:** Add signature verification guidance or in-app check against a pinned public key; document the expected release signing process clearly.

#### M3 - Screenshot protection is user-disableable
**Description:** `FLAG_SECURE` can be turned off by the user.  
**Impact:** Seed phrases, passphrases, and console sessions can be captured via screenshots, screen recording, or recents if the user disables the protection.  
**Recommendation:** Make protection mandatory on screens that display seeds or passphrases. Allow the toggle only for non-sensitive screens or require re-confirmation.

#### M4 - Large native attack surface
**Description:** The app ships `elektrond`, Tor, and I2P as native binaries.  
**Impact:** Vulnerabilities in any of these components can lead to code execution under the app UID.  
**Recommendation:** Keep native dependencies updated; document the exact upstream versions and build provenance; treat native updates as security-relevant.

### Low / Informational

#### L1 - Optional public explorer links
Deep links to a public mempool explorer can leak addresses if the user follows them. The project notes this as opt-in with a privacy warning. Ensure the warning is prominent.

#### L2 - Deep-link / payment URI handling
A custom URI scheme is registered. Reviewers should verify strict parsing and rejection of malformed or unexpected parameters to avoid injection or unexpected wallet actions.

#### L3 - No hardware-backed master keys
All key material is software-based. This is expected for the current design but limits resistance to device compromise.

#### L4 - Early-alpha status
Device test matrix, native-speaker translation review, release keys, and an independent security review are still open per project documentation. These should be completed before recommending the wallet for significant funds.

---

## 7. Recommendations for Further Reviewers

1. Perform dynamic testing on physical devices (Android 14/15/16) covering wallet creation, recovery after pruning, coin control, CPFP, Tor/I2P, and crash/restart scenarios.
2. Review native binaries and their build reproducibility against the published manifests.
3. Audit payment URI and QR parsing for edge cases.
4. Examine the exact lifetime of unlocked wallet state and the interaction between console commands and automatic locking.
5. Test resource-policy edge cases (battery thresholds, data caps, thermal throttling, schedule windows).
6. Confirm that no sensitive material appears in logs, crash reports, or notification content.
7. Evaluate the feasibility and value of moving the node into a separate Android process.

---

## 8. Conclusion

Elektron Net Mobile is architecturally distinctive: it places a consensus-compatible full node on the phone instead of outsourcing chain truth to a server. Its defensive controls around RPC, entropy, console access, and default privacy posture are better than the majority of mobile wallets.

The main limitations are the shared app-UID process model and the currently weak passphrase policy. Both are addressable. Until an independent audit and broader device testing are completed, the project’s own guidance should be followed: do not store significant funds on pre-release software.

This document is intended as a starting point. Subsequent reviewers should verify the findings against the current source tree, test on hardware, and expand coverage of the native components.

---

## Appendix: Key References in the Repository

- `README.md` - high-level security claims and feature list
- `WHITEPAPER.md` - trust model and privacy description
- `doc-elektron/guideline-entropy-and-randomness.md`
- `doc-elektron/guideline-expert-console.md`
- `doc-elektron/guideline-mobile-fullnode-wallet-plan.md`
- `app/src/main/java/org/elektronnet/mobile/node/NodeSupervisor.kt` - process launch
- `app/src/main/java/org/elektronnet/mobile/node/NodeConfig.kt` - RPC bind and cookie arguments
- `app/src/main/java/org/elektronnet/mobile/wallet/Entropy.kt` - entropy sources
- `app/src/main/java/org/elektronnet/mobile/wallet/WalletBootstrap.kt` - wallet creation / encryption
- `app/src/main/java/org/elektronnet/mobile/console/FileGuard.kt` and `Masking.kt`
- `app/src/main/AndroidManifest.xml` and `res/xml/network_security_config.xml`
