# SA Case 2 – Logging Worker

Camunda External Task Worker, der Versandentscheidungen aus einem BPMN-Prozess entgegennimmt und in einer Datenbank protokolliert.

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
│  - SELECT via PreparedStatement (findByBestellnr.)   │
│  - Null-Handling: setNull() für nullable Felder      │
└──────────────┬───────────────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────────────┐
│  MySQLDatabase                                       │
│  - Stellt JDBC-Connection bereit                     │
│  - Erstellt Tabelle automatisch (initTable)          │
│  - Aktuell: H2 In-Memory (temporär)                 │
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
│   └── MySQLClient.java                  # INSERT/SELECT mit PreparedStatements
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
2. **Camunda ExternalTaskClient** erstellen – verbindet sich mit der Camunda Engine
3. **Topic subscriben** – hört auf `"loggingDecision"` und leitet eingehende Tasks an den Handler weiter

```
LoggingWorker.main()
  ├── H2 Web-Konsole (Port 8083)
  ├── MySQLClient → MySQLDatabase → DB-Connection + Tabelle
  ├── LoggingService(MySQLClient)
  └── client.subscribe("loggingDecision") → LoggingExternalTaskHandler
```

---

### 2. `LoggingExternalTaskHandler` (Task-Verarbeitung)

**Datei:** `Worker/LoggingExternalTaskHandler.java`

Implementiert `ExternalTaskHandler` von Camunda. Wird für jeden eingehenden Task aufgerufen.

**Was passiert:**
1. Liest Prozessvariablen aus dem `ExternalTask` (z.B. `orderId`, `carrier`, `weight`)
2. Null-sichere Konvertierung mit `Objects.toString()` für Enum-Felder (`reason`, `country`)
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
| `ruleId` | `Integer` | `rule_id` | ✅ |
| `benutzerId` | `String` | `benutzer_id` | ✅ |
| `grund` | `String` | `grund` | ✅ |
| `bestellnummer` | `String` | `bestellnummer` (PK) | ❌ |
| `lieferadresse` | `String` | `lieferadresse` | ❌ |
| `spediteur` | `String` | `spediteur` | ❌ |
| `versandart` | `String` | `versandart` | ❌ |
| `entscheidungsart` | `String` | `entscheidungsart` | ❌ |
| `land` | `String` | `land` | ❌ |
| `gewicht` | `Long` | `gewicht` | ❌ |

---

### 5. `MySQLClient` (Datenbank-Zugriff)

**Datei:** `DatabaseClient/MySQLClient.java`

Enthält zwei Methoden mit **PreparedStatements** (SQL-Injection-sicher):

#### `insertDecision(Decision decision)`
- INSERT mit 10 Platzhaltern (`?`)
- Nullable Felder (`ruleId`, `benutzerId`, `grund`) werden mit `stmt.setNull()` korrekt als `SQL NULL` gespeichert

#### `findByBestellnummer(String bestellnummer)`
- SELECT mit `WHERE bestellnummer = ?`
- Liest `ResultSet` zurück in ein `Decision`-Objekt
- `rs.wasNull()` prüft ob `rule_id` / `gewicht` in der DB `NULL` sind
- Gibt `null` zurück wenn kein Eintrag gefunden

---

### 6. `MySQLDatabase` (Connection-Management)

**Datei:** `Database/MySQLDatabase.java`

Stellt die JDBC-Connection bereit und initialisiert die Tabelle.

**Aktuell:** H2 In-Memory (temporär, bis MySQL vom Dozenten freigeschalten wird)
```
jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
```

**Später (MySQL):** Auskommentierte Zeile aktivieren:
```
jdbc:mysql://127.0.0.1:3306/logging_db
```

**`initTable()`:** Erstellt die `decision`-Tabelle automatisch beim Start mit `CREATE TABLE IF NOT EXISTS`.

---

## Datenbank

### Tabelle `decision`

```sql
CREATE TABLE IF NOT EXISTS decision (
    bestellnummer    VARCHAR(255)  NOT NULL PRIMARY KEY,
    rule_id          INT           NULL,
    benutzer_id      VARCHAR(255)  NULL,
    grund            VARCHAR(255)  NULL,
    lieferadresse    VARCHAR(255)  NOT NULL,
    spediteur        VARCHAR(255)  NOT NULL,
    versandart       VARCHAR(255)  NOT NULL,
    entscheidungsart VARCHAR(255)  NOT NULL,
    land             VARCHAR(255)  NOT NULL,
    gewicht          BIGINT        NOT NULL
);
```

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

```sql
SELECT * FROM decision;
```

---

## Ablauf: Vom Camunda-Task zum DB-Eintrag

```
1. Camunda-Prozess erzeugt External Task "loggingDecision"
       │
2. LoggingWorker pollt den Task
       │
3. LoggingExternalTaskHandler.execute() wird aufgerufen
       │
4. Prozessvariablen auslesen (orderId, carrier, weight, ...)
       │
5. Null-sichere Konvertierung (Objects.toString für Enums)
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
| H2 Database | 2.2.224 | Temporäre In-Memory DB |
| MySQL Connector | 9.2.0 | JDBC-Treiber (wenn MySQL aktiv) |
| Jersey Client | 4.0.2 | JAX-RS HTTP Client |
| Jackson | – | JSON Serialisierung |

