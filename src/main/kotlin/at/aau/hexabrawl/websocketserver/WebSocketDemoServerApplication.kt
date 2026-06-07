package at.aau.hexabrawl.websocketserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class WebSocketDemoServerApplication

fun main(args: Array<String>) {
    runApplication<WebSocketDemoServerApplication>(*args)
}