package ru.obiz.zeebe.play;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Monitor implements AutoCloseable{
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final Registry registry;
    private final HttpClient client;
    private final String SEARCH_REQUEST = "{\"query\":{\"processIds\":[%s],\"completed\":true,\"finished\":true,\"startDateAfter\":\"%s\"},\"sorting\":{\"sortBy\":\"startDate\",\"sortOrder\":\"desc\"},\"pageSize\":50}";
    private String searchAfter;
    private ScheduledThreadPoolExecutor executor;
    private final String operateApiUrl;
    private final String username;
    private final String password;
    private final Set<Long> monitoredProcesses = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean loggedIn = new AtomicBoolean(false);
    private final Lock loginLock = new ReentrantLock();

    public Monitor(Registry registry, String operateApiUrl, String username, String password) {
        this.registry = registry;
        this.operateApiUrl = operateApiUrl;
        this.username = username;
        this.password = password;

        executor = new ScheduledThreadPoolExecutor(1); //TODO parametrize max count of parallel monitors

        this.client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();

        updateSearchAfter();

        int period = 1;
        int initialDelay = 1;
        executor.scheduleAtFixedRate(
                () -> {
                    if(!loggedIn.get() || monitoredProcesses.isEmpty()) return;
                    String ids = monitoredProcesses.stream().map("\"%d\""::formatted).collect(Collectors.joining(","));
                    performCheckNewDataBringToOperate(ids);
                },
                initialDelay, period, TimeUnit.SECONDS
        );

    }

    private void loginIfAlreadyNot() throws IOException, InterruptedException, URISyntaxException {
        if (loggedIn.get()) return;
        try {
            loginLock.lock();
            //login to Operate here and save cookies
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(new URI(operateApiUrl + "/login?username=" + username + "&password=" + password))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            int statusCode = client.send(loginRequest, HttpResponse.BodyHandlers.discarding()).statusCode();
            if(statusCode >= 200 && statusCode < 300) {
                loggedIn.set(true);
            } else {
                System.out.println("Can't login to operate API!");
            }
        } finally {
            loginLock.unlock();
        }
    }

    public boolean start(long processDefinitionKey) throws IOException, InterruptedException, URISyntaxException {
        loginIfAlreadyNot();
        return monitoredProcesses.add(processDefinitionKey);
    }

    public boolean stop(long processDefinitionKey) {
        return monitoredProcesses.remove(processDefinitionKey);
    }

    public void updateSearchAfter() {
        searchAfter = LocalDateTime.now()
                .atOffset(ZoneOffset.ofTotalSeconds(TimeZone.getDefault().getRawOffset()/1000))
                .format(DATE_TIME_FORMATTER) + "+0300"; //таймзону которую генерит форматтер по Z (+03) оперейт не принимает
        System.out.println("searchAfter = " + searchAfter);
    }

    private void performCheckNewDataBringToOperate(String processDefinitionKeys) {
        try {

            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(new URI(operateApiUrl + "/process-instances"))
                    .header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(SEARCH_REQUEST.formatted(processDefinitionKeys, searchAfter)))
                    .build();

            HttpResponse<String> httpResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            //System.out.println("httpResponse = \n" + httpResponse.body());

            if(httpResponse.statusCode()==200) {
                Stream.of(httpResponse.body().split("\"id\":\"")) // делим JSON по началу нужного поля: "id":"
                        .skip(1) //строка до первого появления id в JSON
                        .map(s -> s.substring(0, s.indexOf('\"'))) // обрезаем всё что после нужного значения
                        .map(Long::parseLong)
                        .filter(registry::contains) //интересуют только те, что есть в реестре
                        .filter(registry::isNotFound) //из них берём только те, по которым ещё не видели данных в Operate
                        .forEach(registry::markFound); //отмечаем на них, что увидели инфу в Operate
                long remainNotFound = registry.remainNotFound();
                System.out.println("remainNotFound = " + remainNotFound);
            } else {
                System.out.println("ERROR on operate search: " + httpResponse.statusCode());
            }
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
        executor.shutdownNow();
    }
}
