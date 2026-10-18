#!/usr/bin/env python3
"""Verifies that an elektrond binary still provides the interface the app depends on.

Usage: contract-check.py <path to elektrond> [--contract contract/contract.json] [--tiers contract/console-tiers.json] [--strict-tiers]

The binary must run on this machine (the host build, not the Android one).
Exit status is 1 if any check fails.
"""
import argparse
import base64
import glob
import hashlib
import hmac
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
RPC_USER = "contract"
RPC_PASSWORD = "contract"
WALLET_PASSPHRASE = "contract-passphrase"
RESULTS = []


def record(ok, name, detail=""):
    RESULTS.append((ok, name, detail))
    print(("PASS " if ok else "FAIL ") + name + (": " + detail if detail and not ok else ""))
    return ok


def base58check(payload):
    alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    data = payload + hashlib.sha256(hashlib.sha256(payload).digest()).digest()[:4]
    number = int.from_bytes(data, "big")
    encoded = ""
    while number:
        number, rest = divmod(number, 58)
        encoded = alphabet[rest] + encoded
    return "1" * (len(data) - len(data.lstrip(b"\0"))) + encoded


def test_master_key():
    """Fixed master private key in testnet/regtest encoding, derived from a constant test seed."""
    digest = hmac.new(b"Bitcoin seed", b"elektron-mobile-contract-check", hashlib.sha512).digest()
    return base58check(bytes.fromhex("04358394") + b"\0" * 9 + digest[32:] + b"\0" + digest[:32])


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class RpcError(Exception):
    def __init__(self, code, message):
        super().__init__(f"{code}: {message}")
        self.code = code


class Node:
    def __init__(self, daemon, chain, extra_args=()):
        self.daemon = daemon
        self.chain = chain
        self.extra_args = list(extra_args)
        self.datadir = tempfile.mkdtemp(prefix="contract-node-")
        self.rpc_port = free_port()
        self.proc = None
        self.log = None

    def start(self, extra_args=None):
        # The node writes a default bitcoin.conf with mainnet peers on first launch, which breaks other chains
        open(os.path.join(self.datadir, "bitcoin.conf"), "a").close()
        args = [
            self.daemon, f"-datadir={self.datadir}", f"-chain={self.chain}",
            "-server=1", f"-rpcuser={RPC_USER}", f"-rpcpassword={RPC_PASSWORD}",
            "-rpcbind=127.0.0.1", "-rpcallowip=127.0.0.1", f"-rpcport={self.rpc_port}",
            f"-port={free_port()}", "-listen=0", "-dnsseed=0", "-fixedseeds=0",
            "-discover=0", "-connect=0", "-fallbackfee=0.00001",
        ] + (self.extra_args if extra_args is None else extra_args)
        self.log = open(os.path.join(self.datadir, "stdout.log"), "ab")
        self.proc = subprocess.Popen(args, stdout=self.log, stderr=subprocess.STDOUT)
        deadline = time.time() + 120
        while time.time() < deadline:
            if self.proc.poll() is not None:
                self.log.flush()
                with open(self.log.name, errors="replace") as f:
                    tail = "".join(f.readlines()[-15:])
                raise RuntimeError("elektrond exited during startup:\n" + tail)
            try:
                self.rpc("getblockchaininfo")
                return
            except (RpcError, urllib.error.URLError, ConnectionError, OSError):
                time.sleep(0.5)
        raise RuntimeError("elektrond did not become ready")

    def rpc(self, method, wallet=None, **params):
        path = "/wallet/" + wallet if wallet else "/"
        body = json.dumps({"jsonrpc": "1.0", "id": "contract", "method": method, "params": params}).encode()
        request = urllib.request.Request(f"http://127.0.0.1:{self.rpc_port}{path}", body)
        token = base64.b64encode(f"{RPC_USER}:{RPC_PASSWORD}".encode()).decode()
        request.add_header("Authorization", "Basic " + token)
        request.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                payload = json.load(response)
        except urllib.error.HTTPError as err:
            payload = json.load(err)
        if payload.get("error"):
            raise RpcError(payload["error"]["code"], payload["error"]["message"])
        return payload["result"]

    def stop(self):
        if self.proc and self.proc.poll() is None:
            try:
                self.rpc("stop")
            except (RpcError, OSError):
                pass
            try:
                self.proc.wait(timeout=60)
            except subprocess.TimeoutExpired:
                self.proc.kill()
        if self.log:
            self.log.close()
            self.log = None

    def cleanup(self):
        self.stop()
        shutil.rmtree(self.datadir, ignore_errors=True)


