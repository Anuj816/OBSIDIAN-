#!/usr/bin/env python3
"""Download and SHA-256 verify registry packages so Flutter embeds them in the APK."""
from __future__ import annotations
import hashlib
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "assets" / "bundled_extensions"
REGISTRY_URL = os.environ.get(
    "SPOTIFLAC_EXTENSION_REGISTRY",
    "https://raw.githubusercontent.com/zarzet/SpotiFLAC-Extension/main/registry.json",
)
USER_AGENT = "SpotiFLAC-Mobile-BundledExtensions/1.0"


def fetch(url: str, timeout: int = 45) -> bytes:
    if not url.startswith("https://"):
        raise ValueError(f"Refusing non-HTTPS URL: {url}")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return response.read()


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    registry_bytes = fetch(REGISTRY_URL)
    registry = json.loads(registry_bytes.decode("utf-8"))
    extensions = registry.get("extensions")
    if not isinstance(extensions, list) or not extensions:
        raise ValueError("Registry has no extensions array")

    manifest_items = []
    seen_ids = set()
    for entry in extensions:
        ext_id = entry.get("id")
        url = entry.get("download_url")
        expected = str(entry.get("sha256", "")).lower()
        if not ext_id or not isinstance(url, str) or len(expected) != 64:
            raise ValueError(f"Malformed extension entry: {entry!r}")
        if ext_id in seen_ids:
            raise ValueError(f"Duplicate extension id: {ext_id}")
        seen_ids.add(ext_id)
        filename = f"{ext_id}.sflx"
        target = OUT / filename
        if target.is_file():
            existing_hash = hashlib.sha256(target.read_bytes()).hexdigest()
        else:
            existing_hash = ""
        if existing_hash == expected:
            payload_size = target.stat().st_size
        else:
            payload = fetch(url)
            actual = hashlib.sha256(payload).hexdigest()
            if actual != expected:
                raise ValueError(
                    f"SHA-256 mismatch for {ext_id}: expected {expected}, got {actual}"
                )
            temp = target.with_suffix(target.suffix + ".tmp")
            temp.write_bytes(payload)
            temp.replace(target)
            payload_size = len(payload)
        manifest_items.append({
            "id": ext_id,
            "name": entry.get("display_name") or entry.get("name") or ext_id,
            "version": str(entry.get("version", "0.0.0")),
            "filename": filename,
            "sha256": expected,
            "download_url": url,
            "category": entry.get("category", "integration"),
        })
        print(f"Bundled {ext_id} {entry.get('version', '')} ({payload_size} bytes, SHA-256 verified)")

    manifest = {
        "registry_url": REGISTRY_URL,
        "registry_updated_at": registry.get("updated_at"),
        "extensions": manifest_items,
    }
    manifest_path = OUT / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    # Remove stale package files so only registry-listed packages are embedded.
    keep = {item["filename"] for item in manifest_items} | {"manifest.json"}
    for path in OUT.iterdir():
        if path.is_file() and path.name not in keep and path.suffix == ".sflx":
            path.unlink()
    print(f"Verified and staged {len(manifest_items)} extension packages")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, json.JSONDecodeError, urllib.error.URLError) as exc:
        print(f"Bundled extension preparation failed: {exc}", file=sys.stderr)
        raise SystemExit(1)
