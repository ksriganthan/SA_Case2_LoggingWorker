package com.fhnw.sa_case2_loggingworker.Worker;

import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;


public class LoggingExternalTaskHandler implements ExternalTaskHandler {


    public LoggingExternalTaskHandler() {
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        // Was noch fehlt: Timestamp vom DB-Eintrag

        // Könnte Null sein -> Hier ist eine Handling nötig!
        Integer ruleID = externalTask.getVariable("ruleid");
        String benutzerId = externalTask.getVariable("userId");
        String grund = externalTask.getVariable("reason").toString(); // Enum


        String bestellnummer = externalTask.getVariable("orderId");
        String lieferadresse = externalTask.getVariable("destination");
        String spediteur = externalTask.getVariable("carrier");
        String versandart = externalTask.getVariable("shippingType");
        String entscheidungsart = externalTask.getVariable("decisionType");
        String land = externalTask.getVariable("country").toString(); // Enum
        Long gewicht = externalTask.getVariable("weight");

    }
}