def help_arg_names(node, method):
    text = node.rpc("help", command=method)
    if text.startswith("help: unknown command"):
        raise RpcError(-32601, "unknown command")
    return set(re.findall(r"^\s*\d+\.\s+(\w+)", text, re.M))


def check_cli_args(daemon, contract):
    out = subprocess.run([daemon, "-help-debug"], capture_output=True, text=True, check=True).stdout
    for arg in contract["node_args"]:
        found = re.search(rf"^\s*{re.escape(arg)}(?:[=\s]|$)", out, re.M) is not None
        record(found, f"node argument {arg}", "not listed by -help-debug")


def check_rpc_surface(node, contract):
    for method, spec in contract["rpc"].items():
        try:
            names = help_arg_names(node, method)
        except RpcError as err:
            record(False, f"rpc {method}", str(err))
            continue
        missing = [a for a in spec.get("args", []) if a not in names]
        record(not missing, f"rpc {method}", "missing arguments: " + ", ".join(missing))
    for method in contract.get("optional_rpc", []):
        try:
            help_arg_names(node, method)
            print(f"INFO optional rpc {method} present")
        except RpcError:
            print(f"WARN optional rpc {method} not present")


TIER_ORDER = ["open", "confirm", "danger", "blocked"]


def resolve_tier(entry, args):
    tier = entry["tier"]
    for rule in entry.get("rules", []):
        value = args
        for part in rule["when"]["arg"].split("."):
            value = value.get(part) if isinstance(value, dict) else None
        if value == rule["when"]["equals"]:
            tier = rule["tier"]
    return tier


def listed_commands(node):
    return [line.split()[0] for line in node.rpc("help").splitlines() if line.strip() and not line.startswith("==")]


def check_console_tiers(node, contract, tiers, strict):
    known = dict(tiers["commands"], **tiers["hidden_commands"])
    record(all(e["tier"] in TIER_ORDER for e in known.values()), "console tiers use known tier names")
    listed = listed_commands(node)
    unclassified = [c for c in listed if c not in known]
    if strict:
        record(not unclassified, "every listed command has a console tier", ", ".join(unclassified))
    elif unclassified:
        print("WARN unclassified commands (treated as confirm): " + ", ".join(unclassified))
    stale = [c for c in tiers["commands"] if c not in listed]
    if stale:
        print("INFO console tiers list commands the node no longer shows: " + ", ".join(stale))
    for inv in contract["console_invariants"]:
        entry = known.get(inv["command"])
        tier = resolve_tier(entry, inv.get("args", {})) if entry else tiers["default_tier"]
        ok = TIER_ORDER.index(tier) >= TIER_ORDER.index(inv["at_least"]) if inv["at_least"] != "open" \
            else tier == "open"
        record(ok, f"console tier of {inv['command']} {inv.get('args', '')}".strip(), f"{tier}, need {inv['at_least']}")


def check_console_guards(contract, tiers):
    known = dict(tiers["commands"], **tiers["hidden_commands"])
    for guard in contract["console_guards"]:
        value = known.get(guard["command"], {}).get(guard["flag"])
        ok = bool(value) and ("contains" not in guard or guard["contains"] in value)
        record(ok, f"console guard {guard['flag']} on {guard['command']}", f"found {value!r}")


def check_mainnet(daemon, contract, tiers, strict):
    node = Node(daemon, "main")
    try:
        node.start()
        info = node.rpc("getblockchaininfo")
        record(info["chain"] == "main", "mainnet chain name", info["chain"])
        genesis = node.rpc("getblockhash", height=0)
        record(genesis == contract["genesis_hash"], "mainnet genesis hash", genesis)
        net = node.rpc("getnetworkinfo")
        record(net["protocolversion"] >= contract["min_protocol_version"], "protocol version", str(net["protocolversion"]))
        record(contract["subversion_contains"] in net["subversion"], "user agent", net["subversion"])
        record(info.get("pruned") is True, "node reports pruned", str(info.get("pruned")))
        for field in contract["blockchaininfo_fields"]:
            record(field in info, f"getblockchaininfo.{field}", "missing")
        for field in contract["networkinfo_fields"]:
            record(field in net, f"getnetworkinfo.{field}", "missing")
        check_rpc_surface(node, contract)
        check_console_tiers(node, contract, tiers, strict)
        check_console_guards(contract, tiers)
    finally:
        node.cleanup()


