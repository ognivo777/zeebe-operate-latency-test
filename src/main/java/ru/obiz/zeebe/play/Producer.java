package ru.obiz.zeebe.play;

import com.google.common.util.concurrent.RateLimiter;
import io.camunda.zeebe.client.ZeebeClient;

import java.util.concurrent.atomic.AtomicInteger;

public class Producer {
    private final RateLimiter rateLimiter;
    private final Registry registry;
    private final ZeebeClient zeebeClient;
    private long rate;
    private AtomicInteger createdCount = new AtomicInteger(0);
    private String bpmnProcessId;

    public Producer(long rate, Registry registry, ZeebeClient zeebeClient, String bpmnProcessId) {
        this.rate = rate;
        rateLimiter = RateLimiter.create(rate);
        this.registry = registry;
        this.zeebeClient = zeebeClient;
        this.bpmnProcessId = bpmnProcessId;
    }

    public void startFor(long count) {
        new Thread(() -> {
            while (createdCount.get()<count) {
                rateLimiter.acquire();
                zeebeClient.newCreateInstanceCommand();
                registry.add(zeebeClient.newCreateInstanceCommand()
                        .bpmnProcessId(bpmnProcessId)
                        .latestVersion()
                        .withResult()
                        .send()
                        .join()
                );
            }
        }).start();
    }
}