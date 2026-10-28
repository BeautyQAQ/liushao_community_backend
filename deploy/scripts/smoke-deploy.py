#!/usr/bin/env python3
"""Read-only HTTP checks for both deployed frontends and their API proxies."""
import argparse
import json
import re
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import Request, urlopen


def fetch(base, path, status=200, media=None, headers=None):
    try:
        response = urlopen(Request(base.rstrip("/") + path, headers=headers or {}), timeout=15)
    except HTTPError as error:
        response = error
    with response:
        assert response.status == status, f"{path}: expected {status}, got {response.status}"
        if media:
            assert media in response.headers.get("Content-Type", ""), f"{path}: wrong content type"
        return response.read().decode()


def check_frontend(base, deep_path):
    fetch(base, "/healthz", media="text/plain")
    html = fetch(base, deep_path, media="text/html")
    assets = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', html)
    assert assets, f"{base}: built assets not found in SPA document"
    for asset in assets:
        fetch(base, asset)
    fetch(base, "/assets/missing-deploy-probe.js", status=404)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--community", default="http://127.0.0.1:8088")
    parser.add_argument("--admin", default="http://127.0.0.1:8089")
    args = parser.parse_args()
    check_frontend(args.community, "/search?query=deploy-probe")
    check_frontend(args.admin, "/sys/users")
    for base in (args.community, args.admin):
        health = json.loads(fetch(base, "/api/actuator/health/readiness", media="application/"))
        assert health == {"status": "UP"}, f"{base}: dependencies are not ready or details are exposed"
        address = urlsplit(base)
        headers = {"Origin": f"{address.scheme}://{address.netloc}",
                   "X-Forwarded-Port": "1", "X-Forwarded-Host": "invalid.example"}
        fetch(base, "/api/actuator/health/readiness", headers=headers)
        if base == args.community:
            fetch(base, "/api/post/list?pageNo=1&size=1", headers=headers)
        else:
            fetch(base, "/api/sys/userInfo", status=401, headers=headers)
    posts = json.loads(fetch(args.community, "/api/post/list?pageNo=1&size=1", media="application/json"))
    assert posts["code"] == 200, "Community list failed"
    fetch(args.community, "/api/ums/user/info", status=401, media="application/json")
    fetch(args.admin, "/api/sys/userInfo", status=401, media="application/json")
    print("PASS: both frontends, deep links, assets, dependencies, same-origin proxies and authentication boundaries")


if __name__ == "__main__":
    main()
