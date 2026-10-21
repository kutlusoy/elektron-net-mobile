#!/usr/bin/env python3
import argparse
import re
import sys

PATTERN = re.compile(r"^(\d+)\.(\d+)\.(\d+)-m(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z.]+)?$")
LIMITS = {"node major": 9, "node minor": 99, "node patch": 99, "mobile major": 9, "mobile minor": 9, "mobile patch": 99}


def parse(version):
    m = PATTERN.match(version)
    if not m:
        raise ValueError(f"'{version}' does not look like <node>-m<mobile>, for example 4.0.4-m0.1.0")
    a, b, c, x, y, z = (int(g) for g in m.groups()[:6])
    for (name, limit), value in zip(LIMITS.items(), (a, b, c, x, y, z)):
        if value > limit:
            raise ValueError(f"{name} {value} is above {limit}, the version code scheme has to be extended")
    return {
        "version": version,
        "tag": "v" + version,
        "node_version": f"{a}.{b}.{c}",
        "mobile_version": f"{x}.{y}.{z}",
        "suffix": m.group(7) or "",
        "prerelease": "true" if m.group(7) else "false",
        "version_code": str(a * 100_000_000 + b * 1_000_000 + c * 10_000 + x * 1000 + y * 100 + z),
    }


def selftest():
    cases = {
        "4.0.4-m0.1.0": 400040100,
        "4.0.4-m0.1.1": 400040101,
        "4.0.4-m0.2.0": 400040200,
        "4.0.5-m0.1.0": 400050100,
        "4.0.6-m0.1.0": 400060100,
        "4.0.6-m0.1.1": 400060101,
        "4.1.0-m0.1.0": 401000100,
        "5.0.0-m1.0.0": 500001000,
    }
    for version, code in cases.items():
        assert int(parse(version)["version_code"]) == code, (version, code)
    ordered = ["4.0.4-m0.1.0", "4.0.4-m0.1.1", "4.0.4-m0.2.0", "4.0.5-m0.1.0", "4.0.6-m0.1.0", "4.1.0-m0.1.0", "5.0.0-m0.1.0"]
    codes = [int(parse(v)["version_code"]) for v in ordered]
    assert codes == sorted(codes) and len(set(codes)) == len(codes)
    assert max(codes) < 2_100_000_000
    assert parse("4.0.4-m0.1.0-rc1")["prerelease"] == "true"
    assert parse("4.0.4-m0.1.0")["prerelease"] == "false"
    for bad in ["4.0.4", "v4.0.4-m0.1.0", "4.0.4-m0.1", "4.0.4-0.1.0", "10.0.0-m0.1.0", "4.0.4-m0.10.0", "4.100.0-m0.1.0"]:
        try:
            parse(bad)
        except ValueError:
            continue
        raise AssertionError(f"accepted {bad}")
    print("version selftest ok")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("version", nargs="?")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--mobile-file", help="file that holds the mobile version, must match the version argument")
    args = ap.parse_args()
    if args.selftest:
        selftest()
        return
    if not args.version:
        ap.error("version required")
    try:
        info = parse(args.version.removeprefix("v"))
        if args.mobile_file:
            declared = open(args.mobile_file).read().strip()
            if declared != info["mobile_version"]:
                raise ValueError(f"{args.mobile_file} says {declared} but the version says {info['mobile_version']}, update the file first")
    except ValueError as e:
        print(f"version: {e}", file=sys.stderr)
        sys.exit(1)
    for k, v in info.items():
        print(f"{k}={v}")


if __name__ == "__main__":
    main()
