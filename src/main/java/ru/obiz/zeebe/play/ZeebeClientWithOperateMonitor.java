package ru.obiz.zeebe.play;

import java.util.Properties;

public class ZeebeClientWithOperateMonitor {

    public static void main(String[] args) {
        Properties zeebeProperties = new Properties();
        zeebeProperties.put("zeebe.client.broker.gateway-address", "127.0.0.1:26500");
        zeebeProperties.put("zeebe.client.security.plaintext", "true");

        //TODO configurable parameters
        String operateApiUrl = "http://localhost:8081/api";
        String username = "demo";
        String password = "demo";

        Registry registry = new Registry();

        try (
             Monitor monitor = new Monitor(registry,operateApiUrl, username, password);
             Producer producer = new Producer(registry, zeebeProperties);
        ) {
            producer.init();
            long processDefinitionKey = producer.deployBPMN("simplest.bpmn");

//            AtomicInteger zeroWaitedPICounter = new AtomicInteger(0);
            monitor.start(processDefinitionKey);

            producer.startFor(500, 5);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}