#!/usr/bin/env bash

URL="https://justrealrin.github.io/"

if command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$URL" >/dev/null 2>&1 &
  exit 0
fi

if command -v sensible-browser >/dev/null 2>&1; then
  sensible-browser "$URL" >/dev/null 2>&1 &
  exit 0
fi

if command -v gnome-open >/dev/null 2>&1; then
  gnome-open "$URL" >/dev/null 2>&1 &
  exit 0
fi

if command -v kde-open5 >/dev/null 2>&1; then
  kde-open5 "$URL" >/dev/null 2>&1 &
  exit 0
fi
if command -v kde-open >/dev/null 2>&1; then
  kde-open "$URL" >/dev/null 2>&1 &
  exit 0
fi

BROWSERS=("google-chrome" "chrome" "chromium" "firefox" "opera" "brave" "edge")
for b in "${BROWSERS[@]}"; do
  if command -v "$b" >/dev/null 2>&1; then
    "$b" "$URL" >/dev/null 2>&1 &
    exit 0
  fi
done

echo "ERROR"
exit 1