def send_psbt(node, wallet, destination):
    psbt = node.rpc("walletcreatefundedpsbt", wallet=wallet, inputs=[], outputs=[{destination: 1.0}],
                    locktime=0, options={"fee_rate": 2}, bip32derivs=True)
    decoded = node.rpc("decodepsbt", psbt=psbt["psbt"])
    outputs = [o["scriptPubKey"].get("address") for o in decoded["tx"]["vout"]]
    record(destination in outputs, "psbt contains the destination", str(outputs))
    record(psbt["fee"] > 0, "psbt reports a fee", str(psbt["fee"]))
    node.rpc("walletpassphrase", wallet=wallet, passphrase=WALLET_PASSPHRASE, timeout=10)
    try:
        processed = node.rpc("walletprocesspsbt", wallet=wallet, psbt=psbt["psbt"], sign=True, finalize=True)
    finally:
        node.rpc("walletlock", wallet=wallet)
    finalized = node.rpc("finalizepsbt", psbt=processed["psbt"])
    record(finalized.get("complete") is True, "psbt is complete after signing", str(finalized))
    return node.rpc("sendrawtransaction", hexstring=finalized["hex"])


def descriptor_requests(node, xprv, timestamp="now"):
    requests = []
    for coin_type, active in (("1370h", True), ("0h", False)):
        for internal in (False, True):
            branch = 1 if internal else 0
            raw = f"wpkh({xprv}/84h/{coin_type}/0h/{branch}/*)"
            desc = raw + "#" + node.rpc("getdescriptorinfo", descriptor=raw)["checksum"]
            requests.append({"desc": desc, "active": active, "internal": internal,
                             "range": [0, 49], "next_index": 0, "timestamp": timestamp})
    return requests


def check_recovery(node, contract):
    expected = node.rpc("getbalances", wallet="app")["mine"]["trusted"]
    node.rpc("createwallet", wallet_name="restored", blank=True, passphrase=WALLET_PASSPHRASE, descriptors=True)
    info = node.rpc("getblockchaininfo")
    # All regtest blocks are minutes old, so "now" would fall inside the window that needs pruned blocks
    future = info["time"] + 7300
    node.rpc("walletpassphrase", wallet="restored", passphrase=WALLET_PASSPHRASE, timeout=120)
    try:
        results = node.rpc("importdescriptors", wallet="restored",
                           requests=descriptor_requests(node, test_master_key(), timestamp=future))
        record(all(r["success"] for r in results), "restore imports descriptors without a pruned rescan", str(results))
        node.rpc("rescanblockchain", wallet="restored", start_height=info["pruneheight"])
        record(True, "rescanblockchain from pruneheight runs while the wallet is unlocked")
    finally:
        node.rpc("walletlock", wallet="restored")
    retained = node.rpc("getbalances", wallet="restored")["mine"]["trusted"]
    record(0 < retained < expected, "rescan from pruneheight restores only retained history",
           f"restored {retained}, full {expected}")

    try:
        help_arg_names(node, "rescanutxoset")
    except RpcError:
        print(f"WARN rescanutxoset missing: restored wallet shows {retained} of {expected}, pruned coins stay unrecoverable")
        return
    node.rpc("rescanutxoset", wallet="restored")
    restored = node.rpc("getbalances", wallet="restored")["mine"]["trusted"]
    record(restored == expected, "rescanutxoset restores the full balance", f"restored {restored}, expected {expected}")


def check_backup_and_dump(node):
    exports = os.path.join(node.datadir, "exports")
    os.makedirs(exports)

    node.rpc("createwallet", wallet_name="plain")
    record("unlocked_until" not in node.rpc("getwalletinfo", wallet="plain"),
           "an unencrypted wallet has no unlocked_until")
    record("unlocked_until" in node.rpc("getwalletinfo", wallet="app"), "an encrypted wallet reports unlocked_until")

    backup = os.path.join(exports, "app.bak")
    node.rpc("backupwallet", wallet="app", destination=backup)
    node.rpc("restorewallet", wallet_name="app_restored", backup_file=backup)
    restored = node.rpc("getwalletinfo", wallet="app_restored")
    record("unlocked_until" in restored, "a restored backup is still encrypted")
    record(node.rpc("getbalances", wallet="app_restored")["mine"]["trusted"]
           == node.rpc("getbalances", wallet="app")["mine"]["trusted"], "a restored backup shows the same balance")
    try:
        node.rpc("backupwallet", wallet="app", destination=backup)
        print("INFO backupwallet overwrites an existing file, the app must refuse existing paths")
    except RpcError:
        print("INFO backupwallet refuses to overwrite an existing file")

    dump = os.path.join(exports, "utxo.dat")
    result = node.rpc("dumptxoutset", path=dump, type="latest")
    record(result["coins_written"] > 0 and os.path.getsize(dump) > 0, "dumptxoutset type latest writes a snapshot",
           str(result))
    try:
        node.rpc("dumptxoutset", path=dump, type="latest")
        print("INFO dumptxoutset overwrites an existing file")
    except RpcError:
        print("INFO dumptxoutset refuses to overwrite an existing file")


