#!/usr/bin/env bash
#
# connect-device.sh — one-shot connect + screen mirror for the remote Android
# device, over local WiFi (fast) or Tailscale (from anywhere).
#
# It figures out the current adb endpoint automatically, in this order:
#   1. Pinned port on the last-known local IP        (10.x / 192.168.x : 5555)
#   2. mDNS discovery on the local network           (adb mdns services)
#   3. Pinned port over Tailscale                     (TS_IP:5555)
#   4. Port scan of the local IP, then Tailscale IP
# Then it pins the device to a fixed port (5555) so next time is instant,
# checks that input-injection works, and launches scrcpy.
#
# Requirements ON THE PHONE (Xiaomi/HyperOS):
#   Developer options →
#     • Wireless debugging = ON   (turns itself off when the screen locks —
#                                   keep the screen awake while connecting)
#     • USB debugging (Security settings) = ON   (needed to CONTROL the UI;
#                                   without it scrcpy is view-only)
#
# Usage:
#   ./connect-device.sh                 # auto: connect + mirror
#   ./connect-device.sh connect         # auto: connect only (no scrcpy)
#   ./connect-device.sh mirror          # scrcpy only, assume connected
#   ./connect-device.sh pair IP:PORT CODE   # one-time Android-11+ pairing
#   ./connect-device.sh status          # show what's reachable

set -uo pipefail

# ─── Config ────────────────────────────────────────────────────────────────
TS_IP="100.74.104.58"     # phone's Tailscale IP (stable, works remotely)
PIN_PORT=5555             # fixed port we pin the device to via `adb tcpip`
SCAN_LO=30000             # port-scan range (adb wireless ports live here)
SCAN_HI=61000
STATE_FILE="${HOME}/.cache/connect-device.last"   # remembers last good local IP
# ────────────────────────────────────────────────────────────────────────────

# Prefer the Android SDK adb (same server Android Studio uses) if present.
SDK_ADB="${HOME}/Library/Android/sdk/platform-tools/adb"
ADB="$( [ -x "$SDK_ADB" ] && echo "$SDK_ADB" || command -v adb )"
[ -n "${ADB:-}" ] || { printf '\033[31m✗ adb not found\033[0m\n' >&2; exit 1; }

red()  { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; }
grn()  { printf '\033[32m✓ %s\033[0m\n' "$*"; }
cyn()  { printf '\033[36m▸ %s\033[0m\n' "$*"; }
ylw()  { printf '\033[33m! %s\033[0m\n' "$*"; }

online() { "$ADB" devices | grep -q "^$1[[:space:]]*device$"; }

reachable() { # true if the phone answers over Tailscale (i.e. it's awake — Doze kills this)
  tailscale ping --c 1 --timeout 4s "$TS_IP" 2>/dev/null | grep -qi pong
}

try() { # try connecting to host:port -> 0 if it ends up 'device'
  local t="$1"
  "$ADB" connect "$t" >/dev/null 2>&1
  sleep 1
  online "$t"
}

discover_mdns() { # print local host:port advertised for adb-tls-connect
  "$ADB" mdns services 2>/dev/null | awk '/_adb-tls-connect/ {print $NF; exit}'
}

scan() { # scan $1 across the port range with nc (2 passes — the relay drops SYNs), print first open host:port
  # NOTE: uses nc, not `timeout ... /dev/tcp` — macOS has no `timeout` command.
  local ip="$1" pass p all=""
  for pass in 1 2; do
    all+=$'\n'"$(seq "$SCAN_LO" "$SCAN_HI" | \
       xargs -P 400 -I{} sh -c 'nc -z -G 2 -w 2 '"$ip"' {} >/dev/null 2>&1 && echo {}' \
       2>/dev/null)"
  done
  p="$(printf '%s\n' "$all" | grep -E '^[0-9]+$' | sort -n | uniq | head -1)"
  [ -n "$p" ] && echo "${ip}:${p}"
}

last_local_ip() { [ -f "$STATE_FILE" ] && cat "$STATE_FILE"; }
save_local_ip() { mkdir -p "$(dirname "$STATE_FILE")"; echo "$1" > "$STATE_FILE"; }

