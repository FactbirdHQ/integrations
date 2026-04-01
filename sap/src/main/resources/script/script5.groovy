import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.util.XmlSlurper;
import groovy.json.JsonBuilder;

def Message processData(Message message) {
    def orders = new XmlSlurper().parseText(message.getBody(java.lang.String))
    
    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog != null) {
        messageLog.addAttachmentAsString("orders", message.getBody(java.lang.String), "application/json");
    }

    def builder = new JsonBuilder([
        operationName: 'StartBatchByBatchNumber',
        variables: [
            input: [
                lineId: orders.order.'line-id'.text(),
                batchNumber: orders.order.'production-order'.text(),
                actualStart: orders.order.'actual-start'.text(),
                forceStop: true
            ]
        ],
        query: 'mutation StartBatchByBatchNumber($input: StartBatchByBatchNumberInput!) { startBatchByBatchNumber(input: $input) { __typename } }',
    ])

    // Set the generated JSON as the message body
    message.setBody(builder.toPrettyString())
    
    return message;
}
