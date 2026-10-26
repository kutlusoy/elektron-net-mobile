# Elektron Net: `elektron-net-mobile` Release m0.1.7

- **Version:** m0.1.7
- **Date:** October 25, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.7` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.6.md`](./CHANGELOG-Release-m0.1.6.md), [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.1.7`. It continues from `m0.1.6`, the wallet version is 0.1.7.

## Project status

- The project status is raised from alpha to beta in the README, the whitepaper and the release notes of the release workflows. The wallet has been tested in practice and runs stably, so it can be used for everyday amounts. Larger holdings belong in a cold wallet, and an independent security review is still open.

## Security review follow-up

Two AI-assisted reviews of release m0.1.5 are kept in `doc-elektron/` as [`security-review-2026-10-25-grok.md`](./security-review-2026-10-25-grok.md) and [`security-review-2026-10-25-gpt.md`](./security-review-2026-10-25-gpt.md). They are static reviews by AI models, not a replacement for the open independent human review. Finding names below are the ones used in the reviews (Grok H1 to L4, GPT H1 to L1).

### Implemented

- **Passphrase policy (Grok H2):** a new passphrase needs at least 10 characters (before 8) and must be rated fair or strong. Very common passwords, also with digits or symbols appended, one repeated character and plain runs such as 1234567890 are refused. Existing passphrases are not touched and keep working, the rule only applies when a passphrase is set or changed (new wallet, restore, restore from a wallet file, change in the console). A full strength estimator in the style of zxcvbn was not added: it needs a large word list in the APK and the simple rules plus the block list cover the cases that matter for a passphrase that is typed on a phone.
- **Screenshot switch (Grok M3):** the protection of all sensitive screens stays on. "Allow screenshots" in Settings, meant for recordings and tutorials, now needs a warning dialog and a check with fingerprint, face or device lock, so it cannot be switched on by accident. If the phone has none of them the switch stays off. It switches itself off after 15 minutes, when the app is closed or locked, and the red banner "Screenshots are on" stays visible while it is on. Switching it off needs no check.
- **Node arguments (GPT H1):** extra start arguments can no longer set or reset `-rpc*`, `-server`, `-datadir`, `-conf`, `-bind`, `-whitebind`, `-whitelist`, `-rest` or `-zmq*`, also not in the `-no` form. This is a guard for later changes, see the note below on the rating.
- **Wallet lock (GPT M1):** after every unlock the lock is checked with `getwalletinfo` and repeated once. If it still cannot be confirmed and the passphrase is known, the unlock window is shortened to one second. A visible warning was not added: a lock that fails means the node did not answer, and the user cannot do anything about it except wait for the unlock time to run out.
- **Recovery unlock time (GPT M2):** the unlock window during recovery and rescan is 20 minutes (before 60 minutes). The scan limit of the app is 15 minutes, so a shorter window would cut a running scan.

### Not possible or not useful in this design, with reasons

- **Separate process for the node (Grok H1, GPT overall note):** the node, Tor and i2pd already run as own operating system processes with their own memory, started by the app. What the review misses is the user ID: on Android every process of an app runs under the same user ID, also with `android:process`, and files and the RPC cookie are readable for that ID. A real boundary would need a second app with its own user ID and an inter-app interface, which brings a bigger attack surface (exported components, an extra permission model). Android's isolated processes are, as far as we know, not meant to start an own executable. So this is not feasible as a small change, and a separate `android:process` would only look like isolation. The limit is stated in the whitepaper and stays the same as in other wallets with an own node.
- **Hardware-backed master key (Grok H1, L3):** the Android Keystore holds AES, RSA and EC P-256 keys, not secp256k1 and not BIP-32 derivation, so a Bitcoin-style seed cannot live there. What exists already is the optional app lock with fingerprint, face or device lock. A hardware-protected seed would need a hardware wallet or a signing app, which the PSBT tools already allow for larger holdings.
- **Seed only inside the node process (Grok M1):** Core has no BIP-39. The seed is turned into descriptors in the app, and only then the node imports them. Moving this into the node would need a change of the node itself (an own import command) and a consensus-neutral but security-relevant new node API. The exposure is limited to the minutes of creating or restoring a wallet and the arrays are cleared afterwards. It stays a candidate for a node change.
- **In-app signature check of an update (Grok M2):** the app never downloads or installs an APK. The update check only shows a version and opens the release page. Android installs an update only if its signature matches the installed app, so an APK signed with another key cannot replace the wallet. A second, pinned-key check inside the app would check the same thing again with weaker means, because code of the app itself could be replaced together with such a key. Users who install by hand should compare `SHA256SUMS.txt` and the signing fingerprint shown in Settings.

### Overstated or based on a misunderstanding

- **"Late extra arguments may override the RPC protection" rated High (GPT H1):** the review says itself that this is conditional. In the product no extra arguments are passed at all (only tests use them), and no input from outside reaches them, so there was no attack path. The guard was added because it costs a few lines, but the rating is Low at most.
- **"Wallet lock errors silently ignored" rated Medium (GPT M1):** the unlock window in the create path is 60 seconds. A failing `walletlock` practically means that the node is not answering. Hardened anyway, see above, but the rating was too high.
- **"Screenshot protection can be disabled by the user" (Grok M3):** it was a temporary switch that ended with closing or locking the app, not a permanent setting. The gap was that it could be switched on too easily. That is what the new check addresses.
- **"Update check without verification" as a risk of the app (Grok M2):** see above, the app is not an installer.
- **"Large native attack surface" (Grok M4):** true for every wallet with an own node and not something that can be removed. The versions and commits are part of the build manifest, the helper programs are built from the newest stable tags, and the node is the same code the network runs.
- **"Early alpha" and "unaudited alpha" (Grok L4, GPT conclusion):** the status is now beta, with the advice to keep larger holdings in a cold wallet until the independent human review is done.
- **Payment links and explorer (Grok L1, L2, GPT L1):** the URI parser has tests for duplicates, length limits, encoding and a fuzz run of 20,000 inputs, and no payment is sent without the confirmation screens. Explorer links stay optional with a privacy note. No change needed.
- **GPT review limits:** it covers three files, without checkout or build, and calls its own verdict partial. Its remark that Grok's claims were not confirmed refers to its own limited scope, not to a contradiction.

### Follow-up review of m0.1.7 by GPT

After the changes above, GPT did a second, short static review of the m0.1.7 source snapshot (October 25, 2026). It confirmed that the listed mitigations are present in the code and named four points. It is not a build or device test and not a certification. What was done with them:

- **Lock state must be confirmed (M1):** correct. A missing or malformed `unlocked_until` answer is now an unconfirmed lock and triggers the retry and the one-second fallback. Only a wallet without a passphrase, which has no `unlocked_until` at all, counts as having nothing to lock. Tests cover a missing answer, wrong types, an RPC error and a wallet without passphrase.
- **Callers ignored the result (M2):** partly taken. All operations that know the passphrase (send, fee bump, CPFP, PSBT and message signing, wallet removal, wallet creation and recovery) now hand it to the lock, so the one-second fallback works there. A user-visible warning was not added: a lock that cannot be confirmed means the node does not answer, and the unlock windows are at most 60 seconds (recovery 20 minutes) and 10 seconds for signing.
- **Tests for the argument filter (L1):** added, covering upper case, double dash, leading space, bare flags and the `-no` form. Whitespace around the name is now ignored by the filter. All node starts use the same `NodeConfig.arguments()` builder.
- **Passphrase rating is a heuristic (L2):** agreed. The Weak, Fair and Strong labels are rules (length, character classes, block list), not measured entropy. They were never meant to be more, which is why the text only says "Weak. Longer is better.".

## Logs

- The cleaned log shows I2P addresses (`.b32.i2p`) as `<i2p>` instead of `<onion>`.

## Node tab

- The Node tab has a new icon (a network of cubes) instead of the circular arrow, which looked like a reload button.
- The Node screen has a "Restart" button under Pause and Stop, shown while the node runs or starts. It restarts only the node. Tor and I2P keep running, so the I2P router does not have to build its tunnels again.
- A second button "Restart node, Tor and I2P" appears when Tor or I2P is switched on. It stops the node and both helper programs together and starts them again, for the case that a helper hangs, for example when I2P does not connect. The first button only restarts the node.
