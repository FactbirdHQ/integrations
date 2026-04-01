"""
Event Data Publisher — Tag Change Script (Step 6B)

Publishes tag values to AWS IoT Core via MQTT Transmission (Cirrus Link)
whenever a tag value changes (change-based / event data).

Setup:
  1. In Ignition Designer, go to Scripting → Timer.
  2. Right-click → New Timer Script.
  3. Paste this script.
  4. Under Tag Path, drag in the highest node of your tag provider and
     append /* (e.g. [Factbird Event]Plant1/*).
  5. Save the project.

Configuration:
  - MQTT_SERVER_NAME : Name of the MQTT server setting created in Step 2.
  - MQTT_TOPIC_TEMPLATE : Topic pattern — the client ID is appended automatically.
"""


def onTagChange(initialChange, newValue, previousValue, event, executionCount):
	import json
	import traceback
	import time

	logger = system.util.getLogger("Publisher")

	if not initialChange:
		MQTT_SERVER_NAME = "AWS"              # Update to match your MQTT server setting name
		MQTT_TOPIC_TEMPLATE = "plc/event/{}"  # The client ID is substituted at publish time

		try:
			msg = {
				"timestamp": int(time.time() * 1000),
				"values": [
					{
						"id": str(event.tagPath),
						"v": newValue.value if newValue.quality.isGood() else None,
						"t": newValue.timestamp.getTime(),
						"q": newValue.quality.isGood(),
					}
				],
			}

			payload = json.dumps(msg)

			config = system.cirruslink.transmission.readConfig(
				"server", MQTT_SERVER_NAME
			)
			system.cirruslink.transmission.publish(
				MQTT_SERVER_NAME,
				MQTT_TOPIC_TEMPLATE.format(config["clientId"]),
				bytearray(payload), 1, False,
			)

		except Exception:
			logger.debug(traceback.format_exc())
