# Elektron Net: `elektron-net-mobile` Expert Mode and Node Console Guideline

- **Version:** 0.3 (draft)
- **Date:** October 18, 2026
- **Audience:** Android/Kotlin developers, security reviewers, translators of the app
- **Reference implementation:** [`elektron-net`](https://github.com/kutlusoy/elektron-net) `src/rpc/` and `src/wallet/rpc/` (RPC surface), `src/init.cpp` (RPC arguments), treat the node's `help` output as ground truth for command names and arguments
- **See also:** [`guideline-mobile-fullnode-wallet-plan.md`](./guideline-mobile-fullnode-wallet-plan.md) (§3.3 sections, §3.4 node transparency, §4.2 supervisor, §8.3 recovery, §9 security, §11.2 contract check), [`native-build/contract/console-tiers.json`](../native-build/contract/console-tiers.json) (command classification), [`native-build/contract/contract.json`](../native-build/contract/contract.json)
- **Website:** https://elektron-net.org | **X:** @elektronnet, @kutlusoy | **Telegram:** @elektronnet

Requirement levels follow common practice: **MUST** = mandatory for correct/safe operation, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. Goal and Scope

Expert mode gives advanced users a console to the node of the phone, comparable to the debug console of the desktop wallet: type an RPC command, see the answer. It lives in the Node section of the app, is off by default, and is fully opt-in.

The same console is also the tool the developers use to inspect the node on a device, so it is built early (read-only first) and hardened before it is exposed to users.

**Not in scope:** a general shell, access to files outside the app, and a bundled `elektron-cli`. The mobile build ships `elektrond` only. The console speaks JSON-RPC to the local node through the app's own RPC client.

### 1.1 Decisions (October 18, 2026)

| Topic | Decision |
|---|---|
| Danger tier | Ships in the first release. It is switched on by the user with an explicit confirmation that they know what they are doing (§3) |
| Device authentication | Once per Expert session (§3) |
| Command history | Persistent, with a delete option and rotation, default retention 90 days (§6) |
| Availability | The console is part of all builds, there is no separate expert flavor |
| File commands | `backupwallet`, `restorewallet` and `dumptxoutset` are supported. Only wallets that are protected by a passphrase are backed up (§7) |
| Classification | As proposed in §5, derived from the node's `help` list, kept in `native-build/contract/console-tiers.json` |
| Danger confirmation | A typed confirmation token switches the Danger tier on, every Danger command shows a dialog with a delayed run button (§3) |
| Session timeout | 30 minutes of inactivity (§3) |
| Restore of an unencrypted backup | The restore runs, then a passphrase is required before the wallet can be used (§7) |
| Housekeeping in `exports/` | Dumps made by hand with `dumptxoutset` are deleted after 7 days. Wallet backups are kept until the user deletes them. The automatic checkpoint snapshots of the node are not touched (§7) |
| `createwallet` | Forbidden without a passphrase (§7) |

## 2. Findings From the Node

Checked against a host build of `elektron-net@main` (`447c069`, October 18, 2026) with the contract check tooling.

- The node lists 151 RPC commands in eight groups: Blockchain 38, Control 6, Mining 7, Network 15, Rawtransactions 21, Signer 1, Util 8, Wallet 55. The list is identical on mainnet and regtest. Some commands are hidden from that list but still callable, for example `invalidateblock`, `reconsiderblock` and the regtest block generation commands. The classification (§5) therefore cannot rely on the `help` list alone, and any command that is not classified, hidden or not, defaults to the Confirm tier.
- Console support commands exist: `help` (command list and per-command help), `getrpcinfo` (active commands), `abortrescan`, `uptime`, `logging`, `getmemoryinfo`.
- There is no `dumpprivkey`, `importprivkey`, `dumpwallet`, `sethdseed` or `importmulti`. Private keys of a descriptor wallet leave the wallet through `listdescriptors` with `private=true`, `gethdkeys` with `options.private=true`, and through `walletpassphrase` followed by signing commands.
- Commands that move money exist outside the app's PSBT review flow: `sendtoaddress`, `send`, `sendmany`, `sendall`, `sendrawtransaction`.
- Commands that can alter or replace chain state exist: `invalidateblock`, `reconsiderblock`, `preciousblock`, `loadtxoutset`, `pruneblockchain`, `setnetworkactive`, `stop`.
- Encryption is detectable: `getwalletinfo` contains `unlocked_until` only for wallets that are protected by a passphrase. A wallet created without a passphrase has no such field.
- `backupwallet` writes to any path the node process can reach and silently overwrites an existing file. The node does not confine it, so the app must.
- A backup of an encrypted wallet restores with `restorewallet`, stays encrypted and shows the same balance.
- `dumptxoutset` requires its `type` argument (`latest` or `rollback`) and refuses to overwrite an existing file. `rollback` temporarily rewinds the chain to an earlier block, which is not something to do on a pruned phone node.
- Because the app always builds against current Core (plan §11.1), the command set changes over time. New commands MUST NOT silently become available as harmless (§5).

## 3. Access and Session Rules

- **MUST:** off by default. Enabling it needs an explicit confirmation screen, shown in the user's language, that explains the risks in plain words: commands can spend funds and reveal keys, nobody legitimate asks for commands to be pasted, a pasted command from a stranger is a scam.
- **MUST:** device authentication (biometric or device credential) once per Expert session. The session starts with that authentication and ends when the app process ends, after 30 minutes of inactivity, or when the user ends it. Moving the app to the background does not end the session, but it relocks wallets (below).
- **MUST:** the **Danger tier is a second, separate switch** ("Allow commands that move funds or reveal keys"). Switching it on requires the user to confirm that they know what they are doing, by typing a fixed confirmation token in a dialog that states the consequences. It is off at the start of every session. In addition, every Danger command shows its full text in a confirmation dialog before it runs, and the run button becomes active after a short delay.
- **SHOULD:** a visible "Expert" indicator on every screen while the console is unlocked, and a stronger indicator while the Danger tier is on.
- **MUST:** the console screen sets `FLAG_SECURE` (no screenshots, no recent-apps preview). The Node section's other screens keep their own rules.
- **MUST:** leaving the console, backgrounding the app, or locking the device sends `walletlock` for every loaded wallet, because a console command may have unlocked one (§6).

## 4. Console Behavior

- **Input:** a single command line with history navigation. Soft keyboard settings: no autocorrect, no suggestions, no capitalization, with an extra key row for `{ } [ ] " : ,` and a paste button. Hardware keyboards get the usual shortcuts.
- **Syntax:** the format used by the desktop console and `bitcoin-cli`: `command arg1 arg2`, JSON values for arrays and objects, quotes for strings with spaces, named arguments as `name=value`. Nested calls and other conveniences are optional.
- **Autocomplete:** command names come from the node's own `help` list at runtime, so the list always matches the running binary. Argument names come from the per-command help.
- **Help viewer:** `help <command>` renders the node's help text in a scrollable monospace view. The help text stays in English (§9).
- **Wallet selection:** a selector for the target wallet, sent as the `/wallet/<name>` endpoint, so multiple wallets work without a `-rpcwallet` argument.
- **Output:** pretty-printed JSON with collapsible nodes. Large results (for example `listunspent`, `getrawmempool`, `getblock` at high verbosity) are paged or truncated with a clear marker, and a hard memory limit protects the app.
- **Long-running commands:** commands marked `heavy` in the classification (for example `gettxoutsetinfo`, `scantxoutset`, `scanblocks`, `verifychain`, `rescanblockchain`, `dumptxoutset`, the `waitfor*` commands) run asynchronously with a visible running state, a note that they can load the phone, and a Cancel action (`abortrescan` for rescans, request abort for others). Timeouts are explicit and shown.
- **Concurrency:** the console uses its own low-priority lane in the app's RPC client. A running console command MUST NOT starve the supervisor's health polling (plan §4.2) or the wallet's own calls.
- **Errors:** RPC errors show the raw node message and code, plus a short localized hint line for the codes the app knows (plan §3.5).
- **Copy and export:** copy of one result is allowed. Export of the whole session follows the log hygiene rule (plan §3.4): masked by default, raw only after explicit confirmation.

## 5. Command Tiers

Every command belongs to exactly one tier. The classification lives in [`native-build/contract/console-tiers.json`](../native-build/contract/console-tiers.json). It was derived from the node's `help` list (151 commands) plus the known hidden commands, and an unknown command is treated as tier **Confirm** until it is classified.

| Tier | Behavior | Commands (initial classification, 94 Open, 37 Confirm, 18 Danger, 2 Blocked among the listed commands) |
|---|---|---|
| **Open** | Runs immediately | Read-only commands, for example `getblockchaininfo`, `getnetworkinfo`, `getpeerinfo`, `getmempoolinfo`, `getmininginfo`, `getblock`, `getblockheader`, `getrawtransaction`, `gettxout`, `gettxoutsetinfo`, `getchaintips`, `getmemoryinfo`, `getrpcinfo`, `uptime`, `getbalances`, `listunspent`, `listtransactions`, `gettransaction`, `getaddressinfo`, `validateaddress`, `decodepsbt`, `decoderawtransaction`, `estimatesmartfee`, `walletcreatefundedpsbt`, `help` |
| **Confirm** | Shows the exact command and requires one confirmation tap | State-changing commands that do not move funds or reveal keys: `addnode`, `setban`, `clearbanned`, `disconnectnode`, `setnetworkactive`, `logging`, `savemempool`, `getblockfrompeer`, `pruneblockchain`, `preciousblock`, `rescanblockchain`, `abortrescan`, `setlabel`, `keypoolrefill`, `lockunspent`, `abandontransaction`, `bumpfee`, `psbtbumpfee`, `createwallet`, `loadwallet`, `unloadwallet`, `backupwallet`, `importdescriptors`, `fundrawtransaction`, `ping`. `createwallet` is Confirm and refused without a non-empty passphrase (§7) |
| **Danger** | Needs the Danger switch of §3, shows the command in a confirmation dialog with a delayed run button | Commands that move funds: `sendtoaddress`, `send`, `sendmany`, `sendall`, `sendrawtransaction`, `submitpackage`. Commands that reveal or handle keys: `listdescriptors` and `gethdkeys` with private output, `walletpassphrase`, `walletpassphrasechange`, `encryptwallet`, `signrawtransactionwithwallet`, `signrawtransactionwithkey`, `descriptorprocesspsbt`, `walletprocesspsbt` (unless `sign` is false), `signmessage`, `signmessagewithprivkey`. Commands that can damage chain state or the wallet, or write large files: `invalidateblock`, `reconsiderblock`, `dumptxoutset`, `migratewallet`, `restorewallet` |
| **Blocked** | Refused with an explanation | `stop` (use the Node controls so the supervisor knows), `loadtxoutset` (snapshots are managed by the node's own automatic mechanism, manual loading bypasses its checks), `dumptxoutset` with `type=rollback`, and the test-only commands (`generate*`, `setmocktime`, `mockscheduler`, `echo*`, `addconnection`, `addpeeraddress`, `sendmsgtopeer`, `syncwithvalidationinterfacequeue`) |

- **File format of the classification:** each command has a `tier`, optional `heavy`, optional `rules` that change the tier depending on an argument (`when: {arg, equals}`, with dotted paths for nested options), optional `path_args` (arguments that are file paths, §7), guard flags (`require_encrypted_wallet`, `require_encrypted_wallet_after`, `require_nonempty_args`) and `auto_delete_days` for files written by a command. The contract check verifies that these guards are still present (`console_guards` in `contract.json`). `hidden_commands` lists commands the `help` list does not show. `default_tier` is `confirm`.
- **Moving funds from the console** bypasses the app's PSBT review (plan §8.4). The confirmation dialog therefore shows the raw command in full and recommends the normal Send screen.
- **New Core versions:** the contract check (plan §11.2) compares the classification with the `help` list of the node that the build contains. Unclassified commands are reported as a warning in dev and nightly builds and fail release builds. Independent of that, it enforces invariants (for example `sendtoaddress` stays Danger or stricter, `stop` stays Blocked), so the classification cannot be weakened by accident.

## 6. Secrets, History and Wallet State

- **MUST:** arguments of `walletpassphrase`, `walletpassphrasechange`, `encryptwallet` and any argument named `passphrase` or `privkey` are masked in the visible history and are never written to a log, a crash report, a notification or the clipboard history.
- **MUST:** results of commands that reveal key material (`listdescriptors` and `gethdkeys` with private output, the signing commands) are shown once, are not kept in the scrollback after leaving the screen, are not stored in the history, and are not exported.
- **History is persistent.** The command text, with secrets masked, is stored in the app database, encrypted at rest with a Keystore-wrapped key (same mechanism as labels, plan §8.6). Results are not stored, except that the user may keep a result in the visible session.
  - **Rotation:** entries older than the retention period are deleted at app start and once per day. Default retention is 90 days. The user can choose off, 7, 30 or 90 days.
  - **Delete:** a visible "Delete history" action removes everything at once, and individual entries can be removed. Clearing app data removes the history with everything else, and it is never part of an Android backup (plan S2).
  - **Export:** history export follows the masked-by-default rule and needs an explicit confirmation.
- **MUST:** the app tracks which wallets the console unlocked and relocks them (§3). A `walletpassphrase` timeout typed in the console is capped by the app policy (the app may shorten it by sending `walletlock` earlier, it never extends it).
- **SHOULD:** a notice after any unlock performed through the console, so the user does not forget an unlocked wallet.
- Clipboard: copy of results uses the same auto-clear rule as addresses (plan S6).

## 7. File Commands: `backupwallet`, `restorewallet`, `dumptxoutset`

These three commands are supported. The node does not confine their paths, so the console does.

- **Location:** file arguments (`destination` of `backupwallet`, `backup_file` of `restorewallet`, `path` of `dumptxoutset`) are plain file names or paths inside the app's private `exports/` directory. The console resolves the name against that directory. Absolute paths outside it, `..` segments and symbolic links are refused. This is a rule of the console, because the node accepts any path (verified).
- **No overwriting:** the console refuses a destination that already exists. `backupwallet` overwrites silently if the console does not check (verified), `dumptxoutset` refuses by itself.
- **Backups only for passphrase-protected wallets:** before `backupwallet` runs, the console reads `getwalletinfo` for the target wallet. If `unlocked_until` is missing, the wallet has no passphrase, and the console refuses with an explanation and a pointer to encrypting the wallet first. Wallets created by the app always have a passphrase. The check also covers the case that a wallet was created without one through the console.
- **After a backup:** the console offers "Save to..." through the Android Storage Access Framework and "Share", and lists the files in `exports/` with a delete action. The backup file stays encrypted, because the wallet is.
- **Restore:** `restorewallet` reads from `exports/` or from an `imports/` staging directory that the user filled through the Storage Access Framework. After the restore, the console checks `unlocked_until` again. If the restored wallet is not passphrase-protected, the restore still runs, and the console then requires a passphrase before the wallet can be used: a dialog asks for it immediately, the wallet stays unavailable in the Wallet section and backups stay refused until it is set, and cancelling unloads the wallet. A restored backup of an encrypted wallet stays encrypted and shows the same balance (verified).
- **UTXO snapshot dump:** `dumptxoutset` is allowed with `type=latest` only. `type=rollback` is Blocked because it temporarily rewinds the chain, which is unsafe on a pruned phone node. The console checks free space against the storage floor first, runs the command as a long-running job with progress, and offers "Save to..." afterwards. The file contains public chain data only.
- **Housekeeping:** files in `exports/` are listed with their age. UTXO snapshot dumps that the user created by hand with `dumptxoutset` are deleted automatically after 7 days (`auto_delete_days` in the classification). Wallet backups are never deleted automatically, the user decides. The automatic checkpoint snapshots of the node (every 197,280 blocks, in the node's own `snapshots/` directory) are not part of `exports/` and are never touched by this housekeeping, they stay until the node replaces them at the next checkpoint.
- **`createwallet` requires a passphrase:** the console refuses `createwallet` unless the `passphrase` argument is present and not empty, so that no unprotected wallet can exist that could not be backed up. This also refuses watch-only wallets (`disable_private_keys`), because a wallet without private keys cannot carry a passphrase. A user who needs a watch-only wallet imports the descriptors into an encrypted wallet instead.
- All file commands are Danger tier (`restorewallet`, `dumptxoutset`) or Confirm tier with a guard (`backupwallet`).

## 8. Interaction With the Supervisor and the Wallet UI

- The console is just another client of the node. It cannot change the supervisor's state, and commands that would (`stop`, `setnetworkactive` false) are Blocked or Confirm with an explanation that the node status will change.
- The wallet UI reacts to changes made through the console (a wallet loaded or unloaded, a rescan started) by refreshing from the node instead of assuming its own state.
- When the node is not running, the console shows that state and offers to start it, it does not queue commands.

## 9. Internationalization

- Console chrome (buttons, dialogs, the risk confirmation, tier explanations, file command messages, error hints) is fully localized in every shipped language (plan §3.5), and the risk confirmation text and the Danger switch dialog are part of the native speaker review gate.
- Command names, arguments, JSON output and the node's help text stay in English. They are technical identifiers and are not translated.
- The confirmation token for the Danger switch is localized in the dialog text, but the typed token is a fixed, language-independent string to avoid keyboard layout problems.

## 10. Open Questions

None. The questions of the first draft are decided (§1.1). The exact texts and delays of the Danger dialog still go through a usability and security review before release (§12).

## 11. Test Strategy

- **Parser:** quoting, JSON arrays and objects, named arguments, invalid input, very long lines.
- **Classification:** `native-build/contract-check.py` already verifies the classification file against the node's `help` list, enforces the invariants and checks argument rules (`listdescriptors` with `private`, `gethdkeys` with `options.private`, `dumptxoutset` with `rollback`). The app additionally tests that unknown and hidden commands resolve to Confirm.
- **Gating:** Blocked commands cannot be sent through any code path of the console, Danger commands need the Danger switch and the per-command dialog.
- **File commands:** path validation (outside `exports/`, `..`, symlinks, existing files), the passphrase guard for `backupwallet` (refused for an unencrypted wallet, allowed for an encrypted one), restore round trip with balance check, restore of an unencrypted backup forces a passphrase, `dumptxoutset` with `rollback` refused, free-space check, `createwallet` without a passphrase refused, manual dumps deleted after 7 days while the node's automatic snapshots are untouched.
- **History:** secrets masked, key-revealing results not stored, encryption at rest, rotation removes entries older than the retention period, delete action, not part of Android backup.
- **Secrets:** no sensitive text in logs and crash reports, `FLAG_SECURE` on the console screen, relock of every console-unlocked wallet when leaving the console or backgrounding the app.
- **Robustness:** a very large result does not exhaust memory, cancel works for `rescanblockchain`, a console command does not delay the supervisor's polling.
- **Localization:** pseudo-locale run and screenshots of the risk confirmation and the Danger dialog in the longest locale.

## 12. Roadmap and Checklist

- Phase 1: a developer console in debug builds only, Open tier only, no persistence (inspection tool for the team).
- Phase 3: Expert mode for users with the session rules of §3, all four tiers including Danger, persistent history, file commands.
- Phase 4: external security review of the console and native speaker review of its texts.

- [x] Classification file derived from the `help` list, with argument rules and invariants
- [x] Contract check reports unclassified commands (warning in dev, failure in release), enforces invariants and verifies the guards (`createwallet` passphrase, encrypted-only backups, path arguments, auto delete)
- [ ] Expert mode gating (confirmation, device authentication once per session, session timeout)
- [ ] Danger switch and per-command confirmation dialog
- [ ] Console UI with autocomplete, help viewer, wallet selector, paging, cancel
- [ ] Persistent encrypted history with retention, delete and rotation
- [ ] File commands with path confinement, passphrase guard and `exports/` management
- [ ] Secrets handling and relock behavior verified
- [ ] Localization of all console texts in the launch locales
- [ ] Security review of the console before it ships
