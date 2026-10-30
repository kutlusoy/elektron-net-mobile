# Elektron Net: `elektron-net-mobile` Release m0.2.0

- **Version:** m0.2.0
- **Date:** October 30, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.2.0` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.9.md`](./CHANGELOG-Release-m0.1.9.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.2.0`. It continues from `m0.1.9`, the wallet version is 0.2.0.

## Incoming connections without Wi-Fi

- "Accept incoming connections" in the settings is no longer limited to Wi-Fi. When the switch is on, the node accepts incoming connections on every network (Wi-Fi, mobile data, roaming), with no further conditions. The switch is the only condition.
- Whether the node runs at all on a metered or roaming network is still decided by the other network settings (metered networks, roaming, data saver, monthly cap). Only the listening itself no longer depends on the network type.
