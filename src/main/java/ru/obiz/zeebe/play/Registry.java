package ru.obiz.zeebe.play;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {
    private final ConcurrentHashMap<Long, ProcessInstanceTimer> instances = new ConcurrentHashMap<>();
    private final Set<Long> foundInstanceIds = Collections.synchronizedSet(new HashSet<>());


    public boolean add(long instanceKey) {
        if(instances.containsKey(instanceKey)) {
            return false;
        }
        instances.put(instanceKey, new ProcessInstanceTimer());
        return true;
    };

    public boolean contains(Long id) {
        return instances.containsKey(id);
    }

    public boolean isNotFound(Long id) {
        return !foundInstanceIds.contains(id);
    }

    public void markFound(Long id) {
        //TODO save and print some times average delay of last 50 PI-s
        foundInstanceIds.add(id);
        instances.get(id).markFoundInOperate();
    }

    public long remainNotFound() {
        if(instances.isEmpty()) {
            return -1; //prevent stop monitor before we accept any instances
        }
        return instances.size() - foundInstanceIds.size();
    }

    public String printStats() {
        long totalWait = 0;
        for (ProcessInstanceTimer value : instances.values()) {
                totalWait+=value.getWaitTime();
        }
        if(totalWait==0) {
            return "No data found";
        } else {
            return "totalWait/instances.size() = " + totalWait / (0d + instances.size());
        }
    }

    public long size() {
        return instances.size();
    }

    public ProcessInstanceTimer remove(long id) {
        return instances.remove(id);
    }

    public void removeAll() {
        instances.clear();
    }
}
