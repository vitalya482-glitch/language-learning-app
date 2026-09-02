#!/usr/bin/env python3
"""Prepare a reproducible Android update bundle for LVK-style distribution."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

SEMVER_RE = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")


def load_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Invalid property line in {path}: {raw_line!r}")
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version-file", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--apk-directory", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--commit-subject", default="")
    parser.add_argument("--run-id", default="")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    properties = load_properties(args.version_file)

    version_name = properties.get("APP_VERSION_NAME", "")
    if not SEMVER_RE.fullmatch(version_name):
        raise ValueError(f"APP_VERSION_NAME must look like semantic versioning, got {version_name!r}")

    try:
        expected_version_code = int(properties["ANDROID_VERSION_CODE"])
    except (KeyError, ValueError) as exc:
        raise ValueError("ANDROID_VERSION_CODE must be an integer") from exc
    if expected_version_code <= 0:
        raise ValueError("ANDROID_VERSION_CODE must be positive")

    metadata = json.loads(args.metadata.read_text(encoding="utf-8"))
    elements = metadata.get("elements") or []
    if len(elements) != 1:
        raise ValueError(f"Expected exactly one APK output, got {len(elements)}")

    element = elements[0]
    built_version_name = str(element.get("versionName", ""))
    built_version_code = int(element.get("versionCode", 0))
    if built_version_name != version_name:
        raise ValueError(
            f"Built versionName {built_version_name!r} does not match version.properties {version_name!r}"
        )
    if built_version_code != expected_version_code:
        raise ValueError(
            f"Built versionCode {built_version_code} does not match version.properties {expected_version_code}"
        )

    application_id = metadata.get("applicationId", "")
    expected_application_id = "kz.lvk.languagelearning.dev"
    if application_id != expected_application_id:
        raise ValueError(f"Unexpected applicationId {application_id!r}")

    source_apk = args.apk_directory / element["outputFile"]
    if not source_apk.is_file():
        raise FileNotFoundError(source_apk)

    out = args.output_directory
    out.mkdir(parents=True, exist_ok=True)
    apk_name = "language-learning-dev.apk"
    output_apk = out / apk_name
    shutil.copy2(source_apk, output_apk)

    digest = sha256_file(output_apk)
    size = output_apk.stat().st_size
    (out / f"{apk_name}.sha256").write_text(f"{digest}  {apk_name}\n", encoding="utf-8")

    short_commit = args.commit[:7]
    release_date = datetime.now(timezone.utc).date().isoformat()
    download_base = f"https://github.com/{args.repository}/releases/download/{args.release_tag}"

    notes = [f"Автоматическая dev-сборка {version_name}."]
    if args.commit_subject:
        notes.append(f"{short_commit}: {args.commit_subject}")
    else:
        notes.append(f"Commit: {short_commit}")

    manifest = {
        "schemaVersion": 1,
        "appId": "language-learning",
        "name": "Language Learning",
        "platform": "android",
        "latestVersion": version_name,
        "versionCode": expected_version_code,
        "channel": "dev",
        "mandatory": False,
        "releaseDate": release_date,
        "packageName": expected_application_id,
        "package": {
            "url": f"{download_base}/{apk_name}",
            "sha256": digest,
            "size": size,
        },
        "notes": notes,
    }
    (out / "language-learning-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    build_info = {
        "versionName": version_name,
        "versionCode": expected_version_code,
        "applicationId": application_id,
        "commit": args.commit,
        "runId": args.run_id,
        "sha256": digest,
        "size": size,
    }
    (out / "build-info.json").write_text(
        json.dumps(build_info, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    release_notes = [
        f"# Language Learning Dev {version_name}",
        "",
        f"- Android version code: `{expected_version_code}`",
        f"- Commit: `{args.commit}`",
        f"- APK SHA-256: `{digest}`",
        f"- APK size: `{size}` bytes",
    ]
    if args.commit_subject:
        release_notes += ["", args.commit_subject]
    (out / "release-notes.md").write_text("\n".join(release_notes) + "\n", encoding="utf-8")

    print(f"Prepared {version_name} ({expected_version_code}), sha256={digest}, size={size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
