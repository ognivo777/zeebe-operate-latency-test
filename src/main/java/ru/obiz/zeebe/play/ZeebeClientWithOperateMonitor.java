package ru.obiz.zeebe.play;

import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.Topology;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ZeebeClientWithOperateMonitor {

    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.put("zeebe.client.broker.gateway-address", "127.0.0.1:26500");
        properties.put("zeebe.client.security.plaintext", "true");

        //TODO configurable parameters
        String operateApiUrl = "http://localhost:8081/api";
        String username = "demo";
        String password = "demo";
        int zeroWaitedPICountToStop = 5;


        Registry registry = new Registry();

        try (io.camunda.zeebe.client.ZeebeClient zeebeClient = io.camunda.zeebe.client.ZeebeClient.newClientBuilder()
                .withProperties(properties)
                .build();
             Monitor monitor = new Monitor(registry,operateApiUrl, username, password);
        ) {

            //just test zeebeClient connection
            Topology topology = zeebeClient.newTopologyRequest().send().join();
            System.out.println("topology.getGatewayVersion() = " + topology.getGatewayVersion());

            //deploy BPMN
            final DeploymentEvent deployment = zeebeClient.newDeployResourceCommand().addResourceFromClasspath("simplest.bpmn").send().join();

            //get info about deployed BPMN
            Process process = deployment.getProcesses().getFirst();
            final int version = process.getVersion();
            String resourceName = process.getResourceName();
            String bpmnProcessId = process.getBpmnProcessId();
            long processDefinitionKey = process.getProcessDefinitionKey();

            System.out.printf("Workflow deployed.\n\tID: %d\n\tBpmn id: %s\n\tResource name: %s\n\tVersion: %s\n",processDefinitionKey, bpmnProcessId, resourceName, version);

            AtomicInteger zeroWaitedPICounter = new AtomicInteger(0);
            monitor.start(processDefinitionKey,
                    r -> r.remainNotFound()==0 && zeroWaitedPICounter.incrementAndGet() >= zeroWaitedPICountToStop
            );

            new Producer(5, registry, zeebeClient, bpmnProcessId).startFor(500);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
}