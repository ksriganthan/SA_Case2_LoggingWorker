package com.fhnw.sa_case2_loggingworker.DatabaseClient;

import com.fhnw.sa_case2_loggingworker.DTO.Decision;
import com.fhnw.sa_case2_loggingworker.Database.MySQLDatabase;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MySQLClient {

    private final MySQLDatabase database;

    public MySQLClient() {
        this.database = new MySQLDatabase();
    }

    public void insertDecision(Decision decision) {
        String sql = "INSERT INTO logTable (orderId, ruleId, userId, reason, address, carrier, shippingType, decisionType, country, weight) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {

            stmt.setString(1, decision.getBestellnummer());

            if (decision.getRuleId() != null) {
                stmt.setInt(2, decision.getRuleId());
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }

            stmt.setString(3, decision.getBenutzerId());
            stmt.setString(4, decision.getGrund());
            stmt.setString(5, decision.getLieferadresse());
            stmt.setString(6, decision.getSpediteur());
            stmt.setString(7, decision.getVersandart());
            stmt.setString(8, decision.getEntscheidungsart());
            stmt.setString(9, decision.getLand());
            stmt.setLong(10, decision.getGewicht());


            stmt.executeUpdate();

            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║           Decision erfolgreich gespeichert              ║");
            System.out.println("╠══════════════════════════════════════════════════════════╣");
            System.out.printf("║  Bestellnummer   : %-37s ║%n", decision.getBestellnummer());
            System.out.printf("║  Rule-ID         : %-37s ║%n", decision.getRuleId() != null ? decision.getRuleId() : "–");
            System.out.printf("║  Benutzer-ID     : %-37s ║%n", decision.getBenutzerId() != null ? decision.getBenutzerId() : "–");
            System.out.printf("║  Grund           : %-37s ║%n", decision.getGrund() != null ? decision.getGrund() : "–");
            System.out.printf("║  Lieferadresse   : %-37s ║%n", decision.getLieferadresse());
            System.out.printf("║  Spediteur       : %-37s ║%n", decision.getSpediteur());
            System.out.printf("║  Versandart      : %-37s ║%n", decision.getVersandart());
            System.out.printf("║  Entscheidungsart: %-37s ║%n", decision.getEntscheidungsart());
            System.out.printf("║  Land            : %-37s ║%n", decision.getLand());
            System.out.printf("║  Gewicht         : %-37s ║%n", decision.getGewicht() + " g");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Speichern der Decision in DB", e);
        }
    }
}