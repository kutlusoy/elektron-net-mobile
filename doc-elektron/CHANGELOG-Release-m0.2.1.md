# Elektron Net: `elektron-net-mobile` Release m0.2.1

- **Version:** m0.2.1
- **Date:** November 1, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.2.1` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.2.0.md`](./CHANGELOG-Release-m0.2.0.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.2.1`. It continues from `m0.2.0`, the wallet version is 0.2.1.

## Extra seed word for the recovery phrase

- When a wallet is created or restored, an optional switch "Use an extra seed word, advanced" on the screen with the recovery words (below the words when creating, below the word field when restoring, like in Electrum) adds a BIP 39 passphrase to the recovery phrase. The seed, and with it every address, is derived from the words and the extra seed word together. Someone who finds only the words cannot open the wallet. It can be any text, and upper and lower case count. It is not a word of the word list and has no fixed position, so it is not called the 13th or 25th word.
- It is not the wallet passphrase. The wallet passphrase unlocks the wallet on the phone for sending, the extra seed word is part of the keys themselves.
- When creating, the extra seed word is entered twice. When restoring, a wrong or missing extra seed word opens a different, empty wallet without an error, which the screen says. The extra seed word is never stored, so it has to be kept apart from the words. Without it the coins cannot be restored.
- Wallets without an extra seed word are unchanged. An empty extra seed word gives the same seed as before, so existing wallets, backups, watch-only wallets and escrows need no change. An extra seed word cannot be added to an existing wallet, because it makes a different wallet.
- Escrow keys are derived from the master key of the wallet, so they follow the extra seed word.

## Word help when a wallet is restored

- While the recovery phrase is typed, the words of the BIP 39 list that match the letters typed so far stand above the field, as in Electrum. A tap completes the word and adds a space.
- A finished word that is not in the list is named under the field, a counter shows how many words are entered, and "Continue" stays off until the phrase, with its checksum, is valid.

## Mining rewards count to 100 confirmations

- A mining reward is shown with "n of 100 confirmations" while the node still counts it as immature, as the consensus rule (100 blocks) demands, instead of "n of 6". It does not show as confirmed after 6 blocks while it cannot be spent yet. Other payments keep the count of 6.

