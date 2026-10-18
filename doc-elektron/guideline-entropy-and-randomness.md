# Elektron Net: `elektron-net-mobile` Entropy and Randomness

- **Version:** 0.2 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, security reviewers, auditors, users who want to verify their own wallet creation
- **Reference implementation:** `app/src/main/java/org/elektronnet/mobile/wallet/Entropy.kt`, `Bip39.kt`, `Bip32.kt`, `WalletBootstrap.kt`, `app/src/test/java/org/elektronnet/mobile/wallet/EntropyTest.kt` and `tools/check-rng.py` in this repository
- **Related repositories:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) (node and wallet), [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (wording only)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (§8.1, §8.3, §9), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md) (§5)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Decisions

A wallet is only as strong as the randomness behind its recovery phrase. Weak random number generators have caused repeated wallet losses in the industry, so the app treats randomness as a security boundary of its own.

| Topic | Decision |
|---|---|
| Minimum strength | 128 bits of true entropy, never less. The default phrase has 12 words. |
| Optional strength | 24 words (256 bits). |
| Sources | The operating system generator plus a second, independent read of `/dev/urandom`, both health checked and mixed. |
| User dice | Supported from the first release. The user rolls a normal six-sided die. |
| Touch drawing | Optional extra input, mixed into the system generator. It never counts toward the 128 bit floor (§4). |
| Failure behavior | If the generator fails a check, no phrase is created. There is no fallback to a weaker source. |
| Time, counters, device ids, sensors | Never used as entropy. |

## 2. System Generator

- **MUST:** the only seed for a phrase is the output of `SystemEntropy.draw`. It is a cryptographic generator seeded by the Android kernel (`SecureRandom`, backed by the kernel generator) combined with a direct read of `/dev/urandom`. The mixing function is SHA-512 over a fixed domain string and all source outputs, truncated to the needed length.
- **MUST:** mixing never makes the result weaker than the strongest healthy source. A hash of independent inputs is at least as unpredictable as the best of them, so a damaged second source cannot lower the strength.
- **MUST (health check per source):** each source is read twice. A source is rejected if its first output is constant (all bytes equal, including all zero) or equals its second output. The read is retried up to three times. The primary source (`SecureRandom`) is required: if it fails, creation stops with an error. The optional second source (`/dev/urandom`) is skipped silently if absent or unhealthy, because a healthy primary source is sufficient.
- **MUST:** if the check fails, the screen says that the device generator failed its safety check, creates no words, and offers retry and the dice mode.
- **MUST NOT:** `java.util.Random`, `kotlin.random`, `Math.random`, `ThreadLocalRandom`, `shuffled()`, clock values or counters are used to create key material. `tools/check-rng.py` fails the build if one of them appears in app code. `SecureRandom` may only be used in the entropy module and for the non-secret quiz positions.
- **MUST:** entropy arrays are zeroed right after the words are derived. The JVM cannot guarantee full erasure, so the exposure window is kept as short as possible (plan §8.1).
- The phrase is generated only on the user's explicit action ("Generate with my phone" or "Create words"). It is never generated in the background or in advance.

## 3. Dice Mode

The user can create the wallet from their own dice rolls, so trust in the phone generator is not required.

### 3.1 Amount

A fair six-sided die gives log2(6) = 2.585 bits per roll.

| Phrase | Target | Minimum rolls | Entropy at the minimum |
|---|---|---|---|
| 12 words | 128 bits | 50 | 129.2 bits |
| 24 words | 256 bits | 100 | 258.5 bits |

The minimum is rounded up, so the entropy is never below the target. (Some tools accept 99 rolls for 256 bits, which is 255.9 bits and therefore below the target. This app does not.) Input is limited to 200 rolls. The screen shows the roll count and the bits collected.

### 3.2 Two derivations

- **Mixed (default).** `entropy = SHA-256("elektron-dice-mix-v1" || rolls || 32 bytes from SystemEntropy)`, truncated to 16 or 32 bytes. The result is at least as strong as the better of the dice and the phone generator. A careless user or a biased die cannot reduce it below the phone generator, and a bad phone generator cannot reduce it below the dice.
- **Dice only (verifiable).** `entropy = SHA-256(rolls)`, truncated to 16 or 32 bytes, with `rolls` the digits 1 to 6 as ASCII text and no separators. The user can recompute the words offline, for example `printf '%s' '<rolls>' | sha256sum` and take the first 32 hex characters (128 bits) or all 64 (256 bits), then convert to words with any BIP-39 tool. This mode is only as strong as the dice rolls. It is an advanced option and the screen says so.

### 3.3 Input checks

Humans make poor random numbers, so the input is rejected when it looks non-random. The thresholds are chosen so that honest dice are almost never rejected (measured below 1 percent in the unit test).

| Check | Rule | Honest false reject |
|---|---|---|
| Characters | only 1 to 6, whitespace and commas are ignored | none |
| Length | at least the minimum, at most 200 | none |
| Long run | 9 or more identical results in a row | about 3 in 100,000 |
| Repeated pattern | any sequence of 9 results appears twice | below 0.1 percent |
| Bias | chi-square over the six faces above 20.515 (p below 0.001, 5 degrees of freedom) | 0.1 percent |

On rejection the user keeps rolling or re-rolls. The text explains in plain words what looks wrong and never accuses the user.

