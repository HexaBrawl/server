# ⚔️ HexaBrawl - Game Server

Ein Spring-Boot-basierter Game-Server für das Hexagon-Strategieduell. Dieses Backend verwaltet den Spielstatus, die Spieler-Lobbys und die Echtzeit-Kommunikation via WebSockets.

# Link zum Wiki
https://github.com/HexaBrawl/server/wiki

## Integration and Unit Tests (noch nicht implementiert)
Wir verwenden Unit und Integrationtest um die Code-Qualität sicherzustellen


## Verwendetet Technologien
Spring-Boot mit Kotlin


## Kommunikationsprotokoll
STOMP

## Security Testing
Dieses Projekt nutzt **OWASP ZAP (Zed Attack Proxy)** für automatisiertes Dynamic Application Security Testing (DAST).
Bei jedem Push und Pull Request in die Hauptbranches startet GitHub Actions die Applikation via Docker Compose und führt einen ZAP Baseline Scan durch. Die Ergebnisse können im "Actions" Tab als HTML-Report heruntergeladen werden.


## Quick Start
- Repo clonen 
- IntelliJ öffnen 
- WebSocketDemoServerApplication (Name wird sich demnächst ändern) ausführen 

## 🔗 Partner-Repository
Das Frontend (Android App) findet man hier: https://github.com/HexaBrawl/app