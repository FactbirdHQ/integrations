import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    def body = message.getBody(String.class);
    
    message.setProperty("productionOrder", body);
    return message;
}