package at.aau.hexabrawl.websocketserver.config

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.security.Principal

/**
 * Setzt jeder neuen STOMP-Session einen anonymen Principal mit der
 * WebSocket-Session-ID als Namen.
 *
 * Hintergrund: `SimpMessagingTemplate.convertAndSendToUser(name, ...)`
 * routet ueber `SimpUserRegistry`. Der Registry indexiert Sessions
 * ausschliesslich nach Principal-Name — Sessions ohne Principal
 * werden gar nicht getrackt. Ohne Authentication ist das bei uns
 * der Default, weshalb bisher alle Error-Frames an
 * `/user/queue/errors` stumm verworfen wurden.
 *
 * Loesung: beim CONNECT-Frame setzen wir [StompHeaderAccessor.user]
 * auf einen [Principal], dessen Name exakt die WebSocket-Session-ID
 * ist. Damit ist die Session unter genau dieser ID im Registry
 * eingetragen und `convertAndSendToUser(sessionId, ...)` findet sie.
 *
 * Es muss nichts an den Controllern geaendert werden — sie nutzen
 * weiterhin [SimpMessageHeaderAccessor.sessionId] als Identifier
 * und das stimmt jetzt mit dem Principal-Namen ueberein.
 */
@Configuration
class WebSocketAuthConfig : WebSocketMessageBrokerConfigurer {

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(
                    message,
                    StompHeaderAccessor::class.java
                )
                if (accessor?.command == StompCommand.CONNECT) {
                    accessor.sessionId?.let { sid ->
                        accessor.user = Principal { sid }
                    }
                }
                return message
            }
        })
    }
}
