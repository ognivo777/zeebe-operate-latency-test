package ru.obiz.zeebe.play;

import com.google.common.net.MediaType;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class OperateMonitorService {

    private static String operateApiUrl;
    private static String operateUsername;
    private static String operatePassword;
    private final Registry registry;

    public OperateMonitorService(int port) {
        registry = new Registry();

        try(Monitor operateMonitor = new Monitor(registry, operateApiUrl, operateUsername, operatePassword)) {

            Map<Long, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

            //Start api
            Undertow.Builder builder = Undertow.builder();
            Undertow undertow = builder
                    .addHttpListener(port, "localhost")
                    .setHandler(Handlers.path()
                            .addPrefixPath("/api", Handlers.routing()
                                    .put("/keys/{nextProcessInstanceKey}", this::addKey)
                                    .put("/monitor/{processDefinitionKey}/stop", exchange -> {
                                        long processDefinitionKey = Long.parseLong(exchange.getQueryParameters().get("processDefinitionKey").getLast());
                                        stopFlags.get(processDefinitionKey).set(true);
                                    })
                                    .put("/monitor/{processDefinitionKey}/start", exchange -> {
                                        long processDefinitionKey = Long.parseLong(exchange.getQueryParameters().get("processDefinitionKey").getLast());
                                        AtomicBoolean stopFlag = new AtomicBoolean(false);
                                        operateMonitor.start(processDefinitionKey, r -> stopFlag.get());
                                        stopFlags.put(processDefinitionKey, stopFlag);
                                    })
                                    .get("/stats/remaining", this::remainingStats)
                                    .setFallbackHandler(this::fallback)
                            )
                    )
                    .build();

            System.out.println("Undertow started");
            undertow.start();


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void remainingStats(HttpServerExchange exchange) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        exchange.getResponseSender().send(registry.printStats());
    }

    private void fallback(HttpServerExchange exchange) {
        //Todo return error
    }

    private void addKey(HttpServerExchange exchange) {
        String piKey = exchange.getQueryParameters().get("name").getLast();
        registry.add(Long.parseLong(piKey));
    }

    public static void main(String[] args) {
        //Todo make configurable
        int port = 8088;
        new OperateMonitorService(port);
        operateApiUrl = "http://localhost:8081/api";
        operateUsername = "demo";
        operatePassword = "demo";
    }

}
