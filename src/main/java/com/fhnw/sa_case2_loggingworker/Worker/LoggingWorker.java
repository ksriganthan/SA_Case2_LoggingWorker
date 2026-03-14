package com.fhnw.sa_case2_loggingworker.Worker;


import com.fhnw.sa_case2_loggingworker.DatabaseClient.MySQLClient;
import com.fhnw.sa_case2_loggingworker.Service.LoggingService;
import org.camunda.bpm.client.ExternalTaskClient;

public class LoggingWorker {

    public static void main(String[] args) {

        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://group6:p5TuHbjEadLeT6L@192.168.111.3:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        MySQLClient apiClient =
                new MySQLClient();


        client.subscribe("shippingDecision")
                .lockDuration(1000)
                .handler(new LoggingExternalTaskHandler())
                .open();
    }
}