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

Two AI-assisted reviews of release m0.1.5 are kept in `doc-elektron/` as [`security-review-2026-10-25-grok.md`](./security-review-2026-10-25-grok.md) and [`security-review-2026-10-25-gpt.md`](./security-review-2026-10-25-gpt.md). They are not a replacement for the open independent human review. What this release changed because of them:

- **Passphrase policy (Grok H2):** a new passphrase needs at least 10 characters (before 8) and must be rated fair or strong. Very common passwords, also with digits or symbols appended, one repeated character and plain runs are refused. Existing passphrases are not touched and keep working, the rule only applies when a passphrase is set or changed (new wallet, restore, restore from a wallet file, change in the console).
- **Screenshot protection (Grok M3):** the temporary "Allow screenshots" switch no longer works on the screens with recovery words and passphrases, the lock screen and the console. Its hint says so.
- **Node arguments (GPT H1):** extra start arguments can no longer set or reset `-rpc*`, `-server`, `-datadir`, `-conf`, `-bind`, `-whitebind`, `-whitelist`, `-rest` or `-zmq*`, also not in the `-no` form. The app uses no extra arguments in the product, this is a guard for later changes.
- **Wallet lock (GPT M1):** after every unlock the lock is checked with `getwalletinfo` and repeated once. If it still cannot be confirmed and the passphrase is known, the unlock window is shortened to one second.
- **Recovery unlock time (GPT M2):** the unlock window during recovery and rescan is 20 minutes (before 60 minutes). The scan limit of the app is 15 minutes.

Points answered without a code change:

- **Shared app process (Grok H1, L3):** node, Tor and I2P run as child processes under the app user, like other wallets with an own node. The boundary is stated in the whitepaper. A separate node process and hardware-backed keys are candidates for a later version.
- **Seed window (Grok M1):** the seed exists in the app process only while a wallet is created or restored, arrays are cleared afterwards. Keeping it only inside the node is a larger change and stays on the list.
- **Update check (Grok M2):** the app never installs anything. Android accepts an update only when the signature matches the installed app, so a build signed with another key cannot replace it. Users who install by hand should compare `SHA256SUMS.txt` and the signing fingerprint shown in Settings, About.
- **Native components (Grok M4):** versions and commits of node, Tor and i2pd are part of the build manifest and shown in About, and the helpers are built from the newest stable tags.
- **Payment links and explorer (Grok L1, L2, GPT L1):** the URI parser is covered by tests for duplicates, length limits, encoding and a fuzz run, and a payment always needs the confirmation screens. Explorer links stay optional with a privacy note.
- **Status (Grok L4):** the project status is beta, with the advice to keep larger holdings in a cold wallet until the independent human review is done.
