#!/bin/bash
# پلِ CryptoKitِ آیفون را روی همین مک می‌سازد و با بردارهای منتشرشده می‌سنجد —
# همان بردارهایی که `LanCryptoTest`ِ جاوا. از ریشهٔ مخزن: iosApp/CryptoInterop/run.sh
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
swiftc -O -o "$out/interop" "$here/../KhayatYar/LanCryptoKit.swift" "$here/main.swift"
"$out/interop"
