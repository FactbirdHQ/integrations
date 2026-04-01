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
        operationName: 'StopBatchByBatchNumber',
        variables: [
            input: [
                lineId: orders.order.'line-id'.text(),
                batchNumber: orders.order.'production-order'.text(),
                actualStop: orders.order.'actual-stop'.text(),
            ]
        ],
        query: 'mutation StopBatchByBatchNumber($input: StopBatchByBatchNumberInput!) { stopBatchByBatchNumber(input: $input) { __typename } }',
    ])

    // Set the generated JSON as the message body
    message.setBody(builder.toPrettyString())
    
    return message;
}
