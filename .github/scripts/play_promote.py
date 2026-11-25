#!/usr/bin/env python3
"""Moves a version already uploaded to one Google Play track to another (beta -> production).

The same AAB is released: nothing is built or uploaded again. Release notes and name are copied
from the source track. Reads the service account JSON from PLAY_SERVICE_ACCOUNT_JSON.

  play_promote.py --package org.calamares.miga --version-code 253 --rollout 100
  play_promote.py --package org.calamares.miga --latest          # newest version in the beta

Prints "version_code=N" and "version_name=X" (for GITHUB_OUTPUT) when it succeeds.
"""
import argparse
import json
import os
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def call(session, method, url, body=None):
    response = session.request(method, url, json=body)
    if response.status_code >= 400:
        fail(f"Google Play answered {response.status_code} to {method} {url.split('/applications/')[-1]}: {response.text[:800]}")
    return response.json() if response.text else {}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--package", required=True)
    parser.add_argument("--from-track", default="alpha")
    parser.add_argument("--to-track", default="production")
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--latest", action="store_true", help="the newest version in --from-track")
    parser.add_argument("--rollout", type=float, default=100, help="percentage of users, 100 for everyone")
    args = parser.parse_args()
    if not args.latest and args.version_code is None:
        fail("Give --version-code or --latest.")
    if not 0 < args.rollout <= 100:
        fail("--rollout must be between 0 and 100.")

    info = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not info:
        fail("PLAY_SERVICE_ACCOUNT_JSON is not set.")
    credentials = service_account.Credentials.from_service_account_info(json.loads(info), scopes=[SCOPE])
    session = AuthorizedSession(credentials)
    base = f"{API}/{args.package}/edits"

    edit = call(session, "POST", base)["id"]
    source = call(session, "GET", f"{base}/{edit}/tracks/{args.from_track}")
    releases = source.get("releases", [])
    if args.latest:
        codes = [int(code) for release in releases for code in release.get("versionCodes", [])]
        if not codes:
            fail(f"There is no version in the {args.from_track} track.")
        version_code = max(codes)
    else:
        version_code = args.version_code
    release = next((r for r in releases if str(version_code) in r.get("versionCodes", [])), None)
    if release is None:
        fail(f"Version code {version_code} is not in the {args.from_track} track: upload it there first.")

    promoted = {"versionCodes": [str(version_code)], "releaseNotes": release.get("releaseNotes", [])}
    if release.get("name"):
        promoted["name"] = release["name"]
    if args.rollout >= 100:
        promoted["status"] = "completed"
    else:
        promoted["status"] = "inProgress"
        promoted["userFraction"] = round(args.rollout / 100, 4)
    call(session, "PUT", f"{base}/{edit}/tracks/{args.to_track}", {"track": args.to_track, "releases": [promoted]})
    call(session, "POST", f"{base}/{edit}:commit")

    print(f"Version code {version_code} ({release.get('name', '?')}) released on {args.to_track} for {args.rollout:g}% of users.", file=sys.stderr)
    print(f"version_code={version_code}")
    print(f"version_name={release.get('name', '')}")


if __name__ == "__main__":
    main()
