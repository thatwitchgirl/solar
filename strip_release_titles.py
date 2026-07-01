#!/usr/bin/env python3
"""Remove redundant title headers from all existing GitHub releases."""
import json
import urllib.request
import sys

if len(sys.argv) < 2:
    print("Usage: python strip_release_titles.py <github_pat>")
    sys.exit(1)

TOKEN = sys.argv[1]
REPO = "thesolarproject/solar"
API = f"https://api.github.com/repos/{REPO}/releases"
HEADERS = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github+json",
    "Content-Type": "application/json",
}

def get_all_releases():
    releases = []
    page = 1
    while True:
        url = f"{API}?per_page=100&page={page}"
        req = urllib.request.Request(url, headers=HEADERS)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode())
            if not data:
                break
            releases.extend(data)
            page += 1
    return releases

def update_release(release_id, new_body):
    url = f"{API}/{release_id}"
    data = json.dumps({"body": new_body}).encode()
    req = urllib.request.Request(url, data=data, headers=HEADERS, method="PATCH")
    with urllib.request.urlopen(req) as resp:
        return resp.status

releases = get_all_releases()
print(f"Found {len(releases)} releases")

updated = 0
skipped = 0
for r in releases:
    tag = r["tag_name"]
    body = r.get("body") or ""

    lines = body.split("\n")
    new_lines = []
    changed = False

    for line in lines:
        if line.startswith("# Solar ") and ("— main" in line or "— nightly" in line):
            changed = True
            # Skip this line
            continue
        new_lines.append(line)

    if changed:
        # Strip leading blank lines
        while new_lines and new_lines[0].strip() == "":
            new_lines.pop(0)

        new_body = "\n".join(new_lines)
        status = update_release(r["id"], new_body)
        print(f"  UPDATED {tag} — HTTP {status}")
        updated += 1
    else:
        print(f"  SKIP {tag} — no title found")
        skipped += 1

print(f"\nDone: {updated} updated, {skipped} skipped")
