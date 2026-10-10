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
def ACCOUNT(uid, email=None):
    token = {"firebase": {"sign_in_provider": "password"}}
    if email: token["email"] = email
    return {"uid": uid, "token": token}
def GUEST(uid):
    return {"uid": uid, "token": {"firebase": {"sign_in_provider": "anonymous"}}}
def EXISTS(result):
    return [{"function": "exists", "args": [{"anyValue": {}}], "result": {"value": result}}]
def case(name, expect, uid, path, method, resource=None, new=None, mocks=None):
    req = {"path": D + path, "method": method}
    if isinstance(uid, dict): req["auth"] = uid
    elif uid: req["auth"] = {"uid": uid, "token": {"firebase": {"sign_in_provider": "password"}}}
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

    # Profiles and email lookup. Accounts sign in with a password; guests are anonymous.
    case("account saves own profile", "ALLOW", ACCOUNT("a"), "/profiles/a", "create",
         new={"name": "Alex", "nameLower": "alex"}),
    case("profile with wrong nameLower", "DENY", ACCOUNT("a"), "/profiles/a", "create",
         new={"name": "Alex", "nameLower": "x"}),
    case("guest creates profile", "DENY", GUEST("g"), "/profiles/g", "create",
         new={"name": "Gus", "nameLower": "gus"}),
    case("edit someone else's profile", "DENY", ACCOUNT("a"), "/profiles/b", "update",
         resource={"name": "B", "nameLower": "b"}, new={"name": "X", "nameLower": "x"}),
    case("search profiles", "ALLOW", ACCOUNT("a"), "/profiles/b", "list", resource={"name": "B", "nameLower": "b"}),
    case("claim own email", "ALLOW", ACCOUNT("a", "Alex@Example.se"), "/emailIndex/alex@example.se", "create",
         new={"uid": "a"}),
    case("claim someone else's email", "DENY", ACCOUNT("a", "alex@example.se"), "/emailIndex/sam@example.se",
         "create", new={"uid": "a"}),
    case("look up exact email", "ALLOW", ACCOUNT("b"), "/emailIndex/alex@example.se", "get", resource={"uid": "a"}),
    case("list all emails", "DENY", ACCOUNT("b"), "/emailIndex/alex@example.se", "list", resource={"uid": "a"}),

    # Friend requests and friend lists.
    case("send request", "ALLOW", ACCOUNT("a"), "/friendRequests/a_b", "create",
         new={"fromId": "a", "toId": "b"}),
    case("send request as someone else", "DENY", ACCOUNT("a"), "/friendRequests/c_b", "create",
         new={"fromId": "c", "toId": "b"}),
    case("guest sends request", "DENY", GUEST("g"), "/friendRequests/g_b", "create",
         new={"fromId": "g", "toId": "b"}),
    case("request to self", "DENY", ACCOUNT("a"), "/friendRequests/a_a", "create",
         new={"fromId": "a", "toId": "a"}),
    case("receiver reads request", "ALLOW", ACCOUNT("b"), "/friendRequests/a_b", "get",
         resource={"fromId": "a", "toId": "b"}),
    case("stranger reads request", "DENY", ACCOUNT("c"), "/friendRequests/a_b", "get",
         resource={"fromId": "a", "toId": "b"}),
    case("accepter adds sender to own list", "ALLOW", ACCOUNT("b"), "/users/b/friends/a", "create",
         new={"name": "A", "tags": []}, mocks=EXISTS(True)),
    case("accepter adds self to sender's list", "ALLOW", ACCOUNT("b"), "/users/a/friends/b", "create",
         new={"name": "B", "tags": []}, mocks=EXISTS(True)),
    case("accepter tags themselves in sender's list", "DENY", ACCOUNT("b"), "/users/a/friends/b", "create",
         new={"name": "B", "tags": ["Best"]}, mocks=EXISTS(True)),
    case("befriend without a request", "DENY", ACCOUNT("b"), "/users/b/friends/a", "create",
         new={"name": "A", "tags": []}, mocks=EXISTS(False)),
    case("write into a stranger's list", "DENY", ACCOUNT("c"), "/users/a/friends/b", "create",
         new={"name": "B", "tags": []}, mocks=EXISTS(True)),
    case("read own friends", "ALLOW", ACCOUNT("a"), "/users/a/friends/b", "get", resource={"name": "B", "tags": []}),
    case("read someone's tags", "DENY", ACCOUNT("b"), "/users/a/friends/b", "get",
         resource={"name": "B", "tags": ["Mixed"]}),
    case("friend edits my tags", "DENY", ACCOUNT("b"), "/users/a/friends/b", "update",
         resource={"name": "B", "tags": []}, new={"name": "B", "tags": ["x"]}),
    case("unfriend from either side", "ALLOW", ACCOUNT("b"), "/users/a/friends/b", "delete",
         resource={"name": "B", "tags": []}),

    # Waitlists and handing over the host role.
    case("join waitlist when full", "ALLOW", "p3", "/lobbies/l1", "update", resource=lobby(playerIds=["h", "p1", "p2"]),
         new=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3"])),
    case("join waitlist with room left", "DENY", "p2", "/lobbies/l1", "update", resource=base,
         new=lobby(waitlistIds=["p2"])),
    case("skip the line", "DENY", "p4", "/lobbies/l1", "update", resource=lobby(waitlistIds=["p3"]),
         new=lobby(playerIds=["h", "p1", "p4"], waitlistIds=["p3"])),
    case("first in line takes a free spot", "ALLOW", "p3", "/lobbies/l1", "update", resource=lobby(waitlistIds=["p3"]),
         new=lobby(playerIds=["h", "p1", "p3"], waitlistIds=[])),
    case("leave and let the first in line in", "ALLOW", "p1", "/lobbies/l1", "update",
         resource=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3", "p4"]),
         new=lobby(playerIds=["h", "p2", "p3"], waitlistIds=["p4"])),
    case("leave and let the wrong one in", "DENY", "p1", "/lobbies/l1", "update",
         resource=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3", "p4"]),
         new=lobby(playerIds=["h", "p2", "p4"], waitlistIds=["p3"])),
    case("leave waitlist", "ALLOW", "p4", "/lobbies/l1", "update",
         resource=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3", "p4"]),
         new=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3"])),
    case("remove someone else from waitlist", "DENY", "p4", "/lobbies/l1", "update",
         resource=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p3", "p4"]),
         new=lobby(playerIds=["h", "p1", "p2"], waitlistIds=["p4"])),
    case("hand over host", "ALLOW", "h", "/lobbies/l1", "update", resource=base,
         new=lobby(hostId="p1", hostName="P1", playerIds=["p1", "h"])),
    case("hand host to an outsider", "DENY", "h", "/lobbies/l1", "update", resource=base, new=lobby(hostId="z")),
    case("player makes self host", "DENY", "p1", "/lobbies/l1", "update", resource=base, new=lobby(hostId="p1")),

    # Private lobbies, invites and join codes.
    case("list public lobbies", "ALLOW", GUEST("g"), "/lobbies/l1", "list", resource=base),
    case("list a stranger's private lobby", "DENY", ACCOUNT("x"), "/lobbies/l1", "list",
         resource=lobby(visibility="Private")),
    case("list own private lobby", "ALLOW", ACCOUNT("p1"), "/lobbies/l1", "list",
         resource=lobby(visibility="Private")),
    case("open private lobby by id", "ALLOW", ACCOUNT("x"), "/lobbies/l1", "get",
         resource=lobby(visibility="Private")),
    case("join private with invite", "ALLOW", "p2", "/lobbies/l1", "update", resource=lobby(visibility="Private"),
         new=lobby(visibility="Private", playerIds=["h", "p1", "p2"], playerNames={"h": "Host", "p1": "P1", "p2": "P2"}),
         mocks=EXISTS(True)),
    case("join private without invite", "DENY", "p2", "/lobbies/l1", "update", resource=lobby(visibility="Private"),
         new=lobby(visibility="Private", playerIds=["h", "p1", "p2"], playerNames={"h": "Host", "p1": "P1", "p2": "P2"}),
         mocks=EXISTS(False)),
    case("invite a friend", "ALLOW", ACCOUNT("p1"), "/invites/l1_b", "create",
         new={"lobbyId": "l1", "fromId": "p1", "toId": "b", "code": None}, mocks=get_lobby + EXISTS(True)),
    case("invite a non-friend", "DENY", ACCOUNT("p1"), "/invites/l1_b", "create",
         new={"lobbyId": "l1", "fromId": "p1", "toId": "b", "code": None}, mocks=get_lobby + EXISTS(False)),
    case("invite to a lobby you're not in", "DENY", ACCOUNT("p9"), "/invites/l1_b", "create",
         new={"lobbyId": "l1", "fromId": "p9", "toId": "b", "code": None}, mocks=get_lobby + EXISTS(True)),
    case("guest invites", "DENY", GUEST("p1"), "/invites/l1_b", "create",
         new={"lobbyId": "l1", "fromId": "p1", "toId": "b", "code": None}, mocks=get_lobby + EXISTS(True)),
    case("invite stored under the wrong id", "DENY", ACCOUNT("p1"), "/invites/l2_b", "create",
         new={"lobbyId": "l1", "fromId": "p1", "toId": "b", "code": None}, mocks=get_lobby + EXISTS(True)),
    case("guest uses join code", "ALLOW", GUEST("g"), "/invites/l1_g", "create",
         new={"lobbyId": "l1", "fromId": "g", "toId": "g", "code": "K7PQ2M"},
         mocks=[{"function": "get", "args": [{"anyValue": {}}], "result": {"value": {"data": {"lobbyId": "l1"}}}}]),
    case("code for another lobby", "DENY", GUEST("g"), "/invites/l1_g", "create",
         new={"lobbyId": "l1", "fromId": "g", "toId": "g", "code": "K7PQ2M"},
         mocks=[{"function": "get", "args": [{"anyValue": {}}], "result": {"value": {"data": {"lobbyId": "l2"}}}}]),
    case("invitee reads invite", "ALLOW", ACCOUNT("b"), "/invites/l1_b", "get",
         resource={"lobbyId": "l1", "fromId": "p1", "toId": "b"}),
    case("stranger reads invite", "DENY", ACCOUNT("x"), "/invites/l1_b", "get",
         resource={"lobbyId": "l1", "fromId": "p1", "toId": "b"}),
    case("invitee declines", "ALLOW", ACCOUNT("b"), "/invites/l1_b", "delete",
         resource={"lobbyId": "l1", "fromId": "p1", "toId": "b"}),
    case("host saves join code", "ALLOW", ACCOUNT("h"), "/lobbyCodes/K7PQ2M", "create", new={"lobbyId": "l1"},
         mocks=[{"function": "getAfter", "args": [{"anyValue": {}}],
                 "result": {"value": {"data": lobby(visibility="Private", joinCode="K7PQ2M")}}}]),
    case("player saves join code", "DENY", ACCOUNT("p1"), "/lobbyCodes/K7PQ2M", "create", new={"lobbyId": "l1"},
         mocks=[{"function": "getAfter", "args": [{"anyValue": {}}],
                 "result": {"value": {"data": lobby(visibility="Private", joinCode="K7PQ2M")}}}]),
    case("look up a join code", "ALLOW", GUEST("g"), "/lobbyCodes/K7PQ2M", "get", resource={"lobbyId": "l1"}),
    case("list join codes", "DENY", GUEST("g"), "/lobbyCodes/K7PQ2M", "list", resource={"lobbyId": "l1"}),

    # Account deletion.
    case("find own messages everywhere", "ALLOW", ACCOUNT("p9"), "/lobbies/l1/messages/m", "list",
         resource={"senderId": "p9", "text": "hi"}, mocks=get_lobby),
    case("find others' messages everywhere", "DENY", ACCOUNT("p9"), "/lobbies/l1/messages/m", "list",
         resource={"senderId": "p1", "text": "hi"}, mocks=get_lobby),
    case("delete own message after leaving", "ALLOW", ACCOUNT("p9"), "/lobbies/l1/messages/m", "delete",
         resource={"senderId": "p9", "text": "hi"}, mocks=get_lobby),
    case("delete own profile", "ALLOW", ACCOUNT("a"), "/profiles/a", "delete", resource={"name": "A", "nameLower": "a"}),
    case("delete own email entry", "ALLOW", ACCOUNT("a"), "/emailIndex/a@x.se", "delete", resource={"uid": "a"}),
    case("delete someone's email entry", "DENY", ACCOUNT("b"), "/emailIndex/a@x.se", "delete", resource={"uid": "a"}),
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
