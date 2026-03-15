package com.fhnw.sa_case2_loggingworker.Service;


import com.fhnw.sa_case2_loggingworker.DTO.Decision;
import com.fhnw.sa_case2_loggingworker.DatabaseClient.MySQLClient;

import java.util.HashMap;


public class LoggingService {
    public MySQLClient sqlClient;

    public LoggingService(MySQLClient mySQLClient) {
    this.sqlClient = mySQLClient;
    }


    public void dataPreparation(HashMap<String, Object> variables) {

        // Mapping -> Request DTO für Spedition
        Decision decision = new Decision();

        try {
            Long ruleIdRaw = (Long) variables.get("ruleId");
            decision.setRuleId(ruleIdRaw != null ? Math.toIntExact(ruleIdRaw) : null);
            decision.setBenutzerId((String) variables.get("benutzerId"));
            decision.setGrund((String) variables.get("grund"));
            decision.setBestellnummer((String) variables.get("bestellnummer"));
            decision.setLieferadresse((String) variables.get("lieferadresse"));
            decision.setSpediteur((String) variables.get("spediteur"));
            decision.setVersandart((String) variables.get("versandart"));
            decision.setEntscheidungsart((String) variables.get("entscheidungsart"));
            decision.setLand((String) variables.get("land"));
            decision.setGewicht((Long) variables.get("gewicht"));


            // Aufruf DB
            sqlClient.insertDecision(decision);

        }
        catch (Exception e) {
            throw new RuntimeException("Datenaufbereitung fehlgeschlagen", e);
        }


    }
}