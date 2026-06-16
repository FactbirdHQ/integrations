"""
Ignition 8.1: MQTT Transmission Refresh — Timer Script (Step 8)

Periodically writes True to the MQTT Transmission Control/Refresh tag,
causing the transmission module to restart and re-send DBIRTH messages.
This acts as a recurring sample measure to ensure the broker always has
an up-to-date metric catalog.

IMPORTANT: This function definition must remain on line 1 — Ignition
requires the entry-point function to be the first line of the script.

Setup:
  1. In Ignition Designer, go to Scripting → Timer.
  2. Right-click → New Timer Script.
  3. Paste this script.
  4. Set the delay to 20000 ms (20 seconds) with Fixed Rate.
  5. Save the project.
"""
logger = system.util.getLogger("MQTTRefresh")

try:
	# REPLACE THIS: paste the Refresh Edge Node tag path copied from the
	# Tag Browser (Transmission Info → Transmitters → <Transmitter name> →
	# Edge Nodes → Factbird → <UUID provided by Factbird> → Refresh Edge Node).
	path = "<REPLACE_WITH_REFRESH_EDGE_NODE_TAG_PATH>"

	results = system.tag.writeBlocking([path], [True])

	if results[0].isGood():
		logger.info("MQTT Transmission Refresh triggered successfully")
	else:
		logger.warn("Refresh write returned bad quality: " + str(results[0]))

except Exception as e:
	logger.error("Failed to trigger MQTT Transmission Refresh: " + str(e))
