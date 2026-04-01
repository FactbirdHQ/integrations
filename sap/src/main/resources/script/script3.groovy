import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.util.XmlSlurper;
import groovy.xml.MarkupBuilder;
import groovy.util.slurpersupport.NodeChildren;
import groovy.json.JsonBuilder;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

def String formatTimePair(String dateTime, String relativeTime) {
    def dateTimeInstant = Instant.parse(dateTime + "Z")
    def relativeTimeInstant = Instant.parse(relativeTime + "Z")

    def absoluteTime = dateTimeInstant.plusMillis(relativeTimeInstant.toEpochMilli())

    DateTimeFormatter.ISO_INSTANT.format(absoluteTime)
}

def zipTimestampsIntoMillis(NodeChildren startDates, NodeChildren startTimes) {
    [startDates.iterator().collect(), startTimes.iterator().collect()]
        .transpose()
        .findAll { it -> it[0].text() && it[1].text() }.collect { it ->
            def dateTimeInstant = Instant.parse(it[0].text() + "Z")
            def relativeTimeInstant = Instant.parse(it[1].text() + "Z")

            dateTimeInstant.plusMillis(relativeTimeInstant.toEpochMilli())
        }
}

// If you want this dynamically, you can match names against ids listed from
// https://api.cloud.factbird.com/v1/docs/#query-lines or a looped process call
// against https://api.cloud.factbird.com/v1/docs/#query-linesPaginated (we have
// not yet stabilized this latter one as of this writing)
lineIdMapping = [
    '1710PACKING': 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee'
]

def Message processData(Message message) {
    def processOrder = new XmlSlurper().parseText(message.getProperty("processOrder"))

    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog != null) {
        messageLog.addAttachmentAsString("processOrder", message.getProperty("processOrder"), "application/json");
    }

    def builder = new JsonBuilder()

    def output = []

    processOrder.A_ProcessOrder_2Type.each { order ->
        def released = order.to_ProcessOrderStatus.children().any { status ->
            status.StatusShortName.text() == "SETC"
            //status.StatusShortName.text() == "REL"
        }

        if (!released) {
            return
        }

        def operation = order.to_ProcessOrderOperation.A_ProcessOrderOperation_2Type

        // Process Orders may propagate from one work center to another - we
        // simply make the assumption it'll remain at the same location.
        def initiallyAllocatedWorkCenter = operation.WorkCenter[0]

        // With that same assumption, all operations are expected to take place
        // in order, so for actualStart and actualStop we find the extrema
        def actualStart = zipTimestampsIntoMillis(operation.OpActualExecutionStartDate, operation.OpActualExecutionStartTime).min()
        if (actualStart) {
            DateTimeFormatter.ISO_INSTANT.format(actualStart)
        }
        def actualStop = zipTimestampsIntoMillis(operation.OpActualExecutionEndDate, operation.OpActualExecutionEndTime).max()
        if (actualStop) {
            DateTimeFormatter.ISO_INSTANT.format(actualStop)
        }

        output << [
            externalLineId: order.Plant.text() + initiallyAllocatedWorkCenter.text(),
            batchNumber: order.ManufacturingOrder.text(),
            itemNumber: order.Material.text(),
            amount: order.TotalQuantity.text(),
            plannedStart: formatTimePair(order.MfgOrderPlannedStartDate.text(), order.MfgOrderPlannedStartTime.text()),
            actualStart: actualStart,
            actualStop: actualStop
        ]
    }

    def resultXml = new StringWriter()
    def xmlBuilder = new MarkupBuilder(resultXml)

    xmlBuilder.'process-orders' {
        output.each { row ->
            'order'(action: row.actualStop ? 'stop' : (row.actualStart ? 'start' : 'create')) {
                'line-id'(lineIdMapping[row.externalLineId])
                'batch-number'(row.batchNumber)
                'item-number'(row.itemNumber)
                'amount'(row.amount)
                'planned-start'(row.plannedStart)
                'actual-start'(row.actualStart)
                'actual-stop'(row.actualStop)
            }
        }
    }

    message.setBody(resultXml.toString())

    return message;
}
