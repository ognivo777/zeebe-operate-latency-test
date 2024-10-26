package ru.obiz.zeebe.play;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;

import java.sql.SQLOutput;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {
    private final ConcurrentHashMap<Long,ProcessInstanceWrapper> instances = new ConcurrentHashMap<>();
    private Set<Long> foundInstances = Collections.synchronizedSet(new HashSet<>());


    public void add(ProcessInstanceResult instance) {
        instances.put(instance.getProcessInstanceKey(), new ProcessInstanceWrapper(instance));
        //TODO
    };

    public boolean contains(Long id) {
        return instances.containsKey(id);
    }

    public boolean isNotFound(Long id) {
        return !foundInstances.contains(id);
    }

    public void markFound(Long id) {
        //TODO save and print some times average delay of last 50 PI-s
        foundInstances.add(id);
        instances.get(id).markFoundInOperate();
    }

    public long remainNotFound() {
        if(instances.isEmpty()) {
            return -1; //prevent stop monitor before we accept any instances
        }
        return instances.size() - foundInstances.size();
    }

    public void printStats() {
        long totalWait = 0;
        for (ProcessInstanceWrapper value : instances.values()) {
                totalWait+=value.getWaitTime();
        }
        System.out.println("totalWait/instances.size() = " + totalWait / (0d + instances.size()) );
    }
}
