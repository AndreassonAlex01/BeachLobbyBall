#!/usr/bin/env python3
"""Updates the Google Play listing and releases through the Play Developer API.

It acts as the play-publisher service account in the Firebase project, which needs to be
invited in Play Console (Users and permissions). Sign in with `gcloud auth login` as someone
allowed to impersonate it, then run one of:

    python3 tools/play_store.py listing --screenshots DIR   # text, contact details, icon, graphics, screenshots
    python3 tools/play_store.py release --track internal --aab app-release.aab
    python3 tools/play_store.py testers --track alpha --group testers@googlegroups.com

The listing text lives in store/listing/<language>/. DIR holds the screenshots in en/ and sv/
folders, as the store-screenshots branch has them.
"""
import argparse
import json
import pathlib
import subprocess
import urllib.error
import urllib.request

PACKAGE = "se.beachlobbyball.app"
PROJECT = "beachlobbyball-75c20"
SERVICE_ACCOUNT = f"play-publisher@{PROJECT}.iam.gserviceaccount.com"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"
ROOT = pathlib.Path(__file__).resolve().parent.parent

# Play language code -> screenshot folder.
LANGUAGES = {"en-US": "en", "sv-SE": "sv"}
CONTACT_WEBSITE = "https://beachlobbyball.web.app"


def play_token():
    """A short-lived token for the service account, made with the signed-in gcloud user."""
    user_token = subprocess.run(
        ["gcloud", "auth", "print-access-token"], check=True, capture_output=True, text=True
    ).stdout.strip()
    response = call(
        "POST",
        f"https://iamcredentials.googleapis.com/v1/projects/-/serviceAccounts/{SERVICE_ACCOUNT}:generateAccessToken",
        user_token,
        {"scope": ["https://www.googleapis.com/auth/androidpublisher"], "lifetime": "3600s"},
    )
    return response["accessToken"]


def call(method, url, token, body=None, data=None, content_type="application/json"):
    if body is not None:
        data = json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Authorization", f"Bearer {token}")
    if data is not None:
        request.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(request) as response:
            text = response.read().decode()
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{method} {url} failed: {e.code} {e.read().decode()}")
    return json.loads(text) if text else {}


class Edit:
    """A Play edit: changes are collected and go live together on commit."""

    def __init__(self, token):
        self.token = token
        self.id = call("POST", f"{API}/edits", token, {})["id"]
        self.url = f"{API}/edits/{self.id}"

    def put(self, path, body):
        return call("PUT", f"{self.url}/{path}", self.token, body)

    def replace_images(self, language, image_type, files):
        call("DELETE", f"{self.url}/listings/{language}/{image_type}", self.token)
        for file in files:
            call("POST", f"{UPLOAD}/edits/{self.id}/listings/{language}/{image_type}?uploadType=media",
                 self.token, data=file.read_bytes(), content_type="image/png")
            print(f"  {language} {image_type}: {file.name}")

    def upload_bundle(self, aab):
        return call("POST", f"{UPLOAD}/edits/{self.id}/bundles?uploadType=media",
                    self.token, data=aab.read_bytes(), content_type="application/octet-stream")

    def commit(self):
        call("POST", f"{self.url}:commit", self.token, {})


def listing(args):
    contact_file = ROOT / "store" / "contact-email.txt"
    if not contact_file.exists():
        raise SystemExit("Put the listing's contact email in store/contact-email.txt first.")
    contact_email = contact_file.read_text().strip()
    edit = Edit(play_token())
    edit.put("details", {
        "defaultLanguage": "en-US",
        "contactEmail": contact_email,
        "contactWebsite": CONTACT_WEBSITE,
    })
    for language, folder in LANGUAGES.items():
        texts = ROOT / "store" / "listing" / language
        edit.put(f"listings/{language}", {
            "language": language,
            "title": (texts / "title.txt").read_text().strip(),
            "shortDescription": (texts / "short_description.txt").read_text().strip(),
            "fullDescription": (texts / "full_description.txt").read_text().strip(),
        })
        print(f"  {language} text")
        edit.replace_images(language, "icon", [ROOT / "store" / "icon-512.png"])
        edit.replace_images(language, "featureGraphic", [ROOT / "store" / "feature-graphic.png"])
        shots = sorted((pathlib.Path(args.screenshots) / folder).glob("*.png"))
        if not shots:
            raise SystemExit(f"No screenshots in {args.screenshots}/{folder}")
        edit.replace_images(language, "phoneScreenshots", shots)
    edit.commit()
    print("Listing updated.")


def release(args):
    edit = Edit(play_token())
    bundle = edit.upload_bundle(pathlib.Path(args.aab))
    print(f"  uploaded version code {bundle['versionCode']}")
    edit.put(f"tracks/{args.track}", {
        "track": args.track,
        "releases": [{"versionCodes": [str(bundle["versionCode"])], "status": args.status}],
    })
    edit.commit()
    print(f"Released to {args.track} ({args.status}).")


def testers(args):
    edit = Edit(play_token())
    edit.put(f"testers/{args.track}", {"googleGroups": args.group})
    edit.commit()
    print(f"Testers on {args.track}: {', '.join(args.group)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(required=True)

    p = commands.add_parser("listing", help="update the store listing")
    p.add_argument("--screenshots", required=True, help="folder with en/ and sv/ screenshots")
    p.set_defaults(run=listing)

    p = commands.add_parser("release", help="upload an .aab to a track")
    p.add_argument("--track", required=True, help="internal, alpha (closed testing), beta or production")
    p.add_argument("--aab", required=True)
    p.add_argument("--status", default="completed", help="completed, or draft to review it in Play Console first")
    p.set_defaults(run=release)

    p = commands.add_parser("testers", help="set the Google Groups that can test a track")
    p.add_argument("--track", required=True)
    p.add_argument("--group", required=True, action="append", help="a Google Group email; repeat for more")
    p.set_defaults(run=testers)

    args = parser.parse_args()
    args.run(args)


if __name__ == "__main__":
    main()
