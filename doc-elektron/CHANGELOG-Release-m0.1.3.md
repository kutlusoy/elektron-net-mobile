# Elektron Net: `elektron-net-mobile` Release m0.1.3

- **Version:** m0.1.3
- **Date:** October 23, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.3` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.2.md`](./CHANGELOG-Release-m0.1.2.md), [`CHANGELOG-Release-m0.1.4.md`](./CHANGELOG-Release-m0.1.4.md), [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

## Coins on reused addresses

- The balance includes the coins on reused addresses, with a small line that says how much of it sits there, so a restored wallet shows its full amount.
- Setting "Spend coins on reused addresses automatically" (Settings, Security), on by default. On: these coins count as available in Send and are added when the other coins do not cover a payment or when everything is sent. Off: they are not counted and never mixed with other coins. A payment that fits into one reused address is still paid from it first.
- Send names the amount on reused addresses and says how to spend it when the setting is off.
