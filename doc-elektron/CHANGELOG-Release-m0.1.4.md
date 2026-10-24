# Elektron Net: `elektron-net-mobile` Release m0.1.4

- **Version:** m0.1.4
- **Date:** October 24, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.4` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.3.md`](./CHANGELOG-Release-m0.1.3.md), [`CHANGELOG-Release-m0.1.5.md`](./CHANGELOG-Release-m0.1.5.md), [`guideline-escrow-psbt-and-tools.md`](./guideline-escrow-psbt-and-tools.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

## Escrow and PSBT

- Escrows belong to a wallet. Every wallet shows and uses only its own escrows, and the PSBT tools look at the active wallet and its own escrows only.
- The number of approvals is chosen freely from 1 to the number of keys, with up to 15 keys. A choice of 1 shows a warning.
- The flow is explained while an escrow is created: a numbered overview, three steps on the detail screen and a note that everyone must use the same keys and the same number.

## Node tab

- The status notification shows the peers by network, for example `IPv4: 9 · Tor: 1 · I2P: 1`.
- A "Storage use" card lists blocks, headers and block index, UTXO set, snapshots, every wallet, Tor data, I2P data, logs, other node data and the app files, with the total.
- Live logs of the node, Tor and I2P are bounded, and each has a "Clear" button.
- The maintenance card with "Reset chain data" moved to the end of the tab.

## Settings

- Log cleaning for the logs of the node, Tor and I2P: keep logs for 1 to 30 days, a maximum size per log file of 1 to 50 MB, an hourly cleaning and a "Clean logs now" button that shows the freed space.
- Tor relay: "Keep the phone awake for the relay" (off by default) holds a partial wake lock while the relay runs.
- I2P: "Use IPv6 for I2P" (on by default), to switch IPv6 off on networks without it.
