# Elektron Net: `elektron-net-mobile` Release m0.1.9

- **Version:** m0.1.9
- **Date:** October 26, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.9` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.8.md`](./CHANGELOG-Release-m0.1.8.md), [`elektron-net-registry`](https://github.com/kutlusoy/elektron-net-registry)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

**Status:** in preparation, branch `m0.1.9`. It continues from `m0.1.8`, the wallet version is 0.1.9. Once a release exists, the status line names it and the list is final.

## Peer registry as stepping stone

- The files `torpeers.txt` and `i2ppeers.txt` of the public `elektron-net-registry` repository list Tor (onion v3) and I2P (b32) nodes, one address per line. The wallet uses them as an additional source for the first contact on these networks, next to the peers the node already knows and the peers added by hand. Every wallet that starts up contacts the same entries, which makes the distribution easier.
- The lists are used only for a network that is switched on, and only while the node has fewer than two peers on that network. The node then tries up to three entries once, taken in turn from the list (`addnode ... onetry`), at most every ten minutes. Nothing is added to the saved peers, and the entries do not replace the gossip of the network: once other peers are known, the registry is no longer needed.
- The lists are fetched over HTTPS and kept on the phone, so they survive being offline. They are refreshed once a day, and after a failure the next try comes after a quarter of an hour. When Tor is switched on, the lists are fetched through the own Tor, so the registry does not see the address of the phone. Without Tor, the I2P list is fetched directly.
- Every line is checked as an address of the wanted network, anything else (comments, other formats, wrong ports) is skipped, at most 200 entries and 64 KB are read, and redirects are not followed. The registry only supplies addresses to try, so a wrong entry cannot do more than fail to connect.
- Not active on the test network.
- The registry check runs in the background: the node service starts it on its own thread, it never waits for the screen and does not block the node.

## Tor and I2P on by default

- A new installation starts with Tor and I2P switched on, so the node finds peers on these networks from the start and the registry above can help. Each switch can be turned off in the settings at any time.
- An installation that existed before this release keeps its choice: switches that were never set stay off, so an update changes nothing for existing users.
