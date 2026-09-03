#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
quality_root="$repository_root/docs/quality"
mode="${1:---manifest}"

if [[ ! -d "$quality_root" ]]; then
    echo "Quality documentation directory not found: $quality_root" >&2
    exit 1
fi

mapfile -d '' quality_files < <(
    find "$quality_root" -type f -print0 | LC_ALL=C sort -z
)

if [[ ${#quality_files[@]} -eq 0 ]]; then
    echo "No quality documents found beneath $quality_root" >&2
    exit 1
fi

manifest_file="$(mktemp)"
trap 'rm -f "$manifest_file"' EXIT

for quality_file in "${quality_files[@]}"; do
    relative_path="${quality_file#"$repository_root/"}"
    blob_hash="$(git -C "$repository_root" hash-object "$quality_file")"
    printf '%s\t%s\n' "$blob_hash" "$relative_path" >> "$manifest_file"
done

manifest_hash="$(sha256sum "$manifest_file" | awk '{print $1}')"

printf 'QUALITY_DOCUMENT_COUNT=%s\n' "${#quality_files[@]}"
printf 'QUALITY_MANIFEST_SHA256=%s\n' "$manifest_hash"
cat "$manifest_file"

case "$mode" in
    --manifest)
        ;;
    --print)
        for quality_file in "${quality_files[@]}"; do
            relative_path="${quality_file#"$repository_root/"}"
            printf '\n===== BEGIN %s =====\n' "$relative_path"
            cat "$quality_file"
            printf '\n===== END %s =====\n' "$relative_path"
        done
        ;;
    *)
        echo "Usage: $0 [--manifest|--print]" >&2
        exit 2
        ;;
esac
