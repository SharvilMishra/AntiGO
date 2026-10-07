"""Fail APK publishing when Firebase or Google sign-in config is incomplete."""

import json
import os
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "app" / "google-services.json"
KEYSTORE = ROOT / "release-signing" / "antigo-release.p12"
PACKAGE = "com.sharvil.antigo"


def fail(message: str) -> None:
    raise SystemExit(f"Firebase release config error: {message}")


if not CONFIG.is_file():
    fail("app/google-services.json is missing")
if not KEYSTORE.is_file():
    fail("release signing keystore is missing")
if not os.environ.get("STORE_PASSWORD") or not os.environ.get("KEY_ALIAS"):
    fail("release signing credentials are incomplete")

try:
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    fail(f"google-services.json is unreadable or invalid JSON ({error})")

clients = [
    client
    for client in config.get("client", [])
    if client.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE
]
if not clients:
    fail(f"no Firebase Android client is registered for {PACKAGE}")

android_hashes = set()
has_web_client = False
for client in clients:
    for oauth in client.get("oauth_client", []):
        if oauth.get("client_type") == 3:
            has_web_client = True
        if oauth.get("client_type") == 1:
            android_info = oauth.get("android_info", {})
            if android_info.get("package_name") == PACKAGE and android_info.get("certificate_hash"):
                android_hashes.add(re.sub(r"[^0-9a-f]", "", android_info["certificate_hash"].lower()))

if not has_web_client:
    fail("Google sign-in web OAuth client (client_type 3) is missing")

try:
    result = subprocess.run(
        [
            "keytool", "-list", "-v", "-keystore", str(KEYSTORE), "-storetype", "PKCS12",
            "-storepass:env", "STORE_PASSWORD", "-alias", os.environ["KEY_ALIAS"],
        ],
        check=True,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
    )
except (OSError, subprocess.CalledProcessError) as error:
    fail(f"could not inspect release signing certificate ({error})")

match = re.search(r"SHA1:\s*([0-9A-F:]+)", result.stdout, re.IGNORECASE)
if not match:
    fail("release signing certificate SHA-1 fingerprint was not reported by keytool")
release_hash = re.sub(r"[^0-9a-f]", "", match.group(1).lower())
if release_hash not in android_hashes:
    fail(
        "the release signing certificate is not registered for Google sign-in; "
        "add its SHA-1 fingerprint to the Firebase Android app and refresh "
        "FIREBASE_CONFIG_BASE64"
    )

print("Firebase package, web OAuth client, and release signing fingerprint verified.")
