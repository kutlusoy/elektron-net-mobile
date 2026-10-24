# Elektron Net: `elektron-net-mobile` Release m0.1.0

- **Version:** m0.1.0
- **Date:** October 19, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.0` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.1.md`](./CHANGELOG-Release-m0.1.1.md), [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

## Wallet and keys

- Create a wallet or restore one from a 12-word BIP-39 recovery phrase. The keys are derived on the phone and the wallet is encrypted with a passphrase.
- Entropy hardening: health-checked system sources, a dice mode and an optional touch drawing as extra input.
- The wallet is created or restored at once. The setup finishes in the background when the node is synced, followed by a rescan.
- Several wallets with switching, names and labels. Unused and empty wallets can be removed.
- Address reuse protection (`avoid_reuse`) on every wallet. Coins on reused addresses are shown and spent first.
- Recovery of coins after pruning: a restored wallet finds all its coins, including coins that were spent while the wallet was offline (needs node v4.0.6).
- Optional biometric app lock with a lock delay.

## Receiving and sending

- Receive screen with the address, a payment link, a QR code, copy and sharing, and checks for Bech32 and Base58 addresses.
- Send flow: transaction built and checked as a PSBT, fee presets and a custom fee, review before signing, signing with a short unlock of the wallet, an optional switch to subtract the fee from the amount, payment links.
- QR scanner for addresses and payment links.
- Balance, transaction details and a History tab with the full history per wallet, search, filters and pages.
- Notice for incoming payments, with the amount hidden on the lock screen.

## Coins and transactions

- Coin control: choose coins and freeze coins.
- Fee bump (replace by fee) and child pays for parent (CPFP).
- Labels, an address book, CSV export of the history, export of descriptors and of the wallet file.
- Rescan of the wallet.

## Tools

- Escrow (multisig) wallets with a manager, PSBT tools to check and sign payment requests, key sweep and message signing and verification.
- Expert console with permission tiers, a danger switch, an encrypted history and file management.

## Node

- A pruned Elektron Net full node that runs as a service of the app, with profiles and storage settings (storage saver, stop below a free space limit), and a node only mode.
- Start and sync progress with an estimate, an optional live node log (copy and share with secrets removed), warm-up messages and warnings when the node seems stuck.
- Connected peers with their country (offline GeoIP database) and a detail page for every peer.
- Chain events such as the next halving and difficulty retarget, and a reset of the downloaded chain data.
- A list of block explorers with fee suggestions from the first explorer that answers.
- Update check against the GitHub releases.

## App

- Five themes with light, dark or system mode and an effect level, plus splash themes.
- 14 languages with a language switch in the app.
- Display unit for amounts: ELEK, mELEK, bit or lepton, chosen with a slider that previews the value.
- Temporary screenshots, a show-password option, an About page with the wallet version, the node version and the licence (MIT) and credits.
- Two-part release versions: node version and wallet version.
