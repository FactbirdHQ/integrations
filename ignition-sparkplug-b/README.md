# Ignition → AWS IoT Core Integration (Sparkplug B)

Connect Inductive Automation **Ignition** to **AWS IoT Core** using the
**Cirrus Link MQTT Transmission** module with native **Sparkplug B** encoding.

> **Note:** This is a **Sparkplug B** integration. The MQTT Transmission module
> handles topic structure, payload encoding (Protocol Buffers), and
> birth/death certificates automatically.
>
> For a non-Sparkplug B variant that publishes plain JSON, see
> [ignition/](../ignition/).

## Prerequisites

| Component | Purpose |
|---|---|
| Ignition Gateway (>= 8.x) | SCADA platform |
| Cirrus Link MQTT Transmission module | MQTT client inside Ignition with Sparkplug B support |
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
| **Data Format Type** | Sparkplug B v1.0 *(Protobuf)* or Sparkplug B v1.0 JSON |

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

## 4 — Create a Tag Provider

**Config → Tags → Realtime → Create new Realtime Tag Provider**

- Type: *Standard Tag Provider*
- Name: `Factbird Tags`

## 5 — Build the Tag Tree

MQTT Transmission requires at least three folder levels that map directly to
the Sparkplug B topic namespace: **Group → Edge Node → Device**.

```
Factbird_Tags/
└── Plant1/                    <-- Group ID
    └── IgnitionGateway/       <-- Edge Node ID
        └── Boiler_PLC/        <-- Device ID — tags live here
```

Inside the device folder, create **Reference Tags** pointing at existing
tags in the `default` provider (lightest-weight 1:1 mirror), or drag OPC
items directly from the OPC browser for a clean dedicated connection.

> **Tip:** If a tag is already used elsewhere in Ignition, prefer a
> Reference Tag to avoid duplicate OPC subscriptions.

The folder hierarchy determines the Sparkplug B topic structure. For the
example above, messages are published to:

```
spBv1.0/Plant1/DDATA/IgnitionGateway/Boiler_PLC
```

## 6 — Create a Transmitter

**Config → MQTT Transmission → Settings → Transmitters → Create new Setting**

| Field | Value |
|---|---|
| **Name** | `AWS_Transmitter` |
| **Enabled** | **Checked** |
| **Tag Provider** | `Factbird_Tags` |
| **Tag Path** | *(blank, or a subfolder path)* |
| **Server Set** | AWS IoT Core server set |
| **Group ID** | `Plant1` |
| **Edge Node ID** | `IgnitionGateway` |
| **Device ID** | `Boiler_PLC` |

### Transmitter Advanced Settings

| Field | Value |
|---|---|
| **Publish Mode** | Publish by Tag Event |
| **Tag Quality Changes Only** | Unchecked |

> **Publish Mode options:**
>
> - **Publish by Tag Event** — publishes a `DDATA` message each time a tag
>   value changes. Best for event-driven data.
> - **Publish by Trigger Tag** — publishes all tags when a designated trigger
>   tag transitions. Useful for batch or periodic snapshots.

## 7 — Deploy the Refresh Timer Script (Temporary Workaround)

> **Why this is needed:** Sparkplug B only publishes **DDATA** messages when
> a tag value *changes*. Factbird requires **periodic data** — even when
> values remain unchanged — to accurately measure production speed and
> derive downtime. Without a regular heartbeat of data, gaps in reporting
> would be misinterpreted as downtime.
>
> As a temporary workaround, a timer script periodically triggers a
> **Transmission Refresh**, which restarts the module and forces a new
> **DBIRTH** message containing the full metric catalog with current values.

1. Open **Ignition Designer → Scripting → Timer**.
2. Create a **New Timer Script**.
3. Paste the contents of [`scripts/refresh_transmission.py`](scripts/refresh_transmission.py).
4. Set the **Delay** to `20000` ms (20 seconds).
5. Set **Delay Type** to **Fixed Rate**.
6. Save the project.

The script writes `True` to the built-in
`[MQTT Transmission]Transmission Control/Refresh` tag, which triggers the
module to restart and publish fresh birth certificates.

## 8 — Verify the Connection

1. Open **Status → Connections → MQTT Transmission** and confirm the server
   shows **Connected**.
2. The transmitter will automatically publish:
   - An **NBIRTH** (Node Birth) message on connection, listing all metrics.
   - A **DBIRTH** (Device Birth) message for each configured device.
   - **NDATA** / **DDATA** messages as tag values change.
3. Check **Diagnostics → Logs** for errors or warnings.

---

## Sparkplug B Topic Structure

All topics follow the Sparkplug B specification:

```
spBv1.0/{group_id}/{message_type}/{edge_node_id}[/{device_id}]
```

| Message Type | Direction | Description |
|---|---|---|
| `NBIRTH` | Publish | Node birth — full metric catalog for the edge node |
| `NDEATH` | Publish | Node death — sent as LWT by the broker on disconnect |
| `DBIRTH` | Publish | Device birth — full metric catalog for a device |
| `DDEATH` | Publish | Device death — device goes offline |
| `NDATA` | Publish | Node data — changed node-level metrics |
| `DDATA` | Publish | Device data — changed device-level metrics |
| `NCMD` | Subscribe | Node command — inbound commands to the edge node |
| `DCMD` | Subscribe | Device command — inbound commands to a device |

## Sparkplug B Payload Format

The payload encoding depends on the **Data Format Type** chosen in Step 2:

- **Sparkplug B v1.0** — Protocol Buffers (binary, more compact)
- **Sparkplug B v1.0 JSON** — JSON (human-readable, easier to debug)

Both formats carry the same structure. Each message contains a `seq` number
and a list of metrics:

| Field | Description |
|---|---|
| `timestamp` | Message timestamp (epoch ms) |
| `seq` | Sequence number (0–255, wraps) |
| `metrics[]` | Array of metric objects |
| `metrics[].name` | Metric name (tag path relative to device) |
| `metrics[].timestamp` | Metric-level timestamp (epoch ms) |
| `metrics[].datatype` | Sparkplug data type (e.g. `Int32`, `Float`, `Boolean`) |
| `metrics[].value` | The metric value |
