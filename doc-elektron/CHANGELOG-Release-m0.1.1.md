# Elektron Net: `elektron-net-mobile` Release m0.1.1

- **Version:** m0.1.1
- **Date:** October 22, 2026
- **Audience:** Testers, maintainers and users who want to know what a release added
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) `app/`, release names look like `v<node version>-m0.1.1` (see `guideline-distribution-and-signing.md` section 8.1)
- **See also:** [`CHANGELOG-Release-m0.1.0.md`](./CHANGELOG-Release-m0.1.0.md), [`CHANGELOG-Release-m0.1.2.md`](./CHANGELOG-Release-m0.1.2.md), [`guideline-tor-and-i2p.md`](./guideline-tor-and-i2p.md), [`guideline-release-signing.md`](./guideline-release-signing.md)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

This file lists the features this release added. Corrections are not listed.

---

## Tor and I2P

- Tor and I2P run as helpers in the app next to the node, each with its own switch. Both work in parallel with IPv4 and IPv6.
- Tor: routing next to the normal connections or all traffic through Tor, and a role of client, relay or exit relay with bandwidth limit, monthly traffic limit, relay name, contact and relay port.
- I2P: incoming connections, relaying for others with a bandwidth limit and a share of the bandwidth.
- Connection chips next to the status dot on the Wallet and the Node tab: IPv4, IPv6, Tor (purple) and I2P (green) with the number of connected peers.
- Tor and I2P tiles on the Node tab with status, own address, peers, ports and the live helper log. The Tor tile shows the bootstrap progress and whether the relay port is reachable.
- Stable addresses: the onion and I2P address stay the same until the user asks for a new one with "New address".
- Ports are picked automatically and moved when another program already uses them.
- Copy and share buttons for the own onion and I2P address.

## Wallets

- Restore from a wallet file (`wallet.dat` backup), with a prompt for a new passphrase when the file has none.
- "Reload wallets" on the Wallets screen: lists the wallets found on the node, drops entries without a wallet and loads unloaded wallets.
- A short confirmation after a restore, and a refresh of the coins after a restore and after a restart of the node.

## Fiat value (optional, off by default)

- Price services: Binance, Coinbase, Kraken, KuCoin, MEXC, Bitget, Gate.io, XeggeX, TradeOgre and CoinGecko, and own services with an https address and a path to the price.
- Currency pairs from a list or any own code, as `ELEK_EUR` or `EUR_ELEK`, and a changeable symbol of ELEK on the exchanges.
- Main display in ELEK or in the fiat currency, with the other value shown small. Fiat values on the balance, in the history, in transaction details and in Send.
- Fiat entry for the amount in Send and Receive, with a switch between ELEK and fiat.
- A list of fallback services that are asked only when the chosen service fails, and a limit on requests with a pause after a rate limit answer.
- A CoinGecko address for Bitcoin can stand in for the ELEK price while ELEK has no listing.

## Release process

- Test builds are signed with one fixed public test key, so they update each other and keep their data.
- A manual `prerelease` workflow for test builds and a strict `release` workflow that builds only with the release key.
- Documentation: the mobile whitepaper, the Tor and I2P guideline and the release signing guideline.
