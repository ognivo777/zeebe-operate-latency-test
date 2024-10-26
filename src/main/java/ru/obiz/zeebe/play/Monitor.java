package ru.obiz.zeebe.play;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Monitor {

    private final long processDefinitionKey;
    private final Registry registry;
    private final HttpClient client;
    private final String SEARCH_REQUEST = "{\"query\":{\"processIds\":[\"%s\"],\"completed\":true,\"finished\":true,\"startDateAfter\":\"%s\"},\"sorting\":{\"sortBy\":\"startDate\",\"sortOrder\":\"desc\"},\"pageSize\":50}";
    private final String searchAfter;
    private ScheduledThreadPoolExecutor executor;

    public Monitor(long processDefinitionKey, Registry registry, HttpClient client) {
        this.processDefinitionKey = processDefinitionKey;
        this.registry = registry;
        this.client = client;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

        searchAfter = LocalDateTime.now()
                .atOffset(ZoneOffset.ofTotalSeconds(TimeZone.getDefault().getRawOffset()/1000))
                .format(formatter) + "+0300"; //таймзону которую генерит форматтер по Z (+03) оперейт не принимает
        System.out.println("searchAfter = " + searchAfter);
    }

    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor
                .scheduleAtFixedRate(
                        this::check, 1, 1, TimeUnit.SECONDS
                );
    }

    private void check() {
        try {
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8081/api/process-instances"))
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(SEARCH_REQUEST.formatted(processDefinitionKey, searchAfter)))
                    .build();

            HttpResponse<String> httpResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            //System.out.println("httpResponse = \n" + httpResponse.body());

            if(httpResponse.statusCode()==200) {
                Stream.of(httpResponse.body().split("\"id\":\"")) // делим JSON по началу нужного поля: "id":"
                        .skip(1) //строка до первого появления id в JSON
                        .map(s -> s.substring(0, s.indexOf('\"'))) // обрезаем всё что после нужного значения
                        .map(Long::parseLong)
                        .filter(registry::contains)
                        .filter(registry::isNotFound)
                        .forEach(registry::markFound);
                long remainNotFound = registry.remainNotFound();
                System.out.println("remainNotFound = " + remainNotFound);
                if(remainNotFound ==0) {
                    executor.shutdown();
                    registry.printStats();
                    System.exit(0);
                }
            } else {
                System.out.println("ERROR on operate search: " + httpResponse.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