def block_files(node):
    return sorted(os.path.basename(f) for f in glob.glob(os.path.join(node.datadir, node.chain, "blocks", "blk*.dat")))


def snapshot_files(node):
    return sorted(os.listdir(os.path.join(node.datadir, node.chain, "snapshots"))) \
        if os.path.isdir(os.path.join(node.datadir, node.chain, "snapshots")) else []


def check_regtest(daemon, contract):
    node = Node(daemon, "regtest", ["-fastprune", "-natpmp=0"])
    try:
        node.start()
        node.rpc("createwallet", wallet_name="source")
        xprv = test_master_key()

        node.rpc("createwallet", wallet_name="app", blank=True, passphrase=WALLET_PASSPHRASE, descriptors=True)
        node.rpc("walletpassphrase", wallet="app", passphrase=WALLET_PASSPHRASE, timeout=10)
        results = node.rpc("importdescriptors", wallet="app", requests=descriptor_requests(node, xprv))
        node.rpc("walletlock", wallet="app")
        record(all(r["success"] for r in results), "importdescriptors for both coin types", str(results))

        address = node.rpc("getnewaddress", wallet="app", address_type="bech32")
        record(address.startswith(contract["regtest_hrp"] + "1q"), "native segwit address prefix", address)
        record("/1370h/" in node.rpc("getaddressinfo", wallet="app", address=address)["desc"],
               "new addresses use coin type 1370")
        record(node.rpc("getwalletinfo", wallet="app").get("unlocked_until") == 0, "wallet is locked after import")

        node.rpc("generatetoaddress", nblocks=700, address=address)
        info = node.rpc("getblockchaininfo")
        record(info["blocks"] == 700, "mined 700 regtest blocks", str(info["blocks"]))

        files = block_files(node)
        record(len(files) >= 2 and "blk00000.dat" not in files, "fastprune rolled and pruned block files", str(files[:5]))
        record(info["pruneheight"] > 0, "pruneheight advances", str(info["pruneheight"]))

        interval = contract["checkpoint_interval_regtest"]
        expected_height = (info["blocks"] // interval) * interval
        snaps = snapshot_files(node)
        dats = [f for f in snaps if f.endswith(".dat")]
        hashes = [f for f in snaps if f.endswith(".hash")]
        record(len(dats) == 1 and len(hashes) == 1 and hashes[0] == dats[0] + ".hash",
               "one checkpoint snapshot with hash sidecar", str(snaps))
        record(bool(dats) and dats[0].startswith(f"{expected_height}-"), "snapshot is for the latest checkpoint",
               f"expected height {expected_height}, files {snaps}")

        destination = node.rpc("getnewaddress", wallet="source")
        txid = send_psbt(node, "app", destination)
        node.rpc("generatetoaddress", nblocks=1, address=address)
        confirmed = node.rpc("gettransaction", wallet="app", txid=txid)["confirmations"]
        record(confirmed >= 1, "sent transaction confirms", str(confirmed))
        balance = node.rpc("getbalances", wallet="app")["mine"]["trusted"]
        record(balance > 0, "wallet reports a balance", str(balance))

        check_recovery(node, contract)
        check_backup_and_dump(node)

        node.stop()
        node.start(extra_args=["-natpmp=0"])
        tip = node.rpc("getblockchaininfo")["blocks"]
        node.rpc("generatetoaddress", nblocks=20, address=address)
        record(node.rpc("getblockchaininfo")["blocks"] == tip + 20, "node keeps working after turning fastprune off")
    finally:
        node.cleanup()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("daemon")
    parser.add_argument("--contract", default=os.path.join(HERE, "contract", "contract.json"))
    parser.add_argument("--tiers", default=os.path.join(HERE, "contract", "console-tiers.json"))
    parser.add_argument("--strict-tiers", action="store_true", help="fail on commands without a console tier")
    args = parser.parse_args()
    with open(args.contract) as f:
        contract = json.load(f)
    with open(args.tiers) as f:
        tiers = json.load(f)

    check_cli_args(args.daemon, contract)
    check_mainnet(args.daemon, contract, tiers, args.strict_tiers)
    check_regtest(args.daemon, contract)

    failed = [r for r in RESULTS if not r[0]]
    print(f"\n{len(RESULTS) - len(failed)} passed, {len(failed)} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
