package ru.obiz.zeebe.play;

import com.google.common.util.concurrent.RateLimiter;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.response.Topology;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements AutoCloseable {
    private final Registry registry;
    private final Properties zeebeProperties;
    private ZeebeClient zeebeClient;
    private final AtomicInteger createdCount = new AtomicInteger(0);
    private String bpmnProcessId;
    private final AtomicBoolean isInitialised = new AtomicBoolean(false);

    public Producer(Registry registry, Properties zeebeProperties) {
        this.registry = registry;
        if (zeebeProperties==null) {
            this.zeebeProperties = new Properties();
            this.zeebeProperties.put("zeebe.client.broker.gateway-address", "127.0.0.1:26500");
            this.zeebeProperties.put("zeebe.client.security.plaintext", "true");
        } else {
            this.zeebeProperties = zeebeProperties;
        }
    }

    public void startFor(long count, long rate) {
        if(!isInitialised.get()) {
            throw new IllegalStateException("Producer is already initialised");
        }

        RateLimiter rateLimiter = RateLimiter.create(rate);

        new Thread(() -> {
            do {
                if(createdCount.get()%10==0) {
                    System.out.println("createdCount = " + createdCount.get());
                }
                rateLimiter.acquire();
                ProcessInstanceResult instanceResult = spawnNewInstance();
                //System.out.println("instanceResult.getProcessInstanceKey() = " + instanceResult.getProcessInstanceKey());
                registry.add(instanceResult.getProcessInstanceKey());
            } while (createdCount.incrementAndGet() < count);
        }).start();

    }

    public ProcessInstanceResult spawnNewInstance() {
        if(!isInitialised.get()) {
            throw new IllegalStateException("Producer is already initialised");
        }
        zeebeClient.newCreateInstanceCommand();
        return zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .withResult()
                .send()
                .join();
    }

    @Override
    public void close() throws Exception {
        zeebeClient.close();
    }

    public void init() {
        zeebeClient = io.camunda.zeebe.client.ZeebeClient.newClientBuilder()
                .withProperties(zeebeProperties)
                .build();

        //just test zeebeClient connection
        Topology topology = zeebeClient.newTopologyRequest().send().join();
        System.out.println("topology.getGatewayVersion() = " + topology.getGatewayVersion());
        isInitialised.set(true);
    }

    public boolean getIsInitialised() {
        return isInitialised.get();
    }

    public long deployBPMN(String resourcePath) {
        //deploy BPMN
        final DeploymentEvent deployment = zeebeClient.newDeployResourceCommand().addResourceFromClasspath(resourcePath).send().join();

        //get info about deployed BPMN
        Process process = deployment.getProcesses().getFirst();
        final int version = process.getVersion();
        String resourceName = process.getResourceName();
        this.bpmnProcessId = process.getBpmnProcessId();
        long processDefinitionKey = process.getProcessDefinitionKey();

        System.out.printf("Workflow deployed.\n\tID: %d\n\tBpmn id: %s\n\tResource name: %s\n\tVersion: %s\n",processDefinitionKey, bpmnProcessId, resourceName, version);
        return processDefinitionKey;
    }
}
