# SA Case 2 – Logging Worker

Camunda External Task Worker, der Versandentscheidungen aus einem BPMN-Prozess entgegennimmt und in einer H2 In-Memory-Datenbank protokolliert.

---

## Architektur-Übersicht

```
Camunda Engine (BPMN-Prozess)
        │
        │  External Task: "loggingDecision"
        ▼
┌──────────────────────────────────────────────────────┐
│  LoggingWorker (Einstiegspunkt)                      │
│  - Startet H2 Web-Konsole (Port 8083)                │
│  - Erstellt Camunda ExternalTaskClient                │
│  - Subscribt auf Topic "loggingDecision"              │
│  - Erstellt MySQLClient + LoggingService              │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  LoggingExternalTaskHandler                          │
│  - Liest Prozessvariablen aus dem ExternalTask       │
│  - Null-sichere Konvertierung (Objects.toString)     │
│  - Befüllt HashMap und ruft LoggingService auf       │
│  - Error-Handling: Retry bei technischen Fehlern     │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  LoggingService                                      │
│  - Mappt HashMap → Decision DTO                      │
│  - Null-sichere Typkonvertierung (Long → Integer)    │
│  - Ruft MySQLClient.insertDecision() auf             │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  MySQLClient                                         │
│  - INSERT via PreparedStatement (SQL-Injection-safe) │
│  - Null-Handling: setNull() für nullable Felder      │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  MySQLDatabase                                       │
│  - Stellt JDBC-Connection bereit                     │
│  - Erstellt Tabelle "logTable" automatisch (initTable)│
│  - Aktuell: H2 In-Memory                            │
│  - Später: MySQL (wenn DB freigeschalten)            │
└──────────────────────────────────────────────────────┘
```

---

## Projektstruktur

```
src/main/java/com/fhnw/sa_case2_loggingworker/
│
├── SaCase2LoggingWorkerApplication.java   # Spring Boot Einstiegspunkt (nicht für Worker genutzt)
│
├── Worker/
│   ├── LoggingWorker.java                 # ★ Haupt-Einstiegspunkt des Workers
│   └── LoggingExternalTaskHandler.java    # Verarbeitet eingehende Camunda-Tasks
│
├── Service/
│   └── LoggingService.java               # Business-Logik: Datenaufbereitung + DB-Aufruf
│
├── DTO/
│   └── Decision.java                     # Datenmodell für eine Versandentscheidung
│
├── DatabaseClient/
│   └── MySQLClient.java                  # INSERT mit PreparedStatements
│
└── Database/
    └── MySQLDatabase.java                # JDBC-Connection + Tabellen-Initialisierung
```

---

## Klassen im Detail

### 1. `LoggingWorker` (Einstiegspunkt)

**Datei:** `Worker/LoggingWorker.java`

Der Haupt-Einstiegspunkt. Wird über `main()` gestartet und macht drei Dinge:

1. **H2 Web-Konsole starten** auf Port 8083 – damit Einträge im Browser geprüft werden können
2. **Camunda ExternalTaskClient** erstellen – verbindet sich mit der Camunda Engine (`http://192.168.111.3:8080/engine-rest`)
3. **Topic subscriben** – hört auf `"loggingDecision"` und leitet eingehende Tasks an den Handler weiter

```
LoggingWorker.main()
  ├── H2 Web-Konsole (Port 8083)
  ├── MySQLClient → MySQLDatabase → DB-Connection + Tabelle "logTable"
  ├── LoggingService(MySQLClient)
  └── client.subscribe("loggingDecision") → LoggingExternalTaskHandler
```

---

### 2. `LoggingExternalTaskHandler` (Task-Verarbeitung)

**Datei:** `Worker/LoggingExternalTaskHandler.java`

Implementiert `ExternalTaskHandler` von Camunda. Wird für jeden eingehenden Task aufgerufen.

**Was passiert:**
1. Liest Prozessvariablen aus dem `ExternalTask` (z.B. `orderId`, `carrier`, `weight`)
2. Null-sichere Konvertierung mit `Objects.toString()` für Enum-Felder (`reason`, `country`, `userId`)
3. Befüllt eine `HashMap<String, Object>` mit allen Werten
4. Ruft `LoggingService.dataPreparation()` auf
5. Bei Erfolg: `externalTaskService.complete()` → Task in Camunda abschliessen
6. Bei Fehler: Retry-Strategie oder Abbruch

**Error-Handling (3 Stufen):**

