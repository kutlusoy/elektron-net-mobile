# Elektron Net: `elektron-net-mobile` UX and Screen Guideline

- **Version:** 0.17 (draft)
- **Date:** October 19, 2026
- **Audience:** Android/Kotlin developers, UI designers, translators, usability testers
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
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

Bottom bar with four items, visible on every screen except the onboarding. Tapping a tab always leaves the page that is open (Send, Receive, Tools, details) and shows that tab:

| Tab | Content |
|---|---|
| **Wallet** (home) | Wallet switcher and menu, balance card, Receive and Send buttons, the eight most recent payments with "Show all payments" |
| **History** | The complete transaction history of the active wallet with search and filters (all, received, sent) |
| **Node** | Plain status, pause and resume, details, snapshot and storage information |
| **Settings** | Security, Network and battery, Appearance, About, Advanced |

- A small node status chip sits at the top of the Wallet screen (colored dot plus one short word: "Synced", "Syncing", "Paused", "Offline"). Tapping it opens the Node tab.
- Receive and Send open as full screens with a back arrow. They never nest more than one level deep.
- Android back always goes one step back. From the home screen it leaves the app and the node keeps running (foreground service).
- The testnet flavor shows its permanent bar above all of this (design system §6).
- **History per wallet:** every wallet has its own history. It lives in the node wallet, so it is complete from the moment the wallet exists on this phone and survives app updates and restarts. A wallet restored from its recovery phrase only shows payments that the network still stores (plan §2.1). The History tab loads all pages of the wallet (not just the newest ones), shows the last loaded list at once when the user switches wallets and refreshes it in the background.
- **Several wallets:** the Wallet tab starts with the name of the active wallet and a menu: Wallet details, Addresses and coins, Sign and verify messages, Sweep keys, Escrow and PSBT tools, and Other wallets. "Other wallets" lists all wallets, switches the active one, renames them and adds a new one (create or restore, same flow as the first run without the network and notification steps). Each wallet has its own recovery phrase, passphrase and receive address. Balance, history, Receive and Send always use the active wallet.
- **Coin control:** "Addresses and coins" lists every coin with its amount, address and confirmations, and a Frozen switch (a frozen coin is never selected automatically and stays frozen after a restart), plus the used addresses with label, received amount and transaction count. In the Send amount step "Choose coins" restricts the payment to the chosen coins, "Max" then spends exactly those coins, and a too small selection fails instead of pulling in other coins.

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
- **Implementation notes (v0.4):** the flow logic is pure Kotlin (`wallet/FirstRunModel.kt`) and tested on the JVM; the wallet is created through `WalletBootstrap` (plan §8.3) with BIP-39 words checked against the official word list, the master key derived from the seed (PBKDF2 and HMAC-SHA512 from the JDK, no third-party crypto) and the descriptors imported by the node, so no private key handling exists beyond the master extended key in memory during creation. Restore refuses to start before wallet creation when the node lacks `rescanutxoset` (X1), and shows a plain message. The app lock is an opt-in switch in Settings, Security (not a step of the flow): it asks for a strong biometric, or the screen lock on Android 11 and later, at a cold start and after the app was in the background for 30 seconds or more. Enabling it needs one successful prompt first. It protects the app screens only, sending still needs the wallet passphrase. The recovery phrase screens set `FLAG_SECURE`; the phrase is held in memory only while the flow runs and is dropped when it ends or the app is recreated.
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

- Status line at the top ("Waiting for confirmation" / "Confirmed, 12 confirmations"), amount, date, address(es), network fee, transaction id with Copy, "Open in explorer" (opens the first explorer of the Settings list in the browser) and, for an own pending payment, "Speed up" (fee bump, one passphrase prompt).

### 6.5 Node tab

