# Supervisor App — Backend API Requirements

**For:** backend team
**From:** XP Jobs Supervisor (Android)
**Status:** the app is built and running against a stub. These endpoints do not
exist yet; everything below is what the app already sends and expects.

## How this is wired today

The app talks to `SupervisorApi`, bound in `di/SupervisorModule.kt` to
`SupervisorApiStub`, which returns exactly the shapes documented here after a
600 ms delay. When these endpoints ship, that one Hilt binding is swapped for
the Retrofit implementation and **no screen changes**.

So: match these shapes and the app works on the day you deploy.

- **Base URL:** `https://betacapture.xphi.in/`
- **Auth:** `Authorization: Bearer <access_token>` — the same token
  `POST /api/v1/recorder/otp/verify` already issues.
- All responses `application/json`.

---

## Endpoints already in use (no work needed)

The supervisor app reuses these as-is. Listed so nobody removes them:

| Endpoint | Used for |
|---|---|
| `POST /api/v1/recorder/otp/send` | Sign in, step 1 |
| `POST /api/v1/recorder/otp/verify` | Sign in, step 2 |
| `GET /api/v1/recorder/dashboard` | Home totals |
| `GET /api/v1/recorder/tasks` | Tasks tab |
| `GET /api/v1/recorder/tags?filter=` | Task work-type filter |
| `GET /api/v1/recorder/sessions?tab=uploaded` | Sessions tab |

---

# 1. `GET /api/v1/supervisor/staff`

Recording staff this supervisor is responsible for. Drives the **People** tab.

```bash
curl -X GET "https://betacapture.xphi.in/api/v1/supervisor/staff?status=active&q=abhay" \
  -H "Authorization: Bearer <token>"
```

### Query parameters

| Param | Type | Required | Notes |
|---|---|---|---|
| `status` | string | no | `active` \| `idle` \| `offline`. Omitted = all. |
| `q` | string | no | Case-insensitive name search. |

### 200

```json
{
  "ok": true,
  "total": 2,
  "staff": [
    {
      "id": "stf_001",
      "full_name": "Abhay Jadon",
      "phone_number": "9598888369",
      "status": "active",
      "assigned_device_id": "CC843",
      "assigned_device_name": "CC843",
      "current_task_title": "Cleaning kitchen slab / counter",
      "sessions_today": 4,
      "sessions_total": 189,
      "last_seen_unix": 1787000000
    }
  ]
}
```

### Field notes

| Field | Notes |
|---|---|
| `status` | **Server-derived, not stored.** See the definition below — the app does not compute it. |
| `assigned_device_id` | `null` when the person holds no cap. The app renders "No device assigned"; do not send `""`. |
| `current_task_title` | `null` when not recording. |
| `sessions_today` | Reset on the supervisor's local day boundary, not UTC. |
| `last_seen_unix` | Seconds. |

### Specific need — define `status` server-side

The app must not infer this, because it only sees caps within Bluetooth range
and would mark everyone else offline. Suggested rule:

- `active` — a session started in the last 15 minutes, or one is running now
- `idle` — seen in the last 24 h but not currently recording
- `offline` — not seen in 24 h

Send the string; the app only renders it.

---

# 2. `GET /api/v1/supervisor/staff/{id}`

One person, for a detail screen.

```bash
curl -X GET "https://betacapture.xphi.in/api/v1/supervisor/staff/stf_001" \
  -H "Authorization: Bearer <token>"
```

**200** — a single `staff` object, identical shape to the array element above.
**404** — `{"ok": false, "error": "staff_not_found"}`

---

# 3. `GET /api/v1/supervisor/devices`

The registered fleet. Drives the **Devices** screen.

```bash
curl -X GET "https://betacapture.xphi.in/api/v1/supervisor/devices" \
  -H "Authorization: Bearer <token>"
```

### 200

```json
{
  "ok": true,
  "devices": [
    {
      "device_id": "CC843",
      "name": "CC843",
      "device_kit_id": "kit-ego-luckfox-v3",
      "firmware_version": "v2.1",
      "assigned_to_id": "stf_001",
      "assigned_to_name": "Abhay Jadon",
      "last_seen_unix": 1787000000,
      "last_session_id": "sess_1786789323121",
      "total_sessions": 189
    }
  ]
}
```

