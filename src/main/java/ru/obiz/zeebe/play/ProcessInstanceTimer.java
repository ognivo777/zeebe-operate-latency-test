package ru.obiz.zeebe.play;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;

import java.time.Duration;
import java.time.Instant;

public class ProcessInstanceTimer {
    private final Instant added;
    private Instant foundInOperate = null;
    private long waitTime;
    public ProcessInstanceTimer() {
        added  = Instant.now();
    }

    public Instant markFoundInOperate() {
        foundInOperate = Instant.now();
        waitTime = Duration.between(added, foundInOperate).toMillis();
        return foundInOperate;
    }

    public long getWaitTime() {
        return waitTime;
    }
}
