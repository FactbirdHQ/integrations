# Ignition → AWS IoT Core Integration

Connect Inductive Automation **Ignition** to **AWS IoT Core** using the
**Cirrus Link MQTT Transmission** module. Data is published over MQTT with
mutual-TLS authentication.

## Prerequisites

| Component | Purpose |
|---|---|
| Ignition Gateway (≥ 8.x) | SCADA platform |
| Cirrus Link MQTT Transmission module | MQTT client inside Ignition |
| AWS IoT Core endpoint + certificates | Cloud-side MQTT broker |
| Certificate bundle from Factbird | Device cert (`.pem.crt`), private key (`.pem.key`), Root CA |

---

## 1 — Upload Certificates

In the Ignition Gateway Web UI navigate to **Connections → MQTT Transmission
Settings → Servers → Certificates**.

Click **Create new Certificate** and upload each file:

- Device certificate (`.pem.crt`)
- Private key (`.pem.key`)
- Root CA certificate

## 2 — Configure the MQTT Server Connection

Under the **Servers** tab, create a new MQTT Server Setting:

| Field | Value |
|---|---|
| **URL** | `ssl://<endpoint>-ats.iot.<region>.amazonaws.com:8883` |
| **Server Set** | Select OPC connection |
| **TLS CA Certificate File** | AWS Root CA |
| **Client Certificate File** | Uploaded device certificate |
| **Client Private Key File** | Uploaded private key |

### Advanced Settings

| Field | Value |
|---|---|
| **Client ID** | UUID provided by Factbird |
| **Subscribe To Legacy STATE Topic** | Unchecked |
| **Data Format Type** | Sparkplug B v1.0 JSON |

### RPC Client Connection

| Field | Value |
|---|---|
| **RPC Client ID** | UUID provided by Factbird |
| **CA Certificate File** | AWS Root CA |
| **Client Certificate File** | User certificate |
| **Client Private Key File** | Private key file |

Click **Create Server**.

## 3 — Server Set Settings

On the default server set, enable the following advanced settings:

| Field | Value |
|---|---|
| **RPC Client Enabled** | Yes |
| **Auto Reconnect RPC Client** | Yes |

---

## Data Paths

There are two independent paths for publishing data. Configure one or both
depending on your use case.

| Path | Trigger | Topic pattern | Script |
|---|---|---|---|
| **A — Input Data** | Timer (every 5 s) | `plc/input/{clientId}` | [`scripts/input_publisher.py`](scripts/input_publisher.py) |
| **B — Event Data** | Tag value change | `plc/event/{clientId}` | [`scripts/event_publisher.py`](scripts/event_publisher.py) |

---

## Path A — Input Data (Time-Based)

### 4A — Create a Tag Provider

**Config → Tags → Realtime → Create new Realtime Tag Provider**

- Type: *Standard Tag Provider*
- Name: `Factbird Input Tags`

### 5A — Build the Tag Tree

MQTT Transmission requires at least three folder levels: **Group → Edge
Node → Device**.

```
Factbird_Input_Tags/
└── Plant1/                    ← Group ID
    └── IgnitionGateway/       ← Edge Node ID
        └── Boiler_PLC/        ← Device ID — tags live here
```

Inside the device folder, create **Reference Tags** pointing at existing
tags in the `default` provider (lightest-weight 1:1 mirror), or drag OPC
items directly from the OPC browser for a clean dedicated connection.

> **Tip:** If a tag is already used elsewhere in Ignition, prefer a
> Reference Tag to avoid duplicate OPC subscriptions.

### 6A — Deploy the Timer Script

1. Open **Ignition Designer → Scripting → Timer**.
2. Create a **New Timer Script**.
3. Paste the contents of [`scripts/input_publisher.py`](scripts/input_publisher.py).
4. Update `MQTT_SERVER_NAME`, `MQTT_TOPIC_TEMPLATE`, and `provider` to
   match your environment.
5. Set the timer delay to **5000 ms**.
6. Save the project.

### 7A — Create a Transmitter

**Config → MQTT Transmission → Settings → Transmitters → Create new Setting**

| Field | Value |
|---|---|
| **Name** | `AWS_Transmitter` |
| **Enabled** | **Unchecked** |
| **Tag Provider** | `AWS_Tags` |
| **Tag Path** | *(blank, or a subfolder path)* |
| **Server Set** | AWS IoT Core server set |
| **Group ID** | `Plant1` |
| **Edge Node ID** | `IgnitionGateway` |
| **Device ID** | `Boiler_PLC` |

> **⚠ Important:** All transmitters must be **disabled**.

### 8A — Check Logs

**Diagnostics → Logs** — look for errors or warnings.

---

## Path B — Event Data (Change-Based)

### 4B — Create a Tag Provider

Same procedure as 4A, but name it `Factbird Event Tags`.

### 5B — Build the Tag Tree

Same structure as 5A but under the `Factbird_Event_Tags` provider:

```
Factbird_Event_Tags/
└── Plant1/
    └── IgnitionGateway/
        └── Boiler_PLC/
```

### 6B — Deploy the Tag Change Script

1. Open **Ignition Designer → Scripting → Timer**.
2. Create a **New Timer Script**.
3. Paste the contents of [`scripts/event_publisher.py`](scripts/event_publisher.py).
4. Update `MQTT_SERVER_NAME` and `MQTT_TOPIC_TEMPLATE`.
5. Under **Tag Path**, drag in the highest node of your tag provider and
   append `/*` (e.g. `[Factbird Event]Plant1/*`).
6. Save the project.

### 7B — Create a Transmitter

Identical to Step 7A — configure for the event tag provider.

> **⚠ Important:** All transmitters must be **disabled**.

### 8B — Check Logs

**Diagnostics → Logs** — look for errors or warnings.

---

## MQTT Message Format

Both scripts publish JSON with the same schema:

```json
{
  "timestamp": 1700000000000,
  "values": [
    {
      "id": "TagName",
      "v": 42.5,
      "t": 1700000000000,
      "q": true
    }
  ]
}
```

| Field | Description |
|---|---|
| `id` | Tag name (input) or full tag path (event) |
| `v` | Value — `null` when quality is bad |
| `t` | Source timestamp (epoch ms) |
| `q` | Quality flag (`true` = good) |