### Specific need — do NOT send live state

**No `recording`, `battery`, `storage_free`, or `temperature` fields.**

Those come from the cap itself over BLE, once a second. A server copy would be
minutes or hours stale, and the app would then have two disagreeing sources for
the same question. This endpoint answers only what the phone cannot know by
itself: **who owns the cap, who is holding it, and what the server last saw.**

`device_id` must match the id in the cap's QR sticker
(`cybercap://device/<device_id>`) and in its BLE heartbeat — that is the join
key between this list and the live fleet.

---

# 4. `PATCH /api/v1/supervisor/devices/{deviceId}/assignment`

Hand a cap to a person, or take it back.

```bash
curl -X PATCH "https://betacapture.xphi.in/api/v1/supervisor/devices/CC843/assignment" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"assigned_to_id": "stf_002"}'
```

Unassign by sending `null`:

```bash
-d '{"assigned_to_id": null}'
```

**200** — the updated `device` object (shape as in §3).

| Error | Response |
|---|---|
| 404 | `{"ok": false, "error": "device_not_found"}` |
| 404 | `{"ok": false, "error": "staff_not_found"}` |
| 409 | `{"ok": false, "error": "device_recording", "message": "..."}` |

### Specific need — refuse reassignment mid-recording

Return **409** if the cap has an open session. Moving a device between people
while it is capturing would split one recording across two operators in the
data, and nothing downstream could untangle it afterwards.

---

# 5. Sessions filtered by person — *extension to an existing endpoint*

The Sessions tab currently uses `GET /api/v1/recorder/sessions?tab=uploaded`,
which returns **the caller's own** sessions. A supervisor needs the team's.

```bash
curl -X GET "https://betacapture.xphi.in/api/v1/recorder/sessions?tab=uploaded&staff_id=stf_001" \
  -H "Authorization: Bearer <token>"
```

### Specific need

Add two optional query params, and one field to each row:

| Addition | Type | Notes |
|---|---|---|
| `staff_id` (query) | string | Filter to one person. Omitted = **everyone this supervisor manages**. |
| `device_id` (query) | string | Filter to one cap. |
| `operator_name` (row field) | string | Who recorded it. Currently absent, so the supervisor cannot tell whose session a row is. |

Scope must be enforced server-side from the bearer token — a supervisor must
only ever receive their own team's sessions.

Everything else about the response stays as it is; the app already parses it.

---

# 6. Notify on session upload — *already specified*

Not part of this app, but it belongs on the same backlog: the LubanCat and
LuckFox firmware now POST when a segment lands in S3.

- `POST /api/v1/device/segment`
- `POST /api/v1/device/session/complete`

Full request/response shapes were delivered separately. Auth is a per-device
key (`X-Device-Key`), **not** the operator's JWT — the caps record standalone
from a button with no phone attached, and uploads lag hours behind recording.

---

## Priority

| # | Endpoint | Priority | Blocks |
|---|---|---|---|
| 1 | `GET /supervisor/staff` | **High** | People tab is stubbed |
| 3 | `GET /supervisor/devices` | **High** | Devices screen is stubbed |
| 5 | `sessions?staff_id=` + `operator_name` | **High** | Sessions shows only the supervisor's own work |
| 4 | `PATCH /devices/{id}/assignment` | Medium | Assignment is read-only for now |
| 2 | `GET /supervisor/staff/{id}` | Low | No detail screen yet |

## Open questions for the backend team

1. **Is there a supervisor role?** The app signs in through
   `/api/v1/recorder/otp/*` and gets a recorder token. If supervisors need a
   distinct role or a different endpoint, say so — it is a one-line change here,
   but it must be decided before rollout.
2. **How is "the team" defined?** By contractor code, an explicit
   supervisor→staff mapping, or something else? It determines who §1 and §5
   return.
3. **Should a supervisor be able to start a recording on a cap assigned to
   someone else?** The app currently allows it, because the cap accepts a start
   from any bonded phone. If that should be restricted, it needs enforcing on
   the device, not in the app.