| Exception | Reaktion |
|---|---|
| `WebApplicationException` / `ProcessingException` | Retry mit max. 3 Versuchen, 60s Wartezeit |
| `Exception` (z.B. Datentypfehler) | Log + Abbruch, Task wird **nicht** als complete markiert |
| Erfolg | `externalTaskService.complete()` |

**Variablen-Mapping (Camunda → HashMap):**

| Camunda-Variable | HashMap-Key | Typ | Nullable? |
|---|---|---|---|
| `ruleId` | `ruleId` | `Long` | ✅ |
| `userId` | `benutzerId` | `String` | ✅ |
| `reason` | `grund` | `String` (Enum) | ✅ |
| `orderId` | `bestellnummer` | `String` | ❌ |
| `destination` | `lieferadresse` | `String` | ❌ |
| `carrier` | `spediteur` | `String` | ❌ |
| `shippingType` | `versandart` | `String` | ❌ |
| `decisionType` | `entscheidungsart` | `String` | ❌ |
| `country` | `land` | `String` (Enum) | ✅ |
| `weight` | `gewicht` | `Long` | ❌ |

---

### 3. `LoggingService` (Business-Logik)

**Datei:** `Service/LoggingService.java`

Nimmt die HashMap entgegen und bereitet die Daten für die Datenbank auf:

1. **Mapping:** `HashMap<String, Object>` → `Decision` DTO
2. **Typkonvertierung:** `ruleId` kommt als `Long` von Camunda, wird null-sicher zu `Integer` konvertiert:
   ```java
   Long ruleIdRaw = (Long) variables.get("ruleId");
   decision.setRuleId(ruleIdRaw != null ? Math.toIntExact(ruleIdRaw) : null);
   ```
3. **DB-Aufruf:** `sqlClient.insertDecision(decision)`
4. Bei Fehler: Wirft `RuntimeException` mit Original-Exception als Cause

---

### 4. `Decision` (DTO)

**Datei:** `DTO/Decision.java`

Einfaches Data Transfer Object mit Gettern/Settern. Repräsentiert eine Versandentscheidung.

| Feld | Typ | DB-Spalte | Nullable? |
|---|---|---|---|
| `ruleId` | `Integer` | `ruleId` | ✅ |
| `benutzerId` | `String` | `userId` | ✅ |
| `grund` | `String` | `reason` | ✅ |
| `bestellnummer` | `String` | `orderId` | ❌ |
| `lieferadresse` | `String` | `address` | ❌ |
| `spediteur` | `String` | `carrier` | ❌ |
| `versandart` | `String` | `shippingType` | ❌ |
| `entscheidungsart` | `String` | `decisionType` | ❌ |
| `land` | `String` | `country` | ❌ |
| `gewicht` | `Long` | `weight` | ❌ |

> **Hinweis:** `logId` (AUTO_INCREMENT PK) und `created_at` (Timestamp) werden automatisch von der Datenbank gesetzt und sind nicht im DTO enthalten.

---

### 5. `MySQLClient` (Datenbank-Zugriff)

**Datei:** `DatabaseClient/MySQLClient.java`

Enthält eine Methode mit **PreparedStatement** (SQL-Injection-sicher):

#### `insertDecision(Decision decision)`
- INSERT in Tabelle `logTable` mit 10 Platzhaltern (`?`)
- Spalten: `orderId`, `ruleId`, `userId`, `reason`, `address`, `carrier`, `shippingType`, `decisionType`, `country`, `weight`
- Nullable Feld `ruleId` wird mit `stmt.setNull(2, java.sql.Types.INTEGER)` korrekt als `SQL NULL` gespeichert
- `benutzerId` und `grund` werden als `String` übergeben – `null` wird automatisch als `SQL NULL` behandelt
- `logId` und `created_at` werden automatisch von der DB generiert

---

### 6. `MySQLDatabase` (Connection-Management)

**Datei:** `Database/MySQLDatabase.java`

Stellt die JDBC-Connection bereit und initialisiert die Tabelle.

**Aktuell:** H2 In-Memory
```
jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
```
> `DB_CLOSE_DELAY=-1` sorgt dafür, dass die DB offen bleibt solange die JVM läuft.

**Später (MySQL):** Auskommentierte Zeile aktivieren:
```
jdbc:mysql://127.0.0.1:3306/logging_db
```

**`initTable()`:** Erstellt die Tabelle `logTable` automatisch beim Start mit `CREATE TABLE IF NOT EXISTS`.

---

## Datenbank

### Tabelle `logTable`

