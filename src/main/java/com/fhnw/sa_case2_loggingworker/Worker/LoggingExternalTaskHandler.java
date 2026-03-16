package com.fhnw.sa_case2_loggingworker.Worker;

import com.fhnw.sa_case2_loggingworker.Service.LoggingService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;

import java.util.HashMap;
import java.util.Objects;


public class LoggingExternalTaskHandler implements ExternalTaskHandler {

public LoggingService loggingService;

    public LoggingExternalTaskHandler(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        HashMap<String, Object> variables = new HashMap<>();

        Long ruleID = externalTask.getVariable("ruleId");
        String benutzerId = Objects.toString(externalTask.getVariable("userId"), null);
        String grund = Objects.toString(externalTask.getVariable("reason"), null);  // Enum

        String bestellnummer = externalTask.getVariable("orderId");
        String lieferadresse = externalTask.getVariable("destination");
        String spediteur = externalTask.getVariable("carrier");
        String versandart = externalTask.getVariable("shippingType");
        String entscheidungsart = externalTask.getVariable("decisionType");
        String land = Objects.toString(externalTask.getVariable("country"), null); // Enum, null-sicher
        Long gewicht = externalTask.getVariable("weight");

        variables.put("ruleId", ruleID);
        variables.put("benutzerId", benutzerId);
        variables.put("grund", grund);
        variables.put("bestellnummer", bestellnummer);
        variables.put("lieferadresse", lieferadresse);
        variables.put("spediteur", spediteur);
        variables.put("versandart", versandart);
        variables.put("entscheidungsart", entscheidungsart);
        variables.put("land", land);
        variables.put("gewicht", gewicht);

        try{
            loggingService.dataPreparation(variables);

            // Antwort zurück an Camunda
            externalTaskService.complete(externalTask, variables);
        }
        catch (WebApplicationException | ProcessingException e) {
            // Technischer Fehler -> Retry Strategie
            Integer retries = externalTask.getRetries();
            int remainingRetries = (retries == null) ? 3 : retries - 1;

            System.out.println("Technical error, remaining retries: " + remainingRetries);

            externalTaskService.handleFailure(
                    externalTask,
                    "REST not reachable / technical error",
                    e.getMessage(),
                    remainingRetries,
                    60_000L
            );
        }
        catch(Exception e){
            System.out.println("Fehler bei der Datenvorbereitung: " + e.getMessage());
            e.printStackTrace();
        }

    }
}