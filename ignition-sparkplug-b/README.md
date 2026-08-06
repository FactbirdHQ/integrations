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
| Certificate bundle from Factbird | Device cert (`.cert.pem`), private key (`.private.key`) |
| AWS | Root CA |

---

> **Where to perform these steps:** Sections **1–4** are configured in the
> **Ignition Gateway** (Web UI), while sections **5–6** are performed in the
> **Ignition Designer**.

---

# Performed in the Ignition Gateway (steps 1–4)

## 1 — Upload Certificates

In the Ignition Gateway Web UI navigate to **Connections → MQTT Transmission
Settings → Servers → Certificates**.

Click **Create new Certificate** and upload each file from the downloaded certificate archive:

- Device certificate (`.cert.pem`)
- Private key (`.private.key`)
- Root CA certificate ([https://www.amazontrust.com/repository/AmazonRootCA1.pem](https://www.amazontrust.com/repository/AmazonRootCA1.pem))

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
| **Advanced Client ID** | UUID provided by Factbird |
| **Subscribe To Legacy STATE Topic** | Unchecked |
| **Data Format Type** | Sparkplug B v1.0 *(Protobuf)* or Sparkplug B v1.0 JSON |

Click **Create Server**.

---

## 3 — Create a Transmitter

**Config → MQTT Transmission → Settings → Transmitters → Create new Setting**

| Field | Value |
|---|---|
| **Name** | `Factbird Input Transmitter` |
| **Enabled** | **Checked** |
| **Tag Provider** | `Factbird_Input_Tags` |
| **Tag Path** | *(blank, or a subfolder path)* |
| **Server Set** | AWS IoT Core server set |
| **Group ID** | `Factbird` |
| **Edge Node ID** | UUID provided by Factbird |
| **Device ID** | Leave blank |

## 4 — Create a Tag Provider

**Services → Tags → Realtime Tag Providers → Create Tag Provider**

- Type: *Standard Tag Provider*
- Name: `Factbird_Input_Tags`

# Performed in the Ignition Designer (steps 5–6)

## 5 — Build the Tag Tree

MQTT Transmission requires at least three folder levels that map directly to
the Sparkplug B topic namespace: **Group → Edge Node → Device**.

```
Factbird_Input_Tags/
└── Factbird/                  <-- Group ID
    └── <factbird-uuid>/       <-- Edge Node ID — UUID provided by Factbird
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
spBv1.0/Factbird/DDATA/<factbird-uuid>/Boiler_PLC
```

## 6 — Deploy the Refresh Timer Script (Temporary Workaround)

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
3. Paste the contents of
    - 8.1: [`scripts/8.1/refresh-transmission.py`](scripts/8.1/refresh-transmission.py)
    - 8.3+: [`scripts/8.3/refresh-transmission.py`](scripts/8.3/refresh-transmission.py)
4. Set the **Delay** to `20000` ms (20 seconds).
5. Set **Delay Type** to **Fixed Rate**.
6. Save the project.

Factbird supports two data pipelines, which must be configured as **separate
Tag Providers**:

1. **Periodic data** — data that must be sent at a fixed frequency, even when
   values are unchanged. This pipeline requires the timer script to drive the
   recurring refresh.
2. **Event data** — data sent only when something happens; this pipeline needs
   no special handling.

For the periodic pipeline, the script writes `True` to the **Refresh Edge
Node** tag, which triggers the module to restart and publish fresh birth
certificates. The tag path is specific to your transmitter and edge node, so
extract it from the **Tag Browser** in the Ignition Designer rather than
hard-coding the built-in path. Browse to:

```
Transmission Info → Transmitters → Factbird Input Transmitter → Edge Nodes → Factbird → <UUID provided by Factbird> → Refresh Edge Node
```

Right-click the **Refresh Edge Node** tag and choose **Copy Path**, then paste
the resulting path into the referenced timer script (8.1 or 8.3) in place of
the example `path` value.

## 7 — Verify the Connection

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
