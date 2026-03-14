#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Wrapper rapido para la replica de pipeline de la app.
# Ejemplos:
#   ./scripts/test_llama_pipeline.sh
#   ./scripts/test_llama_pipeline.sh --query "Como tratar el tizon norteno" --debug
#   ./scripts/test_llama_pipeline.sh --llama-base-url http://127.0.0.1:8080

python3 scripts/run_llama_app_replica.py "$@"