find_endpoint() {
  # This phone runs Tailscale, so wireless-debugging binds to the tailnet — the
  # LAN IP and mDNS are unreachable (WiFi client isolation). Go straight to TS.
  local cand
  # gate FIRST: `adb connect` hangs ~60s on an unreachable phone, so check awake up front (~4s)
  cyn "checking the phone is awake…" >&2
  if ! reachable; then
    red "Phone is not answering over Tailscale — it's almost certainly ASLEEP." >&2
    ylw "Android Doze suspends Tailscale + wireless debugging when the screen is off." >&2
    ylw "Wake the screen (see the 'keep it reachable' tips), then run this again." >&2
    return 1
  fi
  # 1. pinned port over Tailscale (the normal, instant path)
  cyn "trying pinned ${TS_IP}:${PIN_PORT}…" >&2
  try "${TS_IP}:${PIN_PORT}" && { echo "${TS_IP}:${PIN_PORT}"; return 0; }
  # 2. scan the Tailscale IP (needed after a phone reboot rolls the port)
  cyn "port rolled — scanning ${TS_IP} ${SCAN_LO}-${SCAN_HI} (2 passes, ~60s)…" >&2
  cand="$(scan "$TS_IP")"
  if [ -n "$cand" ]; then
    try "$cand" && { echo "$cand"; return 0; }
  fi
  return 1
}

pin_port() { # ensure the device listens on PIN_PORT so future connects are instant
  local ep="$1" ip="${1%%:*}" port="${1##*:}"
  [ "$port" = "$PIN_PORT" ] && { echo "$ep"; return 0; }
  cyn "pinning to :${PIN_PORT} (adb tcpip)…" >&2
  "$ADB" -s "$ep" tcpip "$PIN_PORT" >/dev/null 2>&1
  sleep 2
  local newep="${ip}:${PIN_PORT}"
  try "$newep" && { echo "$newep"; return 0; }
  echo "$ep"   # pinning failed; keep the working endpoint
}

check_input() { # warn if Xiaomi is blocking input injection (view-only)
  local ep="$1" out
  out="$("$ADB" -s "$ep" shell input keyevent KEYCODE_WAKEUP 2>&1)"
  if echo "$out" | grep -q INJECT_EVENTS; then
    ylw "Screen will mirror but you CAN'T control it yet."
    ylw "On the phone: Developer options → enable 'USB debugging (Security settings)'."
    return 1
  fi
  return 0
}

mirror() { # launch scrcpy; lighter settings when we're on the high-latency Tailscale path
  local ep="$1"
  command -v scrcpy >/dev/null 2>&1 || { red "scrcpy not installed (brew install scrcpy)"; exit 1; }
  cyn "mirroring over Tailscale (~78ms)…"
  exec scrcpy --serial "$ep" --max-size 1280 --video-bit-rate 4M --max-fps 30 --stay-awake --window-title "Redmi A4 5G"
}

EP=""   # set by connect_flow to the live endpoint
connect_flow() {
  local ep
  ep="$(find_endpoint)" || {
    red "No adb endpoint found."
    ylw "On the phone, wake the screen and turn Developer options → 'Wireless debugging' ON, then retry."
    return 1
  }
  grn "connected: $ep"
  "$ADB" -s "$ep" shell getprop ro.product.model 2>/dev/null | sed 's/^/   model: /'
  # remember the local IP for next time
  [[ "$ep" == 10.* || "$ep" == 192.168.* || "$ep" == 172.* ]] && save_local_ip "${ep%%:*}"
  ep="$(pin_port "$ep")"
  grn "endpoint: $ep"
  check_input "$ep" || true
  EP="$ep"
}

case "${1:-mirror-auto}" in
  pair)
    [ $# -eq 3 ] || { red "Usage: $0 pair IP:PORT CODE"; exit 1; }
    "$ADB" pair "$2" "$3" && grn "Paired. Now run: $0"
    ;;
  connect)
    connect_flow || exit 1
    ;;
  mirror)
    ep="${TS_IP}:${PIN_PORT}"
    online "$ep" || { red "Not connected. Run: $0 connect"; exit 1; }
    mirror "$ep"
    ;;
  status)
    echo "adb: $ADB"; "$ADB" devices -l
    echo "--- mDNS ---"; "$ADB" mdns services 2>/dev/null
    echo "--- last local IP: $(last_local_ip) ---"
    ;;
  -h|--help|help)
    grep '^#' "$0" | sed 's/^# \{0,1\}//'
    ;;
  mirror-auto|*)
    connect_flow || exit 1
    mirror "$EP"
    ;;
esac
