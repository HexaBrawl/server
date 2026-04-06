package at.aau.hexabrawl.websocketserver.websocket.broker;

import at.aau.hexabrawl.websocketserver.messaging.dtos.StompMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;

import org.springframework.stereotype.Controller;


@Controller
public class WebSocketBrokerController {
    @MessageMapping("/hello")
    @SendTo("/topic/hello-response")
    public String handleHello(String text) {
        // TODO handle the messages here
        return "echo from broker: "+text;
    }
    @MessageMapping("/object")
    @SendTo("/topic/rcv-object")
    public StompMessage handleObject(StompMessage msg) {

       return msg;
    }

}
