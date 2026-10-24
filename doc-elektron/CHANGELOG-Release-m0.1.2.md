# Elektron Net: `elektron-net-mobile` Release m0.1.2

- **Version:** m0.1.2
- **Date:** October 22, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.2` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.1.md`](./CHANGELOG-Release-m0.1.1.md), [`CHANGELOG-Release-m0.1.3.md`](./CHANGELOG-Release-m0.1.3.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

## Tor and I2P peers

- Add peers by hand in the Tor and I2P tiles: a field for the address, an "Add" button and a list with a "Remove" button for every peer. Only onion v3 and `b32.i2p` addresses are accepted.
- Scan the QR code of another phone to add its address.
- "Show QR" under the own address shows it as a QR code for the other phone.
- Added peers are saved and used at every start of the node. Adding or removing one does not restart the node.
