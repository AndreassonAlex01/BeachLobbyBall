#!/usr/bin/env python3
"""Deploys site/ to Firebase Hosting (https://beachlobbyball.web.app) through the Hosting REST API.

Does the same as `firebase deploy --only hosting`, without needing the Firebase CLI.
Sign in with `gcloud auth login` first, then run:

    python3 tools/deploy_site.py
"""
import gzip
import hashlib
import json
import pathlib
import subprocess
import urllib.request

PROJECT = "beachlobbyball-75c20"
API = "https://firebasehosting.googleapis.com/v1beta1"
ROOT = pathlib.Path(__file__).resolve().parent.parent


def call(method, url, token, body=None, data=None, content_type="application/json"):
    if body is not None:
        data = json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    request.add_header("x-goog-user-project", PROJECT)
    request.add_header("Content-Type", content_type)
    with urllib.request.urlopen(request) as response:
        text = response.read().decode()
    return json.loads(text) if text else {}


def main():
    config = json.loads((ROOT / "firebase.json").read_text())["hosting"]
    site = config["site"]
    public = ROOT / config["public"]
    token = subprocess.run(
        ["gcloud", "auth", "print-access-token"], check=True, capture_output=True, text=True
    ).stdout.strip()

    # Hosting stores files gzipped and addresses them by the SHA-256 of the gzipped bytes.
    files = {}
    for path in sorted(public.rglob("*")):
        if path.is_file():
            zipped = gzip.compress(path.read_bytes(), mtime=0)
            files["/" + path.relative_to(public).as_posix()] = (hashlib.sha256(zipped).hexdigest(), zipped)

    version = call(
        "POST", f"{API}/sites/{site}/versions", token,
        {"config": {"cleanUrls": config.get("cleanUrls", False)}},
    )["name"]
    populated = call(
        "POST", f"{API}/{version}:populateFiles", token,
        {"files": {path: digest for path, (digest, _) in files.items()}},
    )
    needed = set(populated.get("uploadRequiredHashes", []))
    for path, (digest, zipped) in files.items():
        if digest in needed:
            call("POST", f"{populated['uploadUrl']}/{digest}", token,
                 data=zipped, content_type="application/octet-stream")
            needed.discard(digest)
            print("uploaded", path)

    call("PATCH", f"{API}/{version}?update_mask=status", token, {"status": "FINALIZED"})
    call("POST", f"{API}/sites/{site}/releases?versionName={version}", token, {})
    print(f"Released {len(files)} files to https://{site}.web.app")


if __name__ == "__main__":
    main()
