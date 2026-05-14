#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ -z "${MODEL_PATH:-}" && -f "$SCRIPT_DIR/best.pt" ]]; then
  export MODEL_PATH="$SCRIPT_DIR/best.pt"
elif [[ -z "${MODEL_PATH:-}" && -f "$SCRIPT_DIR/yolo26m.pt" ]]; then
  export MODEL_PATH="$SCRIPT_DIR/yolo26m.pt"
fi

export PORT="${PORT:-8000}"
python app.py
