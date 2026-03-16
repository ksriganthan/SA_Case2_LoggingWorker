# SA Case 2 – Logging Worker

Camunda External Task Worker, der Versandentscheidungen aus einem BPMN-Prozess entgegennimmt und in einer **MySQL-Datenbank** protokolliert.

---

## Architektur-Übersicht

```
Camunda Engine (BPMN-Prozess)
        │
        │  External Task: "loggingDecision"
        ▼
┌──────────────────────────────────────────────────────┐
│  LoggingWorker (Einstiegspunkt)                      │
│  - Erstellt Camunda ExternalTaskClient               │
│  - Subscribt auf Topic "loggingDecision"             │
│  - Erstellt MySQLClient + LoggingService             │
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
│  - Formatierte Konsolenausgabe nach Eintrag          │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  MySQLDatabase                                       │
│  - Stellt JDBC-Connection bereit (MySQL)             │
│  - jdbc:mysql://xxx/db_group6         │
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
│   └── MySQLClient.java                  # INSERT mit PreparedStatements + Konsolenausgabe
│
└── Database/
    ├── MySQLDatabase.java                # JDBC-Connection zu MySQL (aktiv)
    └── MySQLDatabaseOriginal.java        # Backup/Original-Version
```

---

## Klassen im Detail

### 1. `LoggingWorker` (Einstiegspunkt)

**Datei:** `Worker/LoggingWorker.java`

Der Haupt-Einstiegspunkt. Wird über `main()` gestartet und macht folgendes:

1. **Camunda ExternalTaskClient** erstellen – verbindet sich mit der Camunda Engine (`http://192.168.111.3:8080/engine-rest`) mit Basic Auth (`group6`)
2. **MySQLClient** instanziieren – stellt Verbindung zur MySQL-DB her
3. **LoggingService** erstellen – erhält den MySQLClient als Dependency
4. **Topic subscriben** – hört auf `"loggingDecision"` und leitet eingehende Tasks an den Handler weiter

```
LoggingWorker.main()
  ├── ExternalTaskClient → Camunda Engine (192.168.111.3:8080)
  ├── MySQLClient → MySQLDatabase → MySQL-Connection
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
| `Exception` (z.B. Datentypfehler) | Log + Stacktrace, Task wird **nicht** als complete markiert |
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

**Konsolenausgabe nach erfolgreichem INSERT:**

```
╔══════════════════════════════════════════════════════════╗
║           Decision erfolgreich gespeichert              ║
╠══════════════════════════════════════════════════════════╣
║  Bestellnummer   : B-12345                              ║
║  Rule-ID         : 7                                    ║
║  Benutzer-ID     : user42                               ║
║  Grund           : –                                    ║
║  Lieferadresse   : Musterstrasse 1                      ║
║  Spediteur       : DHL                                  ║
║  Versandart      : Express                              ║
║  Entscheidungsart: Automatisch                          ║
║  Land            : CH                                   ║
║  Gewicht         : 2500 g                               ║
╚══════════════════════════════════════════════════════════╝
```

Nullable Felder (`ruleId`, `benutzerId`, `grund`) werden als `–` angezeigt wenn sie `null` sind.

---

### 6. `MySQLDatabase` (Connection-Management)

**Datei:** `Database/MySQLDatabase.java`

Stellt die JDBC-Connection zur **MySQL-Datenbank** bereit.

---

## Datenbank

### Verbindungsdaten

| Feld | Wert                         |
|---|------------------------------|
| Host | `192.168.111.4`              |
| Port | `3306`                       |
| Datenbank | `db_group6`                  |
| User | `group6`                     |
| JDBC URL | `jdbc:mysql://xxx/db_group6` |

### Tabelle `logTable`

Die Tabelle muss in MySQL existieren. SQL zum Erstellen:

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
| `weight` | `BIGINT NOT NULL` | Gewicht in Gramm |
| `created_at` | `TIMESTAMP` | Zeitstempel des Eintrags (automatisch gesetzt) |

### Daten in MySQL Workbench prüfen

```sql
SELECT * FROM logTable;
```

---

## Starten

### Worker starten

`LoggingWorker.main()` ausführen (nicht `SaCase2LoggingWorkerApplication`).

**Voraussetzungen:**
- MySQL-Datenbank muss erreichbar sein
- Tabelle `logTable` muss in `db_group6` existieren
- Camunda Engine auf `192.168.111.3:8080` muss laufen

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
   - Formatierte Konsolenausgabe aller Felder
       │
9. Bei Erfolg: externalTaskService.complete()
   Bei Fehler: Retry oder Abbruch (Task bleibt offen in Camunda)
```

---

## Technologien

| Technologie | Version | Zweck |
|---|---|---|
| Java | 21 | Programmiersprache |
| Spring Boot | 4.0.3 | Framework (Application-Kontext) |
| Camunda External Task Client | 1.3.1 | Kommunikation mit Camunda Engine |
| MySQL Connector | 9.2.0 | JDBC-Treiber für MySQL |
| Jersey Client | 4.0.2 | JAX-RS HTTP Client |
| Jackson | – | JSON Serialisierung |
| H2 Database | 2.2.224 | Backup für lokales Testen (optional) |