### 3.4 User guidance (shown in the app)

- Use a real die and shake or roll it well. Do not make up numbers, do not copy a pattern.
- Type or paste the digits, then check the count.
- Do not photograph the dice or the screen. The dice rolls are as secret as the recovery phrase.
- The input screen blocks screenshots (`FLAG_SECURE`) like all phrase screens.

## 4. Touch Drawing (extra input)

The user can move a finger around a box on the screen until a bar is full. The movements are hashed into the phone generator output. This is an additional safeguard, not a replacement.

- **MUST:** the 128 bit floor always comes from the system generator (§2). The touch data is only added to it with `SHA-512("elektron-touch-mix-v1" || system draw || touch digest)`. A weak or predictable touch input therefore cannot lower the strength, and a failed system health check still stops creation (unit test `touchDataOnlyAddsToTheSystemFloor`).
- **Why it cannot be the only source:** finger movements are slow, correlated and partly predictable (smooth curves, screen edges, habits). An honest estimate per sample is a fraction of a bit, and nothing can measure true entropy from a sample. A bar that claimed "128 bits reached" would be a false promise. The app says plainly that the drawing is extra protection on top of the phone generator.
- **Per sample data:** position, time in nanoseconds and pressure of every touch event are hashed. Raw data is not stored and the pool is cleared when the step ends.
- **Progress bar (heuristic):** each accepted sample is credited with 0.5 bit, the bar is full at 256 credited bits (about 512 accepted samples, a few seconds of drawing). This is deliberately conservative: even if the touch data were worth a quarter of that, the combined result stays at least as strong as the system generator alone.
- **What does not count:** a sample counts only if it moved at least 4 pixels and at least 4 ms passed since the last counted sample. Repeating an identical step, drawing a perfectly straight line for more than 12 steps, holding still or tiny jitter earn nothing, and a scripted burst of events earns at most one sample.
- The step is optional. "Generate with my phone" without drawing is equally safe, because the floor does not depend on the drawing.

## 5. From Entropy to Wallet

```text
entropy (16 or 32 bytes) -> BIP-39 words (checksum) -> seed (PBKDF2-HMAC-SHA512, 2048 rounds)
-> master extended key (HMAC-SHA512, key "Bitcoin seed") -> descriptors -> node wallet
```

- The word list is the official BIP-39 English list (SHA-256 `2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda`), unchanged.
- BIP-39 and BIP-32 are verified against the published test vectors in the unit tests. Only the JDK's crypto providers are used, with no third-party code in the key path.
- The master key is checked to be in range (not zero, below the curve order). The extremely unlikely failure stops creation instead of continuing.
- The recovery phrase is never shown again after the backup quiz (plan §8.1). The quiz positions are not secret and use a normal secure generator.

## 6. Tests

- **Unit tests (CI, every change):** health check rejects zero, constant and repeating sources; a failing optional source is ignored; a failing required source stops creation; dice-only output equals the independent SHA-256 vector; mixed output changes with either input; the minimum rolls reach the target bits and one roll fewer does not; every dice rule fires on a crafted input; the false reject rate of honest dice stays below 1 percent over 4,000 trials.
- **Touch tests:** human-like motion fills the bar, standing still, a straight line and a scripted burst earn almost nothing, the output depends on every input (position, time), and a broken system generator still stops creation even with full touch data.
- **Distribution test:** thousands of phrases from the real generator are all distinct and the bit balance stays within one percent of one half. This is a smoke test, not proof of randomness.
- **Static check:** `tools/check-rng.py` in CI and as a Gradle `preBuild` dependency.
- **Release gate:** an independent review of `Entropy.kt` and the creation flow before the public beta (plan §13, Phase 5). A device test confirms that `SecureRandom` and `/dev/urandom` are readable on the supported Android versions and ABIs.

## 7. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Faulty device generator on a specific phone | Predictable phrase | Health checks, second source, dice mode, review |
| User types made-up dice results | Weak phrase in dice-only mode | Input checks, mixed mode as default |
| Screenshot or clipboard leak of dice or words | Fund loss | `FLAG_SECURE`, no clipboard writes of secrets |
| Compromised device while creating the wallet | Fund loss | Cannot be fully solved on a phone, documented in the security summary, advise a clean device and hardware wallets for large amounts |

## 8. Open Questions

| # | Question | Recommendation |
|---|---|---|
| R1 | Offer physical coin flips or card shuffles as other manual sources? | No in v1. Dice are well understood and checkable. |
| R2 | Hold the phrase in a `CharArray` or `ByteArray` instead of strings to allow zeroing? | Yes in Phase 4 hardening, as far as the Compose text layer allows. |
| R3 | Add a camera or sensor based source as another mixed input? | Touch drawing is included (§4) because users expect it. Camera and motion sensors stay out: they add permissions and code without raising the guaranteed floor. |

## 9. Checklist

- [x] System entropy with two sources, health checks, no weak fallback
- [x] Dice mode with 50 or 100 rolls, mixed and dice-only derivation
- [x] Input quality checks for dice
- [x] Touch drawing as extra mixed input, never counted toward the floor
- [x] Static check against weak randomness APIs in CI and Gradle
- [x] BIP-39 and BIP-32 test vectors
- [ ] Independent review of the entropy module
- [ ] Device test of the generator on supported Android versions
- [ ] Phrase handling in zeroable buffers (R2)