```sql
CREATE TABLE IF NOT EXISTS logTable (
    logId          INT           AUTO_INCREMENT PRIMARY KEY,
    orderId        VARCHAR(255)  NOT NULL,
    ruleId         INT           NULL,
    userId         VARCHAR(255)  NULL,
    reason         VARCHAR(255)  NULL,
    address        VARCHAR(255)  NOT NULL,
    carrier        VARCHAR(255)  NOT NULL,
    shippingType   VARCHAR(255)  NOT NULL,
    decisionType   VARCHAR(255)  NOT NULL,
    country        VARCHAR(255)  NOT NULL,
    weight         BIGINT        NOT NULL,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);
```

| Spalte | Typ | Beschreibung |
|---|---|---|
| `logId` | `INT AUTO_INCREMENT` | **Primary Key** – wird automatisch hochgezählt |
| `orderId` | `VARCHAR(255) NOT NULL` | Bestellnummer |
| `ruleId` | `INT NULL` | Regel-ID (kann null sein bei manueller Entscheidung) |
| `userId` | `VARCHAR(255) NULL` | Benutzer-ID (kann null sein bei automatischer Entscheidung) |
| `reason` | `VARCHAR(255) NULL` | Begründung / Enum (kann null sein) |
| `address` | `VARCHAR(255) NOT NULL` | Lieferadresse |
| `carrier` | `VARCHAR(255) NOT NULL` | Spediteur |
| `shippingType` | `VARCHAR(255) NOT NULL` | Versandart |
| `decisionType` | `VARCHAR(255) NOT NULL` | Entscheidungsart |
| `country` | `VARCHAR(255) NOT NULL` | Zielland |
| `weight` | `BIGINT NOT NULL` | Gewicht |
| `created_at` | `TIMESTAMP` | Zeitstempel des Eintrags (automatisch gesetzt) |

### H2 → MySQL umstellen

Wenn der Dozent die MySQL-DB freischaltet:

1. In `MySQLDatabase.java`: H2-Zeile auskommentieren, MySQL-Zeile aktivieren
2. In `pom.xml`: H2-Dependency auskommentieren, `mysql-connector-j` aktivieren
3. In MySQL Workbench: `CREATE DATABASE logging_db;` + obiges CREATE TABLE ausführen
4. `initTable()` kann entfernt oder beibehalten werden (MySQL unterstützt `IF NOT EXISTS`)

---

## Starten & H2-Konsole

### Worker starten

`LoggingWorker.main()` ausführen (nicht `SaCase2LoggingWorkerApplication`).

### H2 Web-Konsole öffnen

Nach dem Start im Browser öffnen: **http://localhost:8083**

Login-Daten:

| Feld | Wert |
|---|---|
| JDBC URL | `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` |
| User | `sa` |
| Password | *(leer)* |

Daten prüfen:
```sql
SELECT * FROM logTable;
```

---

## Ablauf: Vom Camunda-Task zum DB-Eintrag

```
1. Camunda-Prozess erzeugt External Task "loggingDecision"
       │
2. LoggingWorker pollt den Task (asyncResponseTimeout: 1000ms)
       │
3. LoggingExternalTaskHandler.execute() wird aufgerufen
       │
4. Prozessvariablen auslesen (orderId, carrier, weight, ...)
       │
5. Null-sichere Konvertierung (Objects.toString für Enums/nullable Felder)
       │
6. HashMap befüllen und an LoggingService übergeben
       │
7. LoggingService.dataPreparation():
   - HashMap → Decision DTO mappen
   - Long → Integer Konvertierung für ruleId (null-sicher)
       │
8. MySQLClient.insertDecision():
   - PreparedStatement mit 10 Platzhaltern
   - Nullable Felder → setNull()
   - stmt.executeUpdate()
   - logId + created_at werden automatisch generiert
       │
9. Bei Erfolg: externalTaskService.complete()
   Bei Fehler: Retry oder Abbruch (Task bleibt offen in Camunda)
```

---

## Konfiguration

### `application.properties`

```properties
spring.application.name=SA_Case2_LoggingWorker
server.port=8082

# H2 In-Memory Datenbank
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
```

---

## Technologien

| Technologie | Version | Zweck |
|---|---|---|
| Java | 21 | Programmiersprache |
| Spring Boot | 4.0.3 | Framework (Application-Kontext) |
| Camunda External Task Client | 1.3.1 | Kommunikation mit Camunda Engine |
| H2 Database | 2.2.224 | In-Memory DB + Web-Konsole |
| MySQL Connector | 9.2.0 | JDBC-Treiber (vorbereitet, noch deaktiviert) |
| Jersey Client | 4.0.2 | JAX-RS HTTP Client |
| Jackson | – | JSON Serialisierung |

