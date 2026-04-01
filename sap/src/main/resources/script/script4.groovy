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
        operationName: 'CreateBatchByItemNumber',
        variables: [
            input: [
                lineId: orders.order.'line-id'.text(),
                batchNumber: orders.order.'batch-number'.text(),
                itemNumber: orders.order.'item-number'.text(),
                amount: Float.parseFloat(orders.order.'amount'.text()),
                plannedStart: orders.order.'planned-start'.text(),
            ]
        ],
        query: 'mutation CreateBatchByItemNumber($input: CreateBatchByItemNumberInput!) { createBatchByItemNumber(input: $input) { __typename } }',
    ])

    message.setBody(builder.toPrettyString())
    
    return message;
}
