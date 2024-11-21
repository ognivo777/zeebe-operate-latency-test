package ru.obiz.zeebe.play;

import com.google.common.net.MediaType;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public class OperateMonitorService {

    private static String operateApiUrl;
    private static String operateUsername;
    private static String operatePassword;
    private final Registry registry;

    public OperateMonitorService(int port) {
        registry = new Registry();


        try(
                Monitor operateMonitor = new Monitor(registry, operateApiUrl, operateUsername, operatePassword);
                Producer producer = new Producer(registry, null);) {

            Map<Long, AtomicBoolean> stopFlags = new ConcurrentHashMap<>();

            //Start api
            Undertow.Builder builder = Undertow.builder();
            Undertow undertow = builder
                    .addHttpListener(port, "localhost")
                    .setHandler(Handlers.path()
                            .addPrefixPath("/api", Handlers.routing()
                                    .get("/version", exchange -> {
                                        String version="1.0";
                                        exchange.getResponseSender().send(asJSON("version", version));
                                    })
                                    .post("/keys/{nextProcessInstanceKey}", this::addKey)
                                    .delete("/monitor/{processDefinitionKey}", exchange -> {
                                        long processDefinitionKey = Long.parseLong(exchange.getQueryParameters().get("processDefinitionKey").getLast());
                                        if (stopFlags.containsKey(processDefinitionKey)) {
                                            AtomicBoolean stopFlag = stopFlags.get(processDefinitionKey);
                                            if(!stopFlag.get()) {
                                                stopFlag.set(true);
                                                jsonResponse(exchange,"status", "Stop flag is set");
                                            } else {
                                                jsonErrorResponse(exchange, "Already sopped", StatusCodes.PRECONDITION_FAILED);
                                            }
                                        } else {
                                            jsonErrorResponse(exchange, "Not found", StatusCodes.NOT_FOUND);
                                        }
                                    })
                                    .post("/monitor/{processDefinitionKey}", exchange -> {
                                        long processDefinitionKey = Long.parseLong(exchange.getQueryParameters().get("processDefinitionKey").getLast());
                                        if(stopFlags.containsKey(processDefinitionKey)) {
                                            jsonErrorResponse(exchange, "Already started", StatusCodes.CONFLICT);
                                        } else {
                                            AtomicBoolean stopFlag = new AtomicBoolean(false);
                                            operateMonitor.start(processDefinitionKey, r -> stopFlag.get());
                                            stopFlags.put(processDefinitionKey, stopFlag);
                                            exchange.getResponseSender().send("{\"status\": \"Stared\"}");
                                        }
                                    })
                                    .get("/stats/remaining", e -> jsonResponse(e, "stats", registry.printStats()))
                                    .post("/zeebe/init", exchange -> {
                                        if(!producer.getIsInitialised()) {
                                            producer.init();
                                            jsonResponse(exchange, "bpmn_id", producer.deployBPMN("simplest.bpmn"));
                                        } else {
                                            jsonErrorResponse(exchange, "Already initialised", StatusCodes.CONFLICT);
                                        }
                                    })
                                    .post("/zeebe/spawn/{processDefinitionKey}", exchange -> {
                                        if(producer.getIsInitialised()) {
                                            ProcessInstanceResult processInstanceResult = producer.spawnNewInstance();
                                            jsonResponse(exchange, "instance_id", processInstanceResult.getProcessInstanceKey());
                                        } else {
                                            exchange.setStatusCode(StatusCodes.PRECONDITION_REQUIRED);
                                            jsonErrorResponse(exchange, "Zeebe producer is not initialised", StatusCodes.PRECONDITION_REQUIRED);
                                        }
                                    })
                                    .setFallbackHandler(this::fallback)
                            )
                    )
                    .build();

            System.out.println("Undertow started");

            undertow.start();

            new Semaphore(0).acquire();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private static void jsonResponse(HttpServerExchange exchange, String name, String data) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        exchange.getResponseSender().send(asJSON(name, data));
    }

    private static void jsonErrorResponse(HttpServerExchange exchange, String data, int errorCode) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        exchange.setStatusCode(errorCode);
        exchange.getResponseSender().send(asJSON("error", data));
    }

    private static void jsonResponse(HttpServerExchange exchange, String name, long data) {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8.toString());
        exchange.getResponseSender().send(asJSON(name, data));
    }

    private void fallback(HttpServerExchange exchange) {
        //Todo return error
    }

    private void addKey(HttpServerExchange exchange) {
        String piKey = exchange.getQueryParameters().get("nextProcessInstanceKey").getLast();
        registry.add(Long.parseLong(piKey));
        jsonResponse(exchange, "size", registry.size());
    }

    public static void main(String[] args) {
        //Todo make configurable
        int port = 8088;
        operateApiUrl = "http://localhost:8081/api";
        operateUsername = "demo";
        operatePassword = "demo";

        new OperateMonitorService(port);
    }

    private static String asJSON(String name, String value) {
        return "{\"%s\":\"%s\"}".formatted(name, value);
    }

    private static String asJSON(String name, long value) {
        return "{\"%s\":%d}".formatted(name, value);
    }

}
