package com.fhnw.sa_case2_loggingworker.Worker;


import com.fhnw.sa_case2_loggingworker.DatabaseClient.MySQLClient;
import com.fhnw.sa_case2_loggingworker.Service.LoggingService;
import org.camunda.bpm.client.ExternalTaskClient;
import org.h2.tools.Server;

public class LoggingWorker {

    public static void main(String[] args) {

        // H2 Web-Konsole starten (vor DB-Verbindung!)
        try {
            Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8083").start();
            System.out.println("H2 Konsole: http://localhost:8083");
            System.out.println("JDBC URL:   jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1 | User: sa | PW: (leer)");
        } catch (Exception e) {
            System.out.println("H2 Konsole konnte nicht gestartet werden: " + e.getMessage());
        }

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://group6:p5TuHbjEadLeT6L@192.168.111.3:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        MySQLClient apiClient =
                new MySQLClient();

        LoggingService loggingService =
                new LoggingService(apiClient);


        client.subscribe("loggingDecision")
                .lockDuration(1000)
                .handler(new LoggingExternalTaskHandler(loggingService))
                .open();
    }
}