- **Hero card:** one sentence of state in plain words and a status dot. Examples: "Your node is up to date." / "Catching up: 62 percent, about 20 minutes left." / "Paused: waiting for the charger." / "Stopped." / "Something went wrong. Tap to fix."
- **Startup and problems:** while the node starts, the card shows a progress bar, the node's own warm-up message, the time since start and the time since the last node message. After two minutes of silence, a slow start or one minute without peers it shows a plain warning that points to the live log. If the app cannot reach the node, it says why.
- **Buttons:** Pause or Resume, Stop or Start. Actions that change cost (use mobile data) say so.
- **Why paused?** The first reason from the policy engine is shown in words (plan §7), with a link to the matching setting.
- **Details** (always visible): tiles for validated blocks, known headers, peers, progress and prune height, then the cards Network (type, version, protocol, connections in and out, network active, initial download, ports), Blockchain and mining (difficulty, hash rate, mempool, storage) and Pruning (blocks kept, blocks until pruning starts, estimated date). Below them the connected peers: tap a peer for its details (ID, network, country, connection type, transport, protocol, synced headers and blocks, minimum ping, bytes received and sent, time offset, local binding). An optional live log (off by default) follows the node output and can be copied.
- **Connection chips:** next to the status dot and the word "Synced" (here and on the Wallet tab) small outlined chips show the networks in use with the number of connected peers: IPv4, IPv6, Tor (purple) and I2P (green). Tor and I2P show with 0 while they are switched on and waiting for peers. They wrap onto the next line on narrow screens.
- **Tor and I2P tiles:** below the Network card, one tile per switched-on network with its status, address, peers and ports (Tor: SOCKS and control port, relay port and bandwidth for relays; I2P: SAM bridge, incoming, relaying, bandwidth and share). Until the node has created the onion or I2P address the tile says "waiting for the address". Once it exists, Copy, Share and Show QR buttons under the address row copy, share or show it with its port (`<address>:<port>`), so others can add it as a peer.
- **Explainer link** "What is my node?" repeats the Welcome explainer.

### 6.6 Settings

| Group | Items (plain labels) | Maps to |
|---|---|---|
| Security | Change passphrase, Fingerprint or face unlock with a slider for the lock delay (immediately to 30 minutes), notification for incoming payments, "Allow screenshots" (temporary, red banner while on, ends when the app is closed or locked), Check my backup (the user types the phrase from paper, the app never stores or shows it again) | plan §8.1, S16 |
| Network and battery | "Use mobile data" (off), "Allow roaming" (off, only shown when mobile data is on), "Monthly mobile data limit" (slider from 1 GB to No limit), "Only while charging", "Only at night" (22:00 to 06:00), "Run in the background" (on), "How much the node may use" (while the app is open, balanced, always on) | `PolicySettings`, plan §7 |
| Tor | "Use Tor" with a status line, routing (next to the normal connections or everything through Tor), role (client only, relay, exit relay). A relay adds a bandwidth slider, a monthly limit slider, relay name, contact and port. An exit adds the exit policy (reduced or web only) and asks for confirmation | `NetworkSettings.tor`, `guideline-tor-and-i2p.md` |
| Stable addresses | A "New Tor address" and a "New I2P address" button in the cards. The node keeps its address until the user asks for a new one, after a confirmation that explains the effect | `AddressKeys`, `guideline-tor-and-i2p.md` §5b |
| I2P | "Use I2P" with a status line, accept incoming I2P connections, relay traffic for others with a bandwidth slider and the share for others | `NetworkSettings.i2p` |
| Sharing | "Accept incoming connections", "Share snapshots (Wi-Fi and charging only)" | `allowInbound`, `serveSnapshots`, plan §5.2 |
| Storage | "Storage saver" (fast pruning, applies at the next node start), "Stop the node when free space is below" (slider, 1 to 20 GB) | `fastprune`, `storageFloorBytes`, plan §3.2 |
| Block explorer | Add, edit, reorder and remove explorer addresses (default `https://mempool.elektron-net.org`), with the privacy sentence | plan §8.7, S23 |
| Unit | ELEK, mELEK, bit, lepton with the conversion table | plan §8.10 |
| Updates | Check for updates (asks GitHub for the newest tag) | S31 |
| Language | System language or any of the 15 shipped languages, each in its own name, applied at once | plan §3.5 |
| Appearance | Theme (five themes with swatches and a live preview), Light or dark | design system §2 |
| About | Version, wallet version, node version, node commit, node library hash, signing key fingerprint, copyright, MIT license, developers with their X accounts, repository and whitepaper links | distribution guideline §8 |
| Advanced | Storage saver, Resource profile, Expert mode and console, Live log, Address list, Export descriptors, Rescan, Reset | plan §3.2, §3.6 |

- Label and value rows wrap instead of squeezing: when the value does not fit beside the label it moves to its own line, and filter chips and action buttons flow onto the next line (no text is ever broken letter by letter).
- The history shows 10 payments per page, newest first, with Newer and Older buttons and "Page x of y". Search and filters work across all payments and reset the page.
- Every password field has a Show and Hide button. The first-run screen offers "Skip for now (node only)".
- Settings are grouped in tiles (cards), one per group, so the page can be scanned at a glance.
- Limits are sliders with discrete steps, the current value is shown beside the title and a limit that means "none" sits at the far end.
- Stopping or pausing the node asks for confirmation first, so a stray tap cannot switch the node off.
- Each toggle has a one-line description in plain words beneath it.
- Defaults are the recommended ones, so most users never open Network and battery. Tor and I2P are off until the user switches them on, their options appear only then.
- After "Restore from wallet file" succeeds the app switches to the new wallet at once and shows a card with "Wallet restored. It scans for your coins in the background." and OK. The error text of a failed restore shows the node's reason in a second line.
- The Wallets screen has "Reload wallets" (lists wallets found on the node, drops stale entries, loads unloaded ones, shows the counts) above "Restore from wallet file"; the list is never extended automatically.
- The Wallets screen has "Restore from wallet file" below "Add wallet". A backup without a passphrase leads to a dialog for a new passphrase, cancelling discards the restored wallet.

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
| U4 | Fiat value display | Built (Settings, between Language and Unit for amounts). Opt-in, off by default, with a note that it contacts a price service. See "Fiat value" below. |
| U5 | Address book and labels in the everyday flow | Address labels can be set in the Addresses list and are shown there and on coins. A dedicated address book is Phase 4. |

## 13. Checklist

- [ ] Navigation shell with Wallet, Node and Settings tabs and the node status chip
- [x] Wallet home with balance card, sync states, list and empty state (screens unbuilt on device, logic tested)
- [x] First-run flow with explainer, backup quiz and passphrase (logic and screens in place, app lock in Settings, restore waits for X1)
- [x] Receive with `elek:` QR, fresh address per visit, optional amount and note (screen unbuilt on device, logic tested)
- [x] Send flow with inline validation, fee choices, review, authentication, done (logic tested against a real node, screens unbuilt on device; QR scanning and biometric confirmation still open)
- [x] Transaction details with explorer link and fee bump (screen unbuilt on device, logic tested against a real node)
- [x] Node tab with progress, estimate, peers, telemetry, warnings and optional live log (screen unbuilt on device)
- [x] Settings groups mapped to policy settings, defaults recommended (screen unbuilt on device)
- [ ] Error and state table implemented, no raw RPC text
- [ ] Previews and screenshot tests per state, theme and longest locale
- [ ] Two usability rounds done and findings applied

### Fiat value

- **Settings card "Fiat value"** sits between "Language" and "Unit for amounts". It has one switch. Only when it is on do the other settings appear. Off: the app shows ELEK only and makes no price request.
- **Price service:** a list of ready-made services (Binance, Coinbase, Kraken, KuCoin, MEXC, Bitget, Gate.io, XeggeX, TradeOgre, CoinGecko). "Add service" creates an own entry from a name, an https address and the path of the price in the JSON answer (`data.price`, numbers pick list entries, `*` takes the first entry of an object). The address and the path may contain `{BASE}` and `{QUOTE}` (`{base}` and `{quote}` for lowercase), which the selected pair replaces. The placeholders are optional, so a fixed address can stand in for a market while ELEK has no listing: address `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies={quote}` with the path `bitcoin.{quote}` shows the Bitcoin price in the chosen currency as if it were the ELEK price. Own entries can be edited and deleted, the ready-made ones cannot.
- **Currency pair:** a list of common codes (EUR, USD, GBP, CHF, TRY, JPY, USDT, BTC) and "Add own pair" for any other code. The order is a choice: `ELEK_EUR` (the service quotes the price of 1 ELEK) or `EUR_ELEK` (the service quotes the price of 1 EUR in ELEK, the app inverts it).
- **Main display:** ELEK in the unit of the user, or the fiat amount. The other value is shown smaller under or beside it, marked with an approximation sign. This applies to the balance, the history rows, the transaction details and the amount in Send.
- **No market, no answer:** if the service does not know the pair, answers with an error or cannot be reached, only ELEK is shown, whatever the main display is. A rate older than six hours is dropped. The rate is refreshed every five minutes while the app is open and by "Refresh now".
- **Privacy:** the request goes directly from the phone to the service over https, so the service sees the IP address. Only the pair is sent, never wallet data. The settings card says so.
- **Send and Receive:** with a rate at hand, the amount step of Send and the amount option of Receive have a switch "Enter amount in" with the unit of the user and the fiat currency. A typed fiat amount is converted at the current rate, the converted ELEK amount is shown under the field (marked with an approximation sign) together with the rate, and only the ELEK amount goes into the transaction or the payment link. Switching converts the number already typed. Review, confirmation and the done page show the fiat value of the amount, the fee and the total in small text. If the rate disappears while fiat entry is on, the field is cleared and the entry returns to ELEK.
- **Symbol of ELEK:** a text field in the Fiat value card (default `ELEK`) sets the ticker used in the market name (`ELEK_EUR`, `EUR_ELEK`) and in the `{BASE}` and `{QUOTE}` placeholders. It can be changed when an exchange lists ELEK under another symbol.
- **CoinGecko stand-in for tests:** an own service whose address starts with `https://api.coingecko.com/api/v3/simple/price` needs no path. Only `ids=bitcoin` is accepted, anything else (for example `ids=solana`, or a list of ids) is rejected with a message and cannot be saved. The app reads the Bitcoin price in the chosen currency (`bitcoin.<currency>`) and treats it as the ELEK price. The address can list several currencies (`vs_currencies=usd,eur,try`).
- **Request limits:** free price services limit requests (CoinGecko roughly 10 to 30 per minute). The app asks only while it is open, at most every five minutes, waits two seconds after a settings change before it asks, never repeats the same request within a minute (this also applies to "Refresh now") and stops asking for 15 minutes after an HTTP 429 answer, with the message that the service limits requests.
- **Fallback services:** under the price service, every other service has a switch. The ones switched on form an ordered list (shown as 1., 2., ...). If the chosen service fails, is rate limited or has no such market, the app asks the fallbacks one after the other and stops at the first rate it gets. They are never asked in parallel and not at all while the chosen service works. A service that answered 429 is skipped for 15 minutes. The status line names the service the rate came from. If nobody answers, the last rate stays for up to six hours, then only ELEK is shown.
- **CoinGecko:** the ready-made entry asks `simple/price?symbols=<ticker>&vs_currencies=<currency>` and takes the first coin of the answer. It has no market pair, so it returns the price of 1 ELEK in the currency whatever the pair order is. A symbol on CoinGecko can belong to more than one coin, so once ELEK is listed, check that the price shown is the right one. Addresses and paths may also use `{ELEK}`, `{elek}`, `{FIAT}` and `{fiat}` (the symbol and the currency regardless of the order). An address without `{BASE}` and `{QUOTE}` is always read as the price of 1 ELEK in the currency.
- **Peers added by hand (Tor and I2P tiles):** each tile has a list of peers, a field for an address, "Add" and "Scan QR". Only onion v3 and b32.i2p addresses are accepted, the app never takes free text into the node arguments. I2P addresses always get port 0. An added peer is saved, the running node connects at once (`addnode ... add`) and every start passes it as `-addnode`. Adding or removing a peer never restarts the node or the Tor and I2P helpers. A peer of a network that is switched off is kept but not passed to the node. The QR code of an address is the plain address text, so the scanner of another phone can add it directly.
- **Node only mode:** the Node tab loads the peer list, the network tiles and their addresses also when no wallet exists yet.

### Balance and reused addresses

- Wallets are created with `avoid_reuse`. Core then counts coins on reused addresses under `used` and keeps them out of `trusted` and out of automatic coin selection. They still belong to the wallet.
- The balance on the Wallet tab is `trusted` plus `used`, so it matches the sum of all coins. A small line under it says how much of it sits on reused addresses.
- Send shows the amount that is selected automatically (`trusted`) and a hint with the amount on reused addresses, which can be spent by picking the coins under Coins. Coins on one reused address are also used first when a payment fits into that address (reused-coin-first).
- **Setting "Spend coins on reused addresses automatically"** (Settings, Security, on by default, so coins of an imported wallet that sit on reused addresses are fully usable; a user who does not want to mix them switches it off). Core's funding ignores `avoid_reuse`, so the app enforces it: with the setting off, coins on reused addresses are locked for the moment a payment is prepared (and unlocked again right after, coins frozen by the user stay frozen), so they are never mixed with other coins. A payment that fits into one reused address is still paid from it first. With the setting on (the default), they count as available in Send and are used when the other coins do not cover a payment or when everything is sent. The hint under the switch warns that this can link addresses.

### Escrow and PSBT per wallet

