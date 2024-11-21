package ru.obiz.zeebe.play;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.Topology;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ZeebeClientWithOperateMonitor {

    public static void main(String[] args) {
        Properties zeebeProperties = new Properties();
        zeebeProperties.put("zeebe.client.broker.gateway-address", "127.0.0.1:26500");
        zeebeProperties.put("zeebe.client.security.plaintext", "true");

        //TODO configurable parameters
        String operateApiUrl = "http://localhost:8081/api";
        String username = "demo";
        String password = "demo";
        int zeroWaitedPICountToStop = 5;


        Registry registry = new Registry();

        try (
             Monitor monitor = new Monitor(registry,operateApiUrl, username, password);
             Producer producer = new Producer(registry, zeebeProperties);
        ) {

            producer.init();
            long processDefinitionKey = producer.deployBPMN("simplest.bpmn");

            AtomicInteger zeroWaitedPICounter = new AtomicInteger(0);
            monitor.start(processDefinitionKey,
                    r -> {
                        System.out.println(r.printStats());
                        return r.remainNotFound() == 0 && zeroWaitedPICounter.incrementAndGet() >= zeroWaitedPICountToStop;
                    }
            );

            producer.startFor(500, 5);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}