def handleTimerEvent():
	"""
	Input Data Publisher — Timer Script (Step 6A)

	Publishes all tags from a designated tag provider to AWS IoT Core on a
	timed interval via MQTT Transmission (Cirrus Link).

	IMPORTANT: This function definition must remain on line 1 — Ignition
	requires the entry-point function to be the first line of the script.

	Setup:
	  1. In Ignition Designer, go to Scripting → Timer.
	  2. Right-click → New Timer Script.
	  3. Paste this script.
	  4. Set the delay to 5000 ms (5 seconds).
	  5. Save the project.

	Configuration:
	  - MQTT_SERVER_NAME : Name of the MQTT server setting created in Step 2.
	  - MQTT_TOPIC_TEMPLATE : Topic pattern — the client ID is appended automatically.
	  - provider : Name of the Realtime Tag Provider created in Step 4A.
	  - limit : Maximum number of tags to query per cycle.
	"""
	import json
	import traceback
	import time

	logger = system.util.getLogger("Publisher")

	MQTT_SERVER_NAME = "AWS"            # Update to match your MQTT server setting name
	MQTT_TOPIC_TEMPLATE = "plc/input/{}"  # The client ID is substituted at publish time
	provider = "Sample_Tags"            # Update to match your tag provider name
	limit = 100

	query = {
		"condition": {
			"path": "*",
			"tagType": "AtomicTag",
		}
	}

	results = system.tag.query(provider, query, limit)

	msg = {"timestamp": int(time.time() * 1000), "values": []}

	for tag in results:
		qualifiedValue = tag["value"]
		msg["values"].append({
			"id": tag["name"],
			"v": qualifiedValue.value if qualifiedValue.quality.isGood() else None,
			"t": qualifiedValue.timestamp.getTime(),
			"q": qualifiedValue.quality.isGood(),
		})

	payload = json.dumps(msg)

	config = system.cirruslink.transmission.readConfig("server", MQTT_SERVER_NAME)
	system.cirruslink.transmission.publish(
		MQTT_SERVER_NAME,
		MQTT_TOPIC_TEMPLATE.format(config["clientId"]),
		bytearray(payload), 1, False,
	)
