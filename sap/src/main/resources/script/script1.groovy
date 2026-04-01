import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.json.JsonSlurper;

def Message processData(Message message) {
    def body = message.getBody(String.class);    
    def jsonSlurper = new JsonSlurper();
    def payload = jsonSlurper.parseText(body);
    
    message.setProperty("manufacturingOrder", payload.data.ManufacturingOrder);
    
    return message;
}