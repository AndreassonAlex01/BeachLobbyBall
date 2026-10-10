#!/usr/bin/env python3
"""Tests firestore.rules with Firebase's rules test API (nothing is deployed).

Usage: python3 tools/test_firestore_rules.py   (needs `gcloud auth login` with access to the project)
"""
import json, os, subprocess, sys, urllib.request

PROJECT = "beachlobbyball-75c20"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = open(os.path.join(ROOT, "firestore.rules")).read()
D = "/databases/(default)/documents"
base = {"hostId": "h", "hostName": "Host", "isOpen": True, "maxPlayers": 3, "visibility": "Public",
        "playerIds": ["h", "p1"], "playerNames": {"h": "Host", "p1": "P1"}}
def lobby(**kw):
    d = dict(base); d.update(kw); return d
def case(name, expect, uid, path, method, resource=None, new=None, mocks=None):
    req = {"path": D + path, "method": method}
    if uid: req["auth"] = {"uid": uid}
    if new is not None: req["resource"] = {"data": new}
    tc = {"expectation": expect, "request": req}
    if resource is not None: tc["resource"] = {"data": resource}
    if mocks: tc["functionMocks"] = mocks
    return name, tc
get_lobby = [{"function": "get", "args": [{"anyValue": {}}], "result": {"value": {"data": base}}}]
cases = [
    case("create as host", "ALLOW", "h", "/lobbies/x", "create", new=lobby(playerIds=["h"])),
    case("create for someone else", "DENY", "h", "/lobbies/x", "create", new=lobby(hostId="z", playerIds=["h"])),
    case("signed-out read", "DENY", None, "/lobbies/l1", "get", resource=base),
    case("join", "ALLOW", "p2", "/lobbies/l1", "update", resource=base,
         new=lobby(playerIds=["h", "p1", "p2"], playerNames={"h": "Host", "p1": "P1", "p2": "P2"})),
    case("join when full", "DENY", "p3", "/lobbies/l1", "update", resource=lobby(playerIds=["h", "p1", "p2"]),
         new=lobby(playerIds=["h", "p1", "p2", "p3"])),
    case("join closed", "DENY", "p2", "/lobbies/l1", "update", resource=lobby(isOpen=False),
         new=lobby(isOpen=False, playerIds=["h", "p1", "p2"])),
    case("add someone else", "DENY", "p2", "/lobbies/l1", "update", resource=base,
         new=lobby(playerIds=["h", "p1", "p9"])),
    case("player edits lobby", "DENY", "p1", "/lobbies/l1", "update", resource=base, new=lobby(isOpen=False)),
    case("leave", "ALLOW", "p1", "/lobbies/l1", "update", resource=base,
         new=lobby(playerIds=["h"], playerNames={"h": "Host"})),
    case("kick other as player", "DENY", "p1", "/lobbies/l1", "update", resource=lobby(playerIds=["h", "p1", "p2"]),
         new=lobby(playerIds=["h", "p1"])),
    case("host closes", "ALLOW", "h", "/lobbies/l1", "update", resource=base, new=lobby(isOpen=False)),
    case("host deletes", "ALLOW", "h", "/lobbies/l1", "delete", resource=base),
    case("player deletes", "DENY", "p1", "/lobbies/l1", "delete", resource=base),
    case("player sends message", "ALLOW", "p1", "/lobbies/l1/messages/m", "create",
         new={"senderId": "p1", "senderName": "P1", "text": "hi", "sentAtMillis": 1}, mocks=get_lobby),
    case("outsider sends message", "DENY", "p9", "/lobbies/l1/messages/m", "create",
         new={"senderId": "p9", "senderName": "X", "text": "hi", "sentAtMillis": 1}, mocks=get_lobby),
    case("spoofed sender", "DENY", "p1", "/lobbies/l1/messages/m", "create",
         new={"senderId": "h", "senderName": "Host", "text": "hi", "sentAtMillis": 1}, mocks=get_lobby),
    case("outsider reads chat", "DENY", "p9", "/lobbies/l1/messages/m", "get",
         resource={"senderId": "p1", "text": "hi"}, mocks=get_lobby),
    case("host deletes message", "ALLOW", "h", "/lobbies/l1/messages/m", "delete",
         resource={"senderId": "p1", "text": "hi"}, mocks=get_lobby),
    case("player deletes others message", "DENY", "p1", "/lobbies/l1/messages/m", "delete",
         resource={"senderId": "h", "text": "hi"}, mocks=get_lobby),
    case("report", "ALLOW", "p1", "/reports/r", "create", new={"reporterId": "p1"}),
    case("read reports", "DENY", "p1", "/reports/r", "get", resource={"reporterId": "p1"}),
    case("own block list", "ALLOW", "p1", "/users/p1/blocked/p2", "create", new={"name": "P2"}),
    case("others block list", "DENY", "p1", "/users/p2/blocked/p1", "get", resource={"name": "P1"}),
]

env = {k: v for k, v in os.environ.items() if k != "CLOUDSDK_AUTH_ACCESS_TOKEN"}
token = subprocess.run(["gcloud", "auth", "print-access-token"], capture_output=True, text=True, env=env, check=True).stdout.strip()
body = json.dumps({"source": {"files": [{"name": "firestore.rules", "content": src}]},
                   "testSuite": {"testCases": [c[1] for c in cases]}}).encode()
req = urllib.request.Request(
    f"https://firebaserules.googleapis.com/v1/projects/{PROJECT}:test", data=body, method="POST",
    headers={"Authorization": f"Bearer {token}", "x-goog-user-project": PROJECT, "Content-Type": "application/json"})
results = json.load(urllib.request.urlopen(req))["testResults"]
failed = 0
for (name, _), result in zip(cases, results):
    ok = result["state"] == "SUCCESS"
    failed += not ok
    print("PASS" if ok else "FAIL", name, "" if ok else result.get("debugMessages", ""))
print(f"{len(cases) - failed}/{len(cases)} passed")
sys.exit(1 if failed else 0)