- Every escrow belongs to the wallet whose seed its key comes from (`owner` in the record). The Escrow list shows only the escrows of the active wallet and says whose they are. Switching the wallet switches the list. Escrows saved before this field belong to the first wallet (`elektron`), because their keys were always derived from it.
- The key of a new escrow is derived from the active wallet, the account number counts per wallet, and the wallet names of escrows (`escrow-<n>`) are unique across all wallets. A name that already exists in the node's wallet folder is skipped, so a leftover folder never blocks a new escrow.
- Removing an escrow unloads its wallet (without load on startup) and deletes its folder. If the folder cannot be deleted, the entry stays and an error is shown, so the removal can be repeated.
- The PSBT tools look at the active wallet and its own escrows only: ownership of inputs and outputs, the net amount per wallet and the wallets that can sign are limited to those. A PSBT that belongs to another wallet shows "no input of this wallet" until that wallet is active.

- **Status notification:** next to the text ("Synchronized at block N, M peers") the notification shows the peers by network as its sub text and in the expanded text, for example "IPv4: 9 · IPv6: 2 · Tor: 1 · I2P: 1". Only networks with at least one peer are listed.
- **Free threshold:** the number of keys that must approve a payment is chosen freely from 1 to the number of keys (with at least two keys, up to 15). The control "How many keys must approve a payment" with - and + sits above the create button as soon as one key of another person has been added. A choice of 1 shows a warning, because then every key holder can spend alone. The intro no longer states a fixed "two of three".
- **Explaining the escrow flow:** the Escrow list shows a numbered "How an escrow works" text under the intro. The create dialog says that it only creates the user's own key and that the number of approvals is chosen later. The detail screen is labeled "Step 1 of 3" (share your key), "Step 2 of 3" (add the keys of the others and choose the number of approvals) and the create button is step 3. It states that everyone must use the same keys and the same number, and the check code and deposit address must be identical for everyone. Nothing has to be set in advance: the number of approvals is chosen in step 2 and is fixed once the escrow wallet is created.

### Logs and storage use

- **Log cleaning** (Settings, Storage): "Keep logs for" (1, 3, 7, 14 or 30 days, default 7) and "Maximum size per log file" (1, 5, 10, 25 or 50 MB, default 10). Once an hour, and on demand with "Clean logs now" (which shows how much was freed), the app trims `debug.log` of the node and any log file of Tor and I2P. Lines older than the limit are removed, a file above the size is cut to half of the limit at a line start. The files are rewritten in place because the node keeps them open. Wallet files are never touched.
- **Live logs on the Node tab:** the in-memory buffers are capped (1000 lines for the node, 500 for Tor and I2P), the node log shows the last 200 lines, the Tor and I2P tile logs scroll inside a box of limited height, and each has a "Clear" button that empties the buffer.
- **Storage use card** (Node tab, the last card of the telemetry, right above the connected peers): lists blocks, headers and block index, UTXO set, snapshots (when present), every wallet on its own (escrow wallets included), Tor data, I2P data, logs, other node data and the app files, and the total. It is measured on the phone and refreshed about every 30 seconds while the Node tab is open. The "Blockchain storage" line above it stays the figure the node reports itself.
- **Order of the Node tab:** status, pause and stop, explainer, figures, live log, telemetry cards, storage use, connected peers and, as the last card, the maintenance card with "Reset chain data", so the destructive button is never in the way.

### Number format

- Settings, "Unit for amounts", has "Thousands separator" (on by default), "Character for thousands" and "Character for decimals" with an example line. The defaults come from the language and the country (`Locale`, so `1,234,567.89` in English and `1.234.567,89` in German). Overrides: comma, period, space (no-break) and apostrophe for thousands, comma or period for decimals. If both would be the same character, the thousands character is changed.
- All shown amounts use it (`Amount.display`, fiat values and the unit preview). Everything that is read back uses the plain form (`Amount.plain`): the amount fields in Send and Receive, payment links and CSV. The amount fields accept a comma or a period as decimal separator whatever the display setting is.

### Escrow deposit and payout

- On the escrow page the deposit address has the button "Deposit from my wallet". It opens the normal Send screen of the active wallet with the address filled in. "Pay out of the escrow" opens the Send screen of the escrow wallet: the available amount is the escrow balance and the result is a payment request that needs the approvals of the others. Each button has a one-line description below it ("The buyer pays into the escrow." and "The seller takes money out of the escrow into their own wallet. For this the seller enters a payout address, usually an address of their own wallet. The other people must approve."), and the payout screen says on top that it pays out of the escrow and not out of the user's own wallet.
