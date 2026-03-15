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
                CREATE TABLE IF NOT EXISTS logTable (
                    logId       INT     AUTO_INCREMENT PRIMARY KEY,
                    orderId    VARCHAR(255)  NOT NULL,
                    ruleId          INT           NULL,
                    userId     VARCHAR(255)  NULL,
                    reason            VARCHAR(255)  NULL,
                    address    VARCHAR(255)  NOT NULL,
                    carrier        VARCHAR(255)  NOT NULL,
                    shippingType       VARCHAR(255)  NOT NULL,
                    decisionType VARCHAR(255)  NOT NULL,
                    country             VARCHAR(255)  NOT NULL,
                    weight          BIGINT        NOT NULL,
                    created_at      TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
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
