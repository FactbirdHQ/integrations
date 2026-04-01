import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog != null) {
        messageLog.addAttachmentAsString("Response", message.getBody(java.lang.String), "application/json");
    }

    return message;
}