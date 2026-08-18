#!/usr/bin/env bash
#
# Offline dependency vulnerability scan for agent-hooks.
#
# This repository does NOT run vulnerability scanning in GitHub Actions. Long
# vulnerability-database acquisition does not belong in a required build, and a scan whose
# result depends on whatever a hosted runner happened to download is not reproducible
# evidence. Scanning is a local, offline step against a database snapshot that the caller
# has already validated and frozen.
#
# Usage:
#   TRIVY_CACHE_DIR=/path/to/validated-trivy-cache scripts/security-scan.sh sbom
#   TRIVY_CACHE_DIR=/path/to/validated-trivy-cache scripts/security-scan.sh rootfs
#   TRIVY_CACHE_DIR=/path/to/validated-trivy-cache scripts/security-scan.sh secrets
#
# Modes:
#   sbom     scan the aggregate CycloneDX SBOM produced by `./mvnw clean package`
#   rootfs   scan the actual resolved runtime JAR closure of every published module
#   secrets  scan the working tree for committed credentials
#
# The scan never updates the database and never contacts a vulnerability data service. It
# fails closed if TRIVY_CACHE_DIR is unset or does not contain both databases, because an
# unvalidated database silently produces a green report.
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="${1:-sbom}"
OUT="${OUT_DIR:-$ROOT/target/security}"

if [[ -z "${TRIVY_CACHE_DIR:-}" ]]; then
	echo "error: TRIVY_CACHE_DIR must point at a validated, frozen Trivy cache." >&2
	echo "       This script will not download a vulnerability database." >&2
	exit 2
fi
for db in "$TRIVY_CACHE_DIR/db/trivy.db" "$TRIVY_CACHE_DIR/java-db/trivy-java.db"; do
	[[ -f "$db" ]] || { echo "error: missing $db" >&2; exit 2; }
done

command -v trivy >/dev/null || { echo "error: trivy is not on PATH" >&2; exit 2; }

mkdir -p "$OUT"

trivy_offline() {
	trivy --cache-dir "$TRIVY_CACHE_DIR" "$@" \
		--skip-db-update --skip-java-db-update --offline-scan --disable-telemetry
}

case "$MODE" in
sbom)
	BOM="$ROOT/target/agent-hooks-bom.json"
	[[ -f "$BOM" ]] || { echo "error: $BOM not found — run ./mvnw clean package first" >&2; exit 2; }
	trivy_offline sbom --scanners vuln --format json --output "$OUT/trivy-sbom-vulnerabilities.json" "$BOM"
	trivy_offline sbom --scanners vuln --format table "$BOM"
	;;
rootfs)
	# The SBOM is generated inventory; this scans the JARs a consumer actually resolves.
	CLOSURE="$OUT/runtime-closure"
	rm -rf "$CLOSURE"
	mkdir -p "$CLOSURE"
	for module in agent-hooks-core agent-hooks-spring agent-hooks-claude agent-hooks-gemini; do
		"$ROOT/mvnw" -q -B -pl "$module" dependency:copy-dependencies \
			-DincludeScope=runtime -DoutputDirectory="$CLOSURE"
		cp "$ROOT/$module/target/"*.jar "$CLOSURE/" 2>/dev/null || true
	done
	trivy_offline rootfs --scanners vuln --format json --output "$OUT/trivy-rootfs-vulnerabilities.json" "$CLOSURE"
	trivy_offline rootfs --scanners vuln --format table "$CLOSURE"
	;;
secrets)
	trivy_offline fs --scanners secret --skip-dirs target --skip-dirs .git \
		--format json --output "$OUT/trivy-secret-scan.json" "$ROOT"
	trivy_offline fs --scanners secret --skip-dirs target --skip-dirs .git --format table "$ROOT"
	;;
*)
	echo "usage: $0 [sbom|rootfs|secrets]" >&2
	exit 2
	;;
esac

echo "wrote results under $OUT"
