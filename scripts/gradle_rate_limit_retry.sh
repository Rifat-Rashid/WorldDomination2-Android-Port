#!/usr/bin/env bash
# Retry a Gradle invocation only when Maven Central explicitly rate-limits it.
set -uo pipefail

log_file="$(mktemp)"
trap 'rm -f -- "$log_file"' EXIT

for attempt in 1 2 3; do
  if "$@" 2>&1 | tee "$log_file"; then
    exit 0
  else
    statuses=("${PIPESTATUS[@]}")
    status=${statuses[0]}
    if (( statuses[1] != 0 )); then exit "${statuses[1]}"; fi
  fi
  if (( attempt == 3 )) \
      || grep -Eq 'There were failing tests|[0-9]+ tests? completed, [0-9]+ failed' "$log_file" \
      || ! grep -Eq "repo\.maven\.apache\.org/.*Received status code 429 from server: Too Many Requests" "$log_file"; then
    exit "$status"
  fi
  echo "Maven Central returned HTTP 429. Retrying the same Gradle tasks ($attempt/3)." >&2
  sleep "$((attempt * 10))"
done
