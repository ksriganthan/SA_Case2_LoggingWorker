package com.fhnw.sa_case2_loggingworker.DatabaseClient;

import com.fhnw.sa_case2_loggingworker.DTO.Decision;
import com.fhnw.sa_case2_loggingworker.Database.MySQLDatabase;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySQLClient {

    private final MySQLDatabase database;

    public MySQLClient() {
        this.database = new MySQLDatabase();
    }

    public void insertDecision(Decision decision) {
        String sql = "INSERT INTO decision (bestellnummer, rule_id, benutzer_id, grund, lieferadresse, spediteur, versandart, entscheidungsart, land, gewicht) "
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

            if (decision.getGewicht() != null) {
                stmt.setLong(10, decision.getGewicht());
            } else {
                stmt.setNull(10, java.sql.Types.BIGINT);
            }

            stmt.executeUpdate();
            System.out.println("Decision erfolgreich in DB gespeichert: " + decision.getBestellnummer());

        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Speichern der Decision in DB", e);
        }
    }

    public Decision findByBestellnummer(String bestellnummer) {
        String sql = "SELECT bestellnummer, rule_id, benutzer_id, grund, lieferadresse, spediteur, versandart, entscheidungsart, land, gewicht "
                   + "FROM decision WHERE bestellnummer = ?";

        try (PreparedStatement stmt = database.getConnection().prepareStatement(sql)) {
            stmt.setString(1, bestellnummer);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Decision d = new Decision();
                    d.setBestellnummer(rs.getString("bestellnummer"));

                    int ruleId = rs.getInt("rule_id");
                    d.setRuleId(rs.wasNull() ? null : ruleId);

                    d.setBenutzerId(rs.getString("benutzer_id"));
                    d.setGrund(rs.getString("grund"));
                    d.setLieferadresse(rs.getString("lieferadresse"));
                    d.setSpediteur(rs.getString("spediteur"));
                    d.setVersandart(rs.getString("versandart"));
                    d.setEntscheidungsart(rs.getString("entscheidungsart"));
                    d.setLand(rs.getString("land"));

                    long gewicht = rs.getLong("gewicht");
                    d.setGewicht(rs.wasNull() ? null : gewicht);

                    return d;
                }
            }
            return null;

        } catch (SQLException e) {
            throw new RuntimeException("Fehler beim Lesen der Decision aus DB", e);
        }
    }
}