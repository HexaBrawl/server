package at.aau.hexabrawl.websocketserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Spring-Boot-Einstiegspunkt. @EnableScheduling aktiviert die
 * `@Scheduled`-Tasks von [at.aau.hexabrawl.websocketserver.service.DisconnectCleanupService]
 * und [at.aau.hexabrawl.websocketserver.service.RoomCleanupService].
 */
@SpringBootApplication
@EnableScheduling
class WebSocketDemoServerApplication

/** Startet die Spring-Boot-Anwendung. */
fun main(args: Array<String>) {
    runApplication<WebSocketDemoServerApplication>(*args)
}