# Elektron Net - `elektron-net-mobile` Tor and I2P Guideline

- **Version:** 0.1 (draft)
- **Date:** October 21, 2026
- **Audience:** Android and node developers, release engineers, testers who run the app as a relay
- **Authors:** Elektron Net Developers, Ali Kutlusoy ([@kutlusoy](https://x.com/kutlusoy)), Milio aka 1000mio ([@spiroflex](https://x.com/spiroflex)) 🇪🇸
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/torcontrol.cpp`, `src/i2p.cpp`, `src/init.cpp` (arguments `-onion`, `-proxy`, `-torcontrol`, `-listenonion`, `-i2psam`, `-i2pacceptincoming`) and this repository: `app/src/main/java/org/elektronnet/mobile/node/` (`NetworkSettings.kt`, `NetworkArgs.kt`, `TorConfig.kt`, `I2pConfig.kt`, `ChildService.kt`, `NetworkServices.kt`), `native-build/fetch-helpers.sh`, `native-build/helpers/`
- **Fork base:** [`tpo/core/tor`](https://gitlab.torproject.org/tpo/core/tor), [`PurpleI2P/i2pd`](https://github.com/PurpleI2P/i2pd)
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) §4.1, §11.4, [`guideline-ux-and-screens.md`](./guideline-ux-and-screens.md) §6.5 and §6.6, [`guideline-distribution-and-signing.md`](./guideline-distribution-and-signing.md)

---

## 1. Goal

The node can use four networks in parallel: IPv4 and IPv6 are always on, Tor and I2P are switched on in Settings. With Tor and I2P a phone node can make and accept connections without depending on a public IP address or on the carrier network allowing inbound traffic.

## 2. Design

- **Same sandbox as the node.** Tor and the I2P router are native executables shipped next to `libelektrond.so` (`libtor.so`, `libi2pd.so` in `jniLibs`). `NetworkServices` starts them as child processes of the app, before the node, and stops them after the node. They live and die with the node service. Nothing runs when the node is stopped or paused.
- **Loopback only.** Tor listens on `127.0.0.1` for SOCKS and control, the I2P router on `127.0.0.1` for SAM. The node connects to them with its own arguments. The app never talks to these ports.
- **Ports per flavor, resolved at start.** Mainnet prefers Tor 9050 (SOCKS) and 9051 (control), I2P SAM 7656. Testnet prefers 19050, 19051 and 17656, so both apps can run side by side. When a preferred port is already in use on loopback (Orbot, a system Tor, another app), the app moves that port up in steps of 10 to the next free one (`NetworkPorts.resolved()`), once per app start, before the helpers and the node are configured. The node arguments always carry the resolved ports explicitly (`-torcontrol`, `-onion` or `-proxy`, `-i2psam`), and the Node tab shows the ports in use.
- **No password.** Tor uses cookie authentication, the node reads the cookie file that Tor reports (SAFECOOKIE), the file sits in the app-private data directory.
- **Settings drive everything.** Settings flow through `PolicySettings.network` into the policy decision. A change that alters the node arguments (Tor or I2P on or off, routing, accepting I2P connections) restarts the node once. Relay settings only rewrite `torrc` or `i2pd.conf` and restart that helper, not the node.
- **Policy.** The helpers run only while the node is allowed to use the network. Pause, stop, metered data and the other policy reasons stop them with the node.

## 3. Node arguments

| Setting | Arguments |
|---|---|
| Tor off | `-listenonion=0` |
| Tor on, next to normal connections | `-onion=127.0.0.1:<socks>`, `-torcontrol=127.0.0.1:<control>`, `-listenonion=1` while the node listens, otherwise `-listenonion=0` (the node refuses `-listen=0` together with `-listenonion=1`) |
| Tor on, everything through Tor | `-proxy=127.0.0.1:<socks>` instead of `-onion`. While listening, the node binds only loopback (`-bind=127.0.0.1:<p2p>` and `-bind=127.0.0.1:<p2p+1>=onion`), so no clearnet port is exposed |
| I2P on | `-i2psam=127.0.0.1:<sam>`, `-i2pacceptincoming=1` when the node listens and the user allows it, otherwise `0` |

## 4. Settings

| Group | Items |
|---|---|
| Tor | Use Tor. Routing: next to the normal connections (recommended) or everything through Tor. Role: client only, relay, exit relay |
| Tor relay | Bandwidth limit (75 KB/s to 8 MB/s), monthly traffic limit (none to 1,000 GB), relay name, contact, relay port (default 9001). For an exit: the exit policy, reduced (recommended) or web only (80 and 443) |
| I2P | Use I2P. Accept incoming I2P connections. Relay traffic for others, with a bandwidth limit and the share of bandwidth for others |

The Tor and I2P groups are visible in Settings at all times, their detailed options only while the switch is on. Switching an exit relay on asks for a confirmation that explains that the traffic of other people leaves through the user's IP address.

## 5. Generated configuration

`TorConfig.torrc()` writes `DataDirectory`, `SocksPort`, `ControlPort`, `CookieAuthentication 1`, `CookieAuthFile`, `AvoidDiskWrites 1` and `Log notice stdout`. A client adds `ClientOnly 1`, `ORPort 0`, `ExitRelay 0` and `ExitPolicy reject *:*`. A relay adds `ORPort`, `PublishServerDescriptor 1`, `RelayBandwidthRate`, `RelayBandwidthBurst` (twice the rate), `Nickname`, `ContactInfo`, `AccountingMax` with `AccountingStart month 1 00:00`, and `ExitRelay 0`. An exit adds `ExitRelay 1`, `IPv6Exit 0`, `ReducedExitPolicy 1` or `ExitPolicy accept *:80`, `accept *:443`, `reject *:*`, and `ServerDNSResolvConfFile` pointing to a file with the DNS servers of the active network, because Android has no `/etc/resolv.conf`. Values are clamped and stripped of line breaks.

`I2pConfig.conf()` writes `ipv4`, `ipv6`, `notransit`, `floodfill = false`, `bandwidth`, `share` and enables only the `[sam]` section on loopback. HTTP console, HTTP and SOCKS proxies, BOB, I2CP, I2PControl and UPnP stay off. The reseed certificates are copied from the app assets to the I2P data directory on first start.

## 5a. Node tab tiles

While Tor is switched on the Node tab shows a Tor tile, while I2P is on an I2P tile, below the Network card. A Tor tile lists status (running, starting, failed, not included), the onion address with its port (or "waiting for the address" until the node has created its onion service), peers over Tor, SOCKS port, control port, routing and role, and for relays the relay port and the bandwidth limit. An I2P tile lists status, the I2P address, peers over I2P, the SAM bridge, whether incoming connections are accepted, whether it relays for others, the bandwidth limit and the share. The addresses come from `getnetworkinfo` (`localaddresses`), the peer counts from `getpeerinfo`.

## 5b. Stable addresses

The node keeps the private key of its onion service (`onion_v3_private_key`) and of its I2P destination (`i2p_private_key`) in its data directory and reuses them at every start. The address therefore stays the same across restarts, app updates and chain resets (a reset keeps these files) until the user decides otherwise. Each card has a "New Tor address" and a "New I2P address" button. After a confirmation the app stops the node, deletes that one key file and lets the node start again, which creates a fresh key and address. The old address stops working at once, peers that only know it can no longer find the node.

The node persists the I2P key only while it accepts incoming I2P connections (`-i2pacceptincoming=1`, source: `net.cpp`). Without that, the I2P session is transient and the address changes with every session. The card says so.

## 6. Connection indicator

Next to the green dot and the word "Synced" on the Wallet tab and on the Node tab, small outlined chips show which networks the node uses right now, with the number of connected peers: IPv4, IPv6, Tor (purple), I2P (green) and CJDNS. IPv4, IPv6 and CJDNS appear when peers are connected. Tor and I2P also appear with 0 when they are switched on, so the user sees that they are waiting for peers. The numbers come from `getpeerinfo` on every tick. When the node is not running, no chip is shown.

## 7. Build

- `native-build/fetch-helpers.sh` resolves the **newest stable release tag** of Tor (`tor-X.Y.Z.N`, no alpha, rc or dev) and of i2pd (`X.Y.Z`) on every build and checks out that exact commit. Tor comes from the official repository `gitlab.torproject.org/tpo/core/tor` only (`TOR_REPO_URLS` can list more). `TOR_REF` and `I2PD_REF` pin a tag for debugging.
- `native-build/helpers/build-helpers-in-container.sh` cross-builds zlib, OpenSSL, libevent and (for i2pd) Boost with the NDK, all with pinned versions and SHA-256 checksums from `native-build/helpers-source.env`, then Tor and i2pd as static-library executables with 16 KB page alignment. `HELPERS=0` builds without them.
- Output: `libtor.so` and `libi2pd.so` per ABI in `jniLibs`, `assets/i2pd/certificates`, and `assets/core-manifest/helpers-<abi>.env` with repository, tag, commit and SHA-256 of both binaries. A build is skipped only when the resolved commits, the builder files and the helper files are unchanged.
- A build without the libraries still works: Settings then shows "not included in this build" next to the switch.

## 8. Decisions and differences

1. **I2P router.** The reference implementation [`i2p/i2p.i2p`](https://github.com/i2p/i2p.i2p) is a Java router. Embedding it needs a Java runtime inside the app process and several times the memory. This version ships [`i2pd`](https://github.com/PurpleI2P/i2pd), the C++ implementation of the same protocol and the same network, as a native child process like Tor and the node. The node side is identical (SAM on `127.0.0.1`), so the router can be replaced later without touching the node arguments.
2. **Tor repository.** Tor is fetched from the official GitLab repository only. The GitHub repository `torproject/tor` is a mirror that lags behind: its newest final tag was `tor-0.4.7.13` while the official repository was at 0.4.9. The first release build resolved `tor-0.4.9.14`. Check the resolved tag in the helper manifest of every release.
3. **Nothing is built on the phone.** All binaries come from the CI build.

## 9. Risks

- A **relay** needs inbound reachability and keeps the radio busy. Most carriers use NAT without inbound ports, so relays work reliably only on Wi-Fi with a forwarded port. The app says so, it does not try to open ports.
- An **exit relay** puts other people's traffic on the user's IP address. The reduced exit policy is the default, the confirmation dialog is shown every time the role is switched to exit.
- The first I2P start downloads peer data (reseed) and needs several minutes. The status line shows "starting" until the SAM bridge answers.
- Tor and I2P add battery and data use. The defaults leave both off.

## 10. Tests

| Test | What it proves |
|---|---|
| `NetworkConfigTest` | Node arguments for every combination, generated `torrc` and `i2pd.conf`, clamping, port resolution. With `TOR_BIN` and `I2PD_BIN` the real programs accept every generated configuration |
| `NetworkServicesTest` | Helpers follow the settings and the active flag, restart on a changed configuration, a failing or missing binary is reported and skipped |
| `TorNodeIntegrationTest` | A real Tor and a real `elektrond`: the node uses the SOCKS port for onion peers, authenticates at the control port with the cookie, creates its onion service and lists the `.onion` address |
| `I2pNodeIntegrationTest` | A real `elektrond` connects to the configured SAM port (a stand-in SAM server sees `HELLO`) and reports I2P as reachable |
| `NetworkTilesModelTest` | Rows of the Tor and I2P tiles and the parsing of the node's local addresses |
| `NodeControllerIntegrationTest` | Only settings that change the node arguments restart the node |
| `TorAddressIntegrationTest` | A real Tor and `elektrond`: the onion address is the same after repeated restarts, differs after the key is renewed, and is again stable |
| `AddressKeysTest` | Renewing deletes only the chosen key |
| `ConnectionModelTest` | Which chips appear |
| `native-build/tests/test-fetch-helpers.sh` | Release tag selection, also across a stale and a fresh repository |

Not covered by automated tests: the Android cross-build of both helpers, running them on a device, and an I2P session (the build sandbox has no I2P reseed access).

## 11. Checklist

- [x] Settings, node arguments, `torrc` and `i2pd.conf`, process supervision
- [x] Indicator chips on the Wallet and Node tab
- [x] Build scripts and release workflow
- [x] Host verification with real Tor and `elektrond`
- [ ] First Android build of `libtor.so` and `libi2pd.so` in CI, then a run on a device
- [ ] I2P session on a device (SAM comes up, `-i2psam` peers appear)
- [ ] Relay run over Wi-Fi with a forwarded port
- [ ] Decide whether to keep i2pd or embed the Java router

## 12. Open Questions

1. Is i2pd acceptable as the I2P router, or must the Java router from `i2p.i2p` ship?
2. Should the relay roles be hidden unless Expert mode is on?
