package com.fhnw.sa_case2_loggingworker.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class MySQLDatabase {
    Connection connection;

    public MySQLDatabase() {
        try {
            // H2 In-Memory DB (temporär bis MySQL freigeschalten)
            // DB_CLOSE_DELAY=-1 → bleibt offen solange JVM läuft
            connection = DriverManager.getConnection(
                    "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", ""
            );

            // Tabelle automatisch erstellen falls nicht vorhanden
            initTable();

            // Original MySQL (aktivieren wenn DB freigeschalten):
            // connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/logging_db", "root", "");

        } catch (Exception e) {
            throw new RuntimeException("Datenbankverbindung fehlgeschlagen", e);
        }
    }

    private void initTable() {
        String sql = """
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
                )
                """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            System.out.println("H2: Tabelle 'decision' bereit.");
        } catch (Exception e) {
            throw new RuntimeException("Tabelle konnte nicht erstellt werden", e);
        }
    }

    public Connection getConnection() {
        return connection;
    }

}
