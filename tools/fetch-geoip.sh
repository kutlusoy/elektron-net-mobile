#!/usr/bin/env bash
# Downloads DB-IP Country Lite (CC BY 4.0) for the peer country display. Never fails the build.
set -uo pipefail
dest="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/app/src/main/assets/geoip"
mkdir -p "$dest"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
for offset in 0 1 2; do
    month=$(date -u -d "$offset month ago" +%Y-%m)
    if curl -fsSL --retry 2 --connect-timeout 15 --max-time 180 "https://download.db-ip.com/free/dbip-country-lite-$month.mmdb.gz" -o "$tmp/db.gz" \
        && gzip -t "$tmp/db.gz" && gzip -dc "$tmp/db.gz" > "$tmp/db.mmdb" && [[ $(stat -c%s "$tmp/db.mmdb") -gt 1000000 ]]; then
        mv -f "$tmp/db.mmdb" "$dest/dbip-country-lite.mmdb"
        echo "geoip: installed DB-IP Country Lite $month"
        exit 0
    fi
done
echo "::warning::GeoIP database could not be downloaded, peers will show no country"
exit 0
