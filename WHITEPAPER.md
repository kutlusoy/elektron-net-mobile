# Elektron Net - Mobile Whitepaper: The Mobile Liberation of the Blockchain

- **Version:** 0.2 (draft)
- **Date:** October 30, 2026
- **Audience:** Users, node operators, developers, reviewers and anyone who wants to understand what the Elektron mobile app is and why it exists
- **Author:** Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy))
- **Reference implementation:** [`elektron-net-mobile`](https://github.com/kutlusoy/elektron-net-mobile) (this repository) and [`elektron-net`](https://github.com/kutlusoy/elektron-net) (the node)
- **See also:** [`README.md`](./README.md), [`doc-elektron/guideline-mobile-fullnode-wallet-plan.md`](./doc-elektron/guideline-mobile-fullnode-wallet-plan.md), [`doc-elektron/guideline-tor-and-i2p.md`](./doc-elektron/guideline-tor-and-i2p.md), [`WHITEPAPER.md` of the node](https://github.com/kutlusoy/elektron-net/blob/main/WHITEPAPER.md)

---

## 1. Abstract

Most people who hold digital money do not run the network that guards it. They install a wallet that asks a server what their balance is, and they trust the answer. The wallet may be non-custodial, the keys may be theirs, but the truth about the chain still comes from someone else.

Elektron Mobile ends that dependency on the device people already carry. It is an Android wallet that ships its own complete Elektron Net full node. The phone downloads the chain state, checks every block and every payment itself, signs with keys that never leave the device, and talks to the network over IPv4, IPv6, Tor and I2P. There is no Electrum server, no indexer and no account in the path.

This paper describes the idea, the architecture, the privacy model and the full feature set of the app. It also states what is not finished, because a wallet that overstates its maturity is worse than no wallet.

## 2. The problem

1. **Light wallets trust servers.** A wallet that asks a third party for balances and history shows what that party says. The server learns the user's addresses and IP address, can withhold or reorder information, and can disappear.
2. **Running a node is for people with computers.** The usual advice to "run your own node" assumes a desktop, a fast disk and an always-on connection. Billions of people have a phone and nothing else.
3. **Networks can be blocked or watched.** A node that speaks only clearnet is easy to find, to filter and to link to a person.
4. **Seed recovery fights pruning.** Small devices must prune old blocks, and a restored wallet then cannot replay history it no longer has.

## 3. The idea: the mobile liberation of the blockchain

A blockchain is free when ordinary people can verify it without permission. Elektron Mobile puts that ability into a pocket:

- **Verify, do not trust.** The phone is a full node. Balances and payments come from its own validation.
- **Own your keys.** Keys live in the node's encrypted wallet on the device. The app never holds the recovery phrase after setup.
- **Be reachable and be private.** IPv4 and IPv6 work out of the box. Tor and I2P are on for a new installation and can be switched with one switch each, so the node can connect and be found without exposing the user's address. With "Accept incoming connections" the phone helps carry the network on every network, also on mobile data.
- **Stay small.** Elektron Net prunes every node to a fixed window of recent blocks by design, so a phone can keep up.
- **Stay open.** The code is MIT licensed, every build is traceable to a source commit, and nothing leaves the phone except the traffic the node itself needs.

## 4. Architecture

```text
Android app (Kotlin, Jetpack Compose)
        |  JSON-RPC, 127.0.0.1 only, cookie authentication
        v
elektrond (native, bundled as libelektrond.so)
        +-- P2P Elektron network over IPv4, IPv6, Tor and I2P
        +-- chainstate, pruned block data, automatic snapshots
        +-- descriptor wallet, encrypted keys, signing
libtor.so, libi2pd.so (optional helpers, started by the app next to the node)
        +-- Tor: SOCKS and control on 127.0.0.1
        +-- I2P router (i2pd): SAM bridge on 127.0.0.1
```

- **One sandbox.** The node, Tor and the I2P router run as child processes of the app, inside the same Android sandbox, started and stopped together by a foreground service. Nothing else on the phone can reach them: every port is bound to loopback.
- **The app is a supervisor and a screen.** It starts the processes, applies the battery, data and storage policy, and presents the wallet. Consensus, networking and keys stay in the node.
- **Current source on every build.** The build fetches the node from `kutlusoy/elektron-net`, Tor from the official Tor Project repository and the I2P router from its project repository, always at the newest stable release tag for release builds, and runs a contract check against the node before anything ships.

## 5. Privacy and reachability

| Network | How it is used | What it gives |
|---|---|---|
| IPv4, IPv6 | Always on | Normal peer connections. IPv6 is the best way to be reached from outside on a phone: most carriers give a global IPv6 address without NAT, while IPv4 sits behind carrier NAT |
| Tor | Switch, on for a new installation. Next to the normal connections, or for everything | Onion peers, an own onion address for incoming connections, hidden IP address in the all-traffic mode |
| I2P | Switch, on for a new installation | I2P peers and an own I2P address |

- **Stable addresses.** The node keeps the same Tor and I2P address across restarts until the user creates a new one, so it can be found at a known place and can still change address when it must.
- **Relay roles.** The user can run Tor as client, relay or exit relay, with bandwidth and monthly traffic limits, and I2P with traffic sharing. Defaults leave all of this off. An exit relay asks for an explicit confirmation because other people's traffic leaves through the user's IP address.
- **Incoming connections.** One switch, "Accept incoming connections", decides whether the node accepts them. When it is on, it does so on every network (Wi-Fi, mobile data, roaming) with no further condition: over IPv4 and IPv6 where the network lets them in, and through the Tor onion service and the I2P router, which need no public address. Whether the node runs at all on mobile data or roaming is decided by the separate network and data settings, which a new installation sets to mobile data allowed within 5 GB a month and roaming off.
- **Stepping stones.** The peer lists of the public `elektron-net-registry` repository (`torpeers.txt` and `i2ppeers.txt`) give the first contact on Tor and I2P, only while the node has fewer than two peers there. They are fetched over HTTPS (through Tor when it is on), kept on the phone, checked line by line and never added to the saved peers, so the network's own gossip takes over.
- **Indicators.** Small chips beside the sync status show which networks the node uses and how many peers each carries. The Node tab has a tile per switched-on network with status, address, peers and ports.
- **No hidden calls.** Web requests outside the explorer list are limited to the update check (by hand, or an optional daily check that is off by default), the price services the user chooses for the optional fiat value, and the registry peer lists. The app never downloads or installs an APK. There is no analytics, no remote geolocation and no remote QR service. Peer countries come from an offline database and addresses never leave the phone.

## 6. Pruning and the pocket philosophy

Elektron Net keeps the last 197,280 blocks (about 137 days at one block a minute) on every node. History older than that is not stored on the network by design. A restored wallet therefore recovers its full balance from the current set of unspent outputs, the "pocket", and not from replaying history. Transaction history older than the window cannot be restored, and the app says so in plain words. Coins that were spent elsewhere while a wallet was offline are recognised and dropped from the balance after a scan, and the app re-reads the coins after a restore. Coins recovered from the set keep their real outpoint (node 4.0.6), so they can be spent like any other coin, coinbase rewards stay immature until maturity, and a wallet that an earlier node version had recovered loads again.

## 7. Security model

- **Keys:** native SegWit (`be1q...`) addresses, BIP-39 recovery phrase, SLIP-44 coin type 1370 with legacy path recovery. Entropy comes from health-checked system generators with no weak fallback. An optional screen-drawing step and a dice mode add user randomness.
- **Custody:** keys sit in the node's encrypted descriptor wallet. Unlocking is short-lived and always followed by a lock that is checked and repeated. Sending needs the passphrase every time. A new passphrase needs at least 10 characters and a fair or strong rating.
- **Recovery phrase:** it is shown once at creation and stored nowhere, not in the app and not in the node, so it cannot be shown again later. Keep it offline.
- **Removing a wallet:** after a warning and a confirmation, with no passphrase. The node unloads the wallet, its files are overwritten and deleted, and the removal counts as done only when the node no longer lists the wallet.
- **Local access only:** node RPC is bound to loopback and authenticated by a cookie file in the app-private folder that the node recreates at every start. No password is stored anywhere.
- **Screen and storage:** sensitive screens block screenshots and recent-app previews, Android backup of app data is disabled, and a temporary screenshot switch for tutorials needs a warning and a device check and switches itself off after 15 minutes.
- **Addresses:** fresh receive and change addresses by default. Coins that arrive on an already spent address are kept out of automatic spending.
- **Honest status:** this is beta software. It has been tested in practice and the wallet runs stably, so it can be used for everyday amounts. Larger holdings belong in a cold wallet. An independent security review is still open. Always keep the recovery phrase offline.

## 8. Feature list

**Node**
- **Full validating node:** header and block validation, per-block UTXO attestation checks, mempool, transaction relay and mandatory pruning, bundled in the app.
- **Automatic snapshots:** checkpoint snapshots and peer-to-peer snapshot bootstrap, as implemented by the Elektron Net node.
- **Foreground service:** runs in the background with resource profiles, and the user can pause or stop it at any time after a confirmation.
- **Policy engine:** respects mobile data, roaming, charging, night schedule, battery, temperature, storage and a monthly data cap. A new installation allows mobile data within a limit of 5 GB a month and keeps roaming off.
- **Resource profiles:** while the app is open (up to 50 connections), Balanced and Always on (up to 125 connections each).
- **Helping the network:** accepts incoming connections on every network when the switch is on, with Tor and I2P switched on for a new installation, and peer lists of the registry as stepping stones.
- **Storage saver:** fast pruning on by default with an opt-out, and a slider for the free-space level at which the node stops.
- **Snapshot sharing:** serves snapshots to other nodes only on Wi-Fi while charging.
- **Node dashboard:** sync phase, height, connected peers in one collapsible list with country, disk use, prune height, difficulty, hash rate, mempool, upcoming halving with date and reward, difficulty epoch and projected difficulty.
- **Live log:** optional node log with a cleaned copy for sharing, with IP addresses, onion names, addresses and paths removed.
- **Guided chain reset:** reloads the chain data, including the snapshot chainstate and the automatic restart marker, and keeps the wallets and the Tor and I2P addresses. A node that reports no usable chainstate gets its own explanation that points to the reset.
- **Restart:** restart only the node, or the node with Tor and I2P together.

**Wallet**
- **Create and restore:** BIP-39 phrase with true entropy, backup quiz, restore from phrase, and restore from a `wallet.dat` backup file. A backup without a passphrase gets one right away.
- **Several wallets:** quick switch, rename, and removal after a confirmation (no passphrase, the files are overwritten and deleted). The wallet list follows what the node really holds.
- **Send:** review-before-sign flow, fee presets from the node with a mempool explorer fallback, fee re-check before signing and payment links. A switch "Advanced payment" adds choosing coins, subtracting the fee from the amount, a custom fee rate and "Only create a PSBT" (signed or unsigned, as text, animated QR code, copy or share).
- **Text in a block:** a text of up to 1,000 bytes can be written into the block with the payment as one `OP_RETURN` output. It is public, cleaned before signing and checked again after signing. Received texts show in the transaction details.
- **Coin control:** choose and freeze coins, see which coins sit on reused addresses.
- **Fee bump and CPFP:** speed up a waiting payment with RBF when you sent it, or with CPFP when it is incoming.
- **History:** complete per-wallet history, 10 per page, search across everything, filters and CSV export.
- **Address book:** local contacts shared by all wallets.
- **Receive:** QR code and copy, `elek:` payment links in lowercase with amount and message, notification for incoming payments that hides the amount on the lock screen.
- **QR scan:** camera scan with CameraX and ZXing, no Google services.
- **Units and format:** ELEK, mELEK, bit and lepton, with the ELEK value repeated when sending, and a number format with a thousands separator switch and the choice of both characters.
- **Fiat value:** optional (off by default), from many price services with fallbacks, as main display or small next to ELEK, with fiat entry in Send and Receive.
- **Exports:** public descriptors for watch-only use, an encrypted wallet file, and the history as CSV.
- **Recovery tools:** rescan, UTXO scan for coins older than the pruning window under their real outpoints, and recognition of coins spent while offline.
- **Watch-only wallets:** from an extended public key, a descriptor, one address or the QR code of a signing device. Balance, history and receiving work, a payment is made as an unsigned file that the signing device signs, for example a SeedSigner. Two phones with this app can work as wallet and signing device with the camera only.
- **Animated QR codes:** payment files as UR `crypto-psbt` codes, with a scanner that reads plain and animated codes.
- **Node-only mode:** start without a wallet and create or restore one later.

**Advanced tools**
- **Escrow wallets:** M-of-N multisig, for example 2 of 3 with an arbiter. Deposit from the own wallet in one step, a message history per escrow, and keys that a new escrow never reuses.
- **PSBT tools:** check, sign, combine and send payment files.
- **Key sweep:** import a private key and sweep its coins, like in Electrum.
- **Message signing:** sign and verify messages with an address.
- **Expert console:** opt-in console to the node with four risk tiers, confirmation dialogs, encrypted history and file commands confined to a private folder.

**Network**
- **IPv4 and IPv6:** automatic. Incoming connections follow the one switch, on every network.
- **Tor:** one switch. Routing next to the normal connections or for everything, own onion address, relay or exit relay with limits and exit policy.
- **I2P:** one switch. I2P peers, own I2P address, incoming connections and traffic sharing with limits.
- **Stable addresses:** the same Tor and I2P address until the user creates a new one.
- **Automatic ports:** helper ports are chosen at start and move to the next free port if another program uses them.
- **Connection indicator:** chips for IPv4, IPv6, Tor (purple) and I2P (green) beside the sync status, and a tile per switched-on network on the Node tab.

**App and design**
- **Languages:** fifteen languages as drafts (English, German, Spanish, French, Italian, Chinese, Arabic, Japanese, Korean, Hindi, Portuguese (Brazil), Russian, Turkish, Indonesian, Vietnamese). The app follows the system language and can be switched in Settings. Native-speaker review of the security texts is open.
- **Themes:** five color themes, each with a dark and a light mode, one readable system font, a contrast check in CI.
- **App lock:** optional biometric unlock with an adjustable delay.
- **Update check:** on request, or once a day when switched on (off by default), asks GitHub for the newest tag and shows a banner with a button to the release page.
- **Mainnet and testnet:** separate builds (`org.elektronnet.mobile` and `org.elektronnet.mobiletestnet`) that install side by side.
- **MIT license:** free to use, study and build.

## 9. Build, trust and distribution

- **Reproducible provenance.** Every build writes a manifest with the source commit and the SHA-256 of each native binary, shown in Settings, About.
- **Contract check.** The build refuses to continue unless the node still offers exactly the RPC calls, startup options and behavior the app depends on.
- **Versions.** A release name has two parts: the node version and the wallet version, for example `v4.0.6-m0.2.0`, so the wallet can be updated without a new node.
- **Signing.** Release builds are signed with the project release key. Test builds use one fixed public test key, so test builds update each other and are clearly not for distribution.
- **Channels.** The primary source is the GitHub releases page. F-Droid and Google Play are planned after review.

## 10. Limits and risks

- **Beta.** The core flows work and have been tested in practice, but the device test matrix, native-speaker review, release keys and an independent security review are open.
- **Android only.** There is no iOS app. 32-bit ARM is not supported.
- **Reachability.** Mobile carriers usually put IPv4 behind carrier NAT, so no one can connect to the phone that way. IPv6 (where the carrier lets it in), the Tor onion service and the I2P router reach the node without a public address. A Tor or I2P relay still needs a connection that can be reached from outside, in practice Wi-Fi with a forwarded port.
- **Battery and data.** A full node and its helpers cost battery and data, more with up to 125 connections and with the onion service and the I2P router running on mobile data. The policy engine limits both, and a new installation starts with a monthly mobile data limit of 5 GB.
- **Limited history.** Payments older than the pruning window are not stored on the network.
- **Third-party software.** Tor and the I2P router are built from their upstream sources at the newest stable tag. A bug in them is a bug in the app until the next build.

## 11. Roadmap

1. Device test matrix and fixes from real use.
2. Native-speaker review of the translations.
3. Independent security review, then the first release.
4. Server access for the local network (the node-only mode already exists), so a phone can serve as a pool or miner backend.
5. A pluggable path for Lightning in a later version.

## 12. Links

- Website: https://elektron-net.org
- X: @elektronnet, @kutlusoy
- Telegram: @elektronnet
- Node: https://github.com/kutlusoy/elektron-net
- App: https://github.com/kutlusoy/elektron-net-mobile
