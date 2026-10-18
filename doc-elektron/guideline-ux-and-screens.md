# Elektron Net: `elektron-net-mobile` UX and Screen Guideline

- **Version:** 0.12 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, UI designers, translators, usability testers
- **Reference implementation:** this repository `app/src/main/java/org/elektronnet/mobile/ui/` (screens), `app/src/main/java/org/elektronnet/mobile/runtime/` (status headline) and `policy/` (reasons shown to the user)
- **Related repositories:** [`elektron-net-electrum`](https://github.com/kutlusoy/elektron-net-electrum) (the project's light wallet; only its address safety wording is kept consistent, no code or screens are taken)
- **See also:** [`guideline-entropy-and-randomness.md`](./guideline-entropy-and-randomness.md), [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (§3.3, §3.4, §8), [`guideline-ui-design-system.md`](./guideline-ui-design-system.md), [`guideline-expert-console.md`](./guideline-expert-console.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Decisions

The app must be intuitive and simple for everyday wallet users. The layout follows the common conventions of mobile wallets on the market (decided October 18, 2026). Only the general UX pattern is used as inspiration. No code, screens, icons, assets or texts of any specific product are taken, neither from commercial nor from open source wallets, and no product is named as a template. The node is the product's strength, but a user who only wants to receive and send never has to understand it.

| Topic | Decision |
|---|---|
| Audience | Everyday wallet users first, advanced users second. Every advanced feature is reachable but never in the way. |
| Model | Familiar wallet pattern: balance card, two big actions (Receive, Send), transaction list. |
| Node | Visible as a small status indicator on the home screen and as its own tab, explained in plain words. Details are one tap deeper. |
| Language | Plain, short, no jargon on the wallet side (rules in §9). |
| Safety | The safe path is the default path and costs no extra taps for normal use. Warnings appear only when something is actually risky. |

## 2. Reference Patterns

The inspiration is the shared vocabulary that everyday users already know from mobile wallets in general:

| Convention | How it is used here |
|---|---|
| One balance card at the top of the home screen | §6.1 |
| Receive and Send as the two primary actions, large and side by side | §6.1 |
| Transaction list below, grouped by day, with in and out icons | §6.1 |
| Receive screen with a large QR code, copy and share | §6.2 |
| Send as a short guided flow ending in a review screen | §6.3 |
| Recovery phrase shown once, confirmed with a short quiz | §5 |
| Settings as a short grouped list with toggles | §6.6 |
| Small connection indicator on the home screen | §4 |

The goal is familiarity with these conventions, not a copy of any product. Screens, icons, texts and flows are written for this project and for its own node model. Visual style comes from the design system guideline.

## 3. Principles

1. **One primary action per screen.** The thing the user most likely wants is the big, obvious control.
2. **Show state, hide mechanism.** "Catching up with the network, 62 percent" instead of block heights and header counts. The numbers are available under Details.
3. **Never a bare zero when the truth is unknown.** While the node is not synchronized the balance carries a clear note (plan §8.3).
4. **Progressive disclosure.** Basic screen first, "More" or "Details" for the rest. Expert mode and the console live under Settings, Advanced.
5. **Three taps for the daily jobs.** Show the receive address, start a payment, see why the node is paused.
6. **Plain errors.** What happened, what to do next, one button for it.
7. **Reversible where possible.** Confirm before sending and before destructive actions, never otherwise.
8. **Fast first paint.** The home screen renders from cached data immediately, then updates.

## 4. Navigation

Bottom bar with three items, always visible except during Send and the onboarding:

| Tab | Content |
|---|---|
| **Wallet** (home) | Balance card, Receive and Send buttons, transaction list |
| **Node** | Plain status, pause and resume, details, snapshot and storage information |
| **Settings** | Security, Network and battery, Appearance, About, Advanced |

- A small node status chip sits at the top of the Wallet screen (colored dot plus one short word: "Synced", "Syncing", "Paused", "Offline"). Tapping it opens the Node tab.
- Receive and Send open as full screens with a back arrow. They never nest more than one level deep.
- Android back always goes one step back. From the home screen it leaves the app and the node keeps running (foreground service).
- The testnet flavor shows its permanent bar above all of this (design system §6).

## 5. First Run

```text
Welcome  ->  Create new wallet  /  Restore wallet
Create:  explain recovery phrase  ->  generate (phone, drawing or own dice)  ->  show 12 or 24 words  ->  quiz (3 words)  ->  choose passphrase  ->  optional biometric  ->  Home
Restore: enter 12 or 24 words  ->  choose passphrase  ->  "Restore starts when your node has caught up"  ->  Home with progress
```

- **Welcome:** one sentence on what the app is ("Your own wallet with your own copy of the network. No account, no server.") and the two buttons. A short link "How does this work?" opens a one-screen explainer about the node (what it does, that it syncs in the background, that it uses data and battery, that the user controls it).
- **Network question, once:** "Sync only on Wi-Fi? (recommended)" with a short note about mobile data. This sets `allowMetered`. The default is Wi-Fi only.
- **Notification permission:** an explainer screen before the system dialog: "The node runs in the background and shows its status in a notification. You can pause or stop it there."
- **Randomness:** the default is the phone generator with health checks. An optional step lets the user draw on the screen until a bar is full, which adds extra randomness on top of the phone generator. An advanced option lets the user roll their own dice (50 rolls for 12 words, 100 for 24), either mixed with the phone generator or dice only. A failed generator check creates no phrase and says so. Full rules: [`guideline-entropy-and-randomness.md`](./guideline-entropy-and-randomness.md).
- **Recovery phrase:** shown on a screen with screenshots blocked (`FLAG_SECURE`). Wording: "Write these words down on paper. Anyone with them can take your coins. We cannot recover them for you." The quiz asks for three random positions and does not allow skipping in v1. The phrase is never shown again after the quiz (plan §8.1). Settings, Security has a "Check my backup" action that repeats the quiz.
- **Passphrase:** the user chooses a wallet passphrase that is needed to send. The text explains that this is not the recovery phrase. A strength hint is shown, no composition rules beyond a minimum length. Biometric unlock is offered afterwards as an opt-in switch with the trade-off sentence from plan §8.1.
- **No waiting for the node (v0.12):** the user never waits for the node during setup. After the passphrase the app opens the wallet at once and a banner says "Your wallet is being set up. This happens as soon as your node has started." The recovery phrase and the passphrase stay in memory only. A background step keeps trying every second until the node answers, treats the node's start-up phase as "keep waiting" and not as an error, creates the wallet and then the banner disappears. If the node reports a real error, the banner shows it with "Try again" and "Start over". If the app is closed before the wallet exists, nothing was created and no coins can be affected, and the next start shows the welcome screen again, where "Restore wallet" with the written phrase gives the same wallet.
- **Restore:** the wallet is created at once from the phrase, without waiting for synchronization. The search for older coins (`rescanblockchain` from the prune height, plus `rescanutxoset` when the node has it) starts automatically when the node has caught up, while the passphrase is still in memory. If the app was closed in between, a button "Enter passphrase to finish restoring" asks for the passphrase again. Until `rescanutxoset` exists in the node (X1) the banner says that older payments cannot be restored yet.
- **Restore while the node is syncing:** restore needs a synchronized node (plan §8.3). The screen says so plainly and shows the same progress card as the home screen. The app keeps the entered phrase only until the restore finishes if the user chooses "Restore automatically when ready", otherwise the user returns later and enters it again. Decision to confirm (§12, U3).
- **Implementation notes (v0.4):** the flow logic is pure Kotlin (`wallet/FirstRunModel.kt`) and tested on the JVM; the wallet is created through `WalletBootstrap` (plan §8.3) with BIP-39 words checked against the official word list, the master key derived from the seed (PBKDF2 and HMAC-SHA512 from the JDK, no third-party crypto) and the descriptors imported by the node, so no private key handling exists beyond the master extended key in memory during creation. Restore refuses to start before wallet creation when the node lacks `rescanutxoset` (X1), and shows a plain message. The optional biometric step is not built yet. The recovery phrase screens set `FLAG_SECURE`; the phrase is held in memory only while the flow runs and is dropped when it ends or the app is recreated.
- **A new wallet is usable immediately** for receiving, even while the node syncs. A first-time user sees a working Receive screen in under a minute.

## 6. Screens

### 6.1 Wallet (home)

- **Balance card:** large amount with the unit ELEK, trailing zeros trimmed, up to 8 decimals. Below it, smaller: "Pending: x ELEK" when incoming or outgoing amounts are unconfirmed.
- **Sync note inside the card** while not synchronized: "Catching up with the network (62 percent). Your balance may not be complete yet." with a thin progress bar. When the node is paused for a policy reason the note says why in one line ("Paused: waiting for Wi-Fi") with a Resume button if the user can override it.
- **Buttons:** Receive and Send, equal size, under the card. Send is disabled with an explanation while the node is not synchronized (see §6.3).
- **Transaction list:** grouped by day, one row per transaction: icon (arrow in or out), label or short address, amount with sign, status word for unconfirmed. Pull to refresh does nothing visible except a state check. Empty state: "No payments yet. Tap Receive to get your first address."
- **History limit:** below the list a quiet line: "Older payments than about 137 days are not stored on the network." with a "Why?" link (plan §8.3).
- **Implementation notes (v0.8):** balance and history come from the wallet RPC (`getbalances`, `listtransactions`), refreshed every 8 seconds while synchronized and every 15 seconds otherwise, and only while the app is visible. `listtransactions` returns one entry per output, so entries are grouped per transaction: the net amount includes the network fee once, a transaction whose sent and received parts cancel out is shown as "Moved within your wallet", and generation entries are "Mining reward". Amounts are converted from the node's decimals without floating point. Confirmation words: 0 is "Waiting for confirmation", 1 to 5 shows "n of 6", 6 and more shows nothing. A balance of zero while the node is not yet synchronized and no history exists shows "Waiting for your node to catch up" instead of 0 (plan §8.3). Unconfirmed incoming and outgoing amounts show as "Pending". The list shows the latest 100 transactions grouped by day.
- **Tools entry:** a quiet text button under Receive and Send opens the Tools page (escrow, payment files, sweep, messages). These functions are for experienced users and are described in [`guideline-escrow-psbt-and-tools.md`](./guideline-escrow-psbt-and-tools.md).
- **Not on this screen:** block height, peer count, fee rates, UTXO count, addresses with balance. Those are under Node and Settings.

### 6.2 Receive

- Large QR code of the `elek:` URI (plan §8.4.1), the address below in grouped monospace with the first and last six characters emphasized (design system §4), Copy and Share buttons.
- Optional "Request an amount" and "Add a note" fields, collapsed. They update the QR.
- A fresh address is shown for every visit once the previous one has received a payment. The user never has to think about address reuse.
- Always native SegWit `be1...`. No address type switch in the everyday flow (Settings, Advanced).
- **Implementation notes (v0.7):** the address comes from `getnewaddress` with type `bech32`. The current address is kept and shown again until `getreceivedbyaddress` (0 confirmations, so unconfirmed payments count) is above zero, then a new one is created. The URI builder and parser (`PaymentUri`), amount handling (decimal ELEK, at most 8 decimals, comma accepted only in the input field) and address classification (`AddressCheck`, Bech32 and Bech32m with checksum, Base58 with checksum, foreign network detection) are pure Kotlin with test vectors from BIP-173 and a fuzz test. The QR text is uppercased (alphanumeric mode, a smaller code) only when no note is present. The QR is drawn from a ZXing core matrix as black on white regardless of theme. Copy puts only the address on the clipboard and clears it after 60 seconds if unchanged. The note is sent as the URI `message` parameter and is not stored.
- Works while syncing. A short note: "Payments to this address will appear when your node has caught up."

### 6.3 Send

```text
1 Recipient  ->  2 Amount  ->  3 Fee  ->  4 Review  ->  5 Confirm  ->  6 Done
```

- **Recipient:** one field with Paste and Scan buttons. Accepts a plain address or an `elek:` link. Inline validation as the user types: valid (check mark with the shortened address), foreign network (specific message, plan §8.5), Bitcoin-style legacy address (collision warning with "Use anyway" requiring a tap), unknown format (what is wrong in plain words).
- **QR scanner:** the Scan button opens a camera view that reads QR codes with CameraX and ZXing, both open source, so the build stays free of Google Play Services and F-Droid compatible. It asks for the camera permission only when the user taps Scan, shows a plain explanation if it is denied, reads one code and closes. Nothing is saved or sent. The same scanner is used for payment links in Send, payment files (PSBT) and escrow key lines in the Tools, and private keys in Sweep. A single QR code holds about 2,000 characters, so only small payment files fit. Larger files use Copy or Share. Animated multi-part codes are planned.
- **Amount:** big numeric keypad, unit ELEK, a Max button, remaining balance shown. Over-balance is explained ("You have x ELEK available").
- **Fee:** three choices with a time estimate each ("Slow, about 30 minutes", "Normal, about 5 minutes", "Fast, next block"), derived from `estimatesmartfee` (plan §2.2 #5). "Custom fee" and coin selection are behind "More options".
- **Review:** recipient (shortened with emphasized ends), amount, network fee, total. Everything the PSBT contains is checked before this screen is shown (plan §8.4). A single primary button "Send".
- **Confirm:** biometric prompt if enabled, otherwise the passphrase field. The wallet is unlocked only for the signing window (plan S4).
- **Done:** check mark, amount, "View transaction", "Done". The new transaction appears at the top of the list as pending.
- **Implementation notes (v0.9):** `SendEngine` builds the payment with `walletcreatefundedpsbt` (explicit fee rate, replaceable, native change from the wallet), decodes it and checks that the destination appears exactly once with the exact amount, that every other output is the wallet's own change address (`getaddressinfo` with `ischange`), that inputs minus outputs equals the fee, and that the effective fee rate is within a tolerance of the chosen one. The same check runs again immediately before the wallet is unlocked and once more on the signed PSBT, and the review must be identical each time (plan S5). The unlock lasts 10 seconds and `walletlock` runs in a `finally` path, so a wrong passphrase, a mismatch or a rejection leaves the wallet locked and nothing sent. "Send everything" uses `subtractFeeFromOutputs`. Fee choices come from `estimatesmartfee` for 30, 5 and 1 blocks (the block time is 60 seconds), kept in ascending order and never below the larger of the relay minimum, the mempool minimum and the wallet minimum of 1 lep/vB. While no estimate exists the lowest accepted fees are used and the screen says so. Payment links open the same screen through the `elek:` or `elektest:` intent. The send screens block screenshots and ignore touches from overlay windows (S3, S8), and the wallet is locked whenever the app leaves the foreground.
- **While not synchronized:** the Send button explains "Sending needs a synchronized node. Your node is at 62 percent." and links to the Node tab. v1 has no override. Rationale: an incomplete view of the chain can show wrong balances and invite double-spend mistakes. Decision to confirm (§12, U2).
- Failure messages map from node error codes to plain text with one suggested action (plan §3.5).

### 6.4 Transaction details

- Status line at the top ("Waiting for confirmation" / "Confirmed, 12 confirmations"), amount, date, address(es), network fee, transaction id with Copy, and "Open in explorer" only if the explorer link setting is on (plan X7).
- "Speed up" (fee bump) for own pending outgoing transactions arrives in Phase 4.

### 6.5 Node tab

- **Hero card:** one sentence of state in plain words and a status dot. Examples: "Your node is up to date." / "Catching up: 62 percent, about 20 minutes left." / "Paused: waiting for the charger." / "Stopped." / "Something went wrong. Tap to fix."
- **Buttons:** Pause or Resume, Stop or Start. Actions that change cost (use mobile data) say so.
- **Why paused?** The first reason from the policy engine is shown in words (plan §7), with a link to the matching setting.
- **Details** (collapsed): block height and known headers, peers, disk used, retained window, last checkpoint snapshot, whether this phone is serving snapshots, version and Core tag. Live log and console are not here, they are under Settings, Advanced.
- **Explainer link** "What is my node?" repeats the Welcome explainer.

### 6.6 Settings

| Group | Items (plain labels) | Maps to |
|---|---|---|
| Security | Change passphrase, Fingerprint or face unlock, Check my backup (the user types the phrase from paper, the app never stores or shows it again) | plan §8.1, S16 |
| Network and battery | "Use mobile data" (off), "Allow roaming" (off, only shown when mobile data is on), "Monthly mobile data limit", "Only while charging", "Run in the background" (on), "Active hours" | `PolicySettings` fields |
| Sharing | "Help other phones sync (Wi-Fi and charging only)" | `serveSnapshots`, plan §5.2 |
| Appearance | Theme, Light or dark, Glow effects (Off, Subtle, Full), Language | design system §2, §3, plan §3.5 |
| About | Version, Core version and commit, signing fingerprint, licenses, links | distribution guideline §8 |
| Advanced | Storage saver, Resource profile, Expert mode and console, Live log, Address list, Export descriptors, Rescan, Reset | plan §3.2, §3.6 |

- Each toggle has a one-line description in plain words beneath it.
- Defaults are the recommended ones, so most users never open Network and battery.

## 7. States and Errors

| Situation | What the user sees | Action offered |
|---|---|---|
| First sync running | Progress card on Wallet and Node, estimate in minutes | Nothing required, "Keep running in the background" tip once |
| No network | "No internet connection" on the sync note | none |
| Mobile data not allowed | "Waiting for Wi-Fi" | "Use mobile data once" (explains data use) |
| Battery low or device warm | "Paused to protect your battery" or "Paused: device is warm" | none, resumes automatically |
| Storage low | "Not enough free space. Free up x GB to continue." | Open storage settings |
| Node crashed | "Your node stopped unexpectedly" | Restart, and for a damaged database a guided repair (plan §4.2 item 4) |
| Wrong passphrase | "That passphrase is not correct" | Try again, no lockout text that leaks state |
| Recovery needs sync | "Restore starts when your node has caught up" | Restore automatically when ready |
| Foreign or unknown address | Specific message, see §6.3 | Edit address |

- No raw RPC messages, error codes or stack traces in the wallet UI. The English technical detail is available under "Details" for support.
- Errors are never shown only in color (design system §4).

## 8. Layout, Touch and Accessibility

- Single column, 16 dp side gutters, cards with 12 dp corners (design system). The primary action sits in the lower third of the screen for one-handed use.
- Touch targets at least 48 dp, body text 16 sp, support for font scaling up to 200 percent, TalkBack labels on every control and icon-only button, focus order follows reading order.
- Right-to-left layouts mirror, amounts and addresses stay left-to-right (design system §5).
- Motion is minimal and honors "Remove animations".
- Haptic feedback on successful scan and on send.

## 9. Writing Rules

- Short sentences, active voice, second person ("Your node is up to date").
- Say **recovery phrase**, never "seed" or "mnemonic". Say **network fee**, not "miner fee" or "sat/vB". Say **node** only after the explainer has introduced it, with "your own copy of the network" next to it on first use.
- Amounts are always "x ELEK". The unit lep appears only in the advanced fee screen.
- Do not blame the user. "That does not look like an Elektron address" rather than "Invalid input".
- Buttons are verbs: Send, Receive, Copy, Pause. No "OK" for decisions.
- All strings are resources with plurals and positional arguments (plan §3.5). The glossary per language fixes how "node", "recovery phrase" and "network fee" are translated.

## 10. Implementation Notes

- Screens are Compose with state hoisted into view models that read `NodeHost.latest` (runtime snapshot), the settings store and a wallet repository. UI never calls RPC directly.
- Each screen has Compose previews for loading, synced, syncing, paused and error states, per theme (design system §8).
- Screenshot tests cover the home screen in every state, Send review, Receive, and the first-run quiz, in the longest locale.
- Phase mapping: navigation shell, Wallet home with sync states and Node tab belong to Phase 1; first run, Receive, Send, history and details to Phase 2; Settings screens with Network and battery to Phase 3; fee bump and address list to Phase 4.
- The current `HomeScreen` in this repository is a placeholder for the node card and is replaced by the structure above.

## 11. Usability Tests

Five non-technical testers per round, think aloud, tasks without help:

1. Create a wallet and back up the recovery phrase.
2. Show the address to a friend (Receive).
3. Send a small amount to a given address.
4. Find out why the node is not syncing and fix it (for example Wi-Fi only).
5. Restore a wallet from the phrase on a second phone.

Success: at least 4 of 5 complete each task unassisted, create-and-back-up under 3 minutes, send under 1 minute after the node is synchronized, no tester confuses the recovery phrase with the passphrase after the explainer. Findings feed back into this guideline before each beta.

## 12. Open Decisions

| # | Question | Recommendation |
|---|---|---|
| U1 | Tab label for the node: "Node" or "Network"? | "Node", because it is the product's identity and the explainer introduces it. Revisit after the first usability round. |
| U2 | Sending while not synchronized | Not allowed in v1, with a clear explanation and a link to progress. |
| U3 | Restore while syncing: keep the entered phrase in memory to auto-restore, or ask again later? | Ask again later by default (the phrase is never stored). Offer "Restore automatically when ready" only as an explicit opt-in that keeps the phrase in memory while the app is open. |
| U4 | Fiat value display | Out of scope for v1 (plan §1). If added later, opt-in, with a note that it contacts a price service. |
| U5 | Address book and labels in the everyday flow | Labels appear in Receive notes and transaction rows. A dedicated address book is Phase 4. |

## 13. Checklist

- [ ] Navigation shell with Wallet, Node and Settings tabs and the node status chip
- [x] Wallet home with balance card, sync states, list and empty state (screens unbuilt on device, logic tested)
- [x] First-run flow with explainer, backup quiz and passphrase (logic and screens in place, biometric step still open, restore waits for X1)
- [x] Receive with `elek:` QR, fresh address per visit, optional amount and note (screen unbuilt on device, logic tested)
- [x] Send flow with inline validation, fee choices, review, authentication, done (logic tested against a real node, screens unbuilt on device; QR scanning and biometric confirmation still open)
- [x] Transaction details (explorer link and fee bump still open)
- [ ] Node tab in plain language with details collapsed
- [ ] Settings groups mapped to policy settings, defaults recommended
- [ ] Error and state table implemented, no raw RPC text
- [ ] Previews and screenshot tests per state, theme and longest locale
- [ ] Two usability rounds done and findings applied
