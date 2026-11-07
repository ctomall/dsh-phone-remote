#!/usr/bin/env bash
# Guard installs, taps and screenshots against a shared emulator.
# Usage:
#   scripts/android-device-guard.sh <serial> <apk> <package> <expected-version-name>
# Exits non-zero instead of operating on a foreign package or device.
set -euo pipefail

serial="${1:?serial, e.g. emulator-5556}"
apk="${2:?apk path}"
package="${3:?package id}"
expected="${4:?expected versionName}"

lock="/tmp/dsh-android-${serial}.lock"
exec 9>"$lock"
if ! flock -n 9; then
  echo "device $serial is busy (lock $lock)" >&2
  exit 2
fi

if ! adb -s "$serial" get-state >/dev/null 2>&1; then
  echo "device $serial is not ready" >&2
  exit 3
fi

echo "installing $apk on $serial"
adb -s "$serial" install -r "$apk" >/dev/null

installed="$(adb -s "$serial" shell dumpsys package "$package" 2>/dev/null | tr -d '\r' | sed -n 's/.*versionName=//p' | head -1)"
if [ "$installed" != "$expected" ]; then
  echo "version mismatch: installed=$installed expected=$expected" >&2
  exit 4
fi

echo "ok: $package $installed on $serial"
