package ru.obiz.zeebe.play;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import io.camunda.zeebe.client.api.response.Topology;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        Properties properties = new Properties();
        properties.put("zeebe.client.broker.gateway-address", "127.0.0.1:26500");
        properties.put("zeebe.client.security.plaintext", "true");

        try (ZeebeClient client = ZeebeClient.newClientBuilder()
                .withProperties(properties)
                .build();
             HttpClient httpClient = HttpClient.newBuilder()
                     .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                     .build()
        ) {

            //login to Operate here and save cookies
            HttpRequest loginRequest = HttpRequest.newBuilder()
                    .uri(new URI("http://localhost:8081/api/login?username=demo&password=demo"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.send(loginRequest, HttpResponse.BodyHandlers.discarding());

            //just test client connection
            Topology topology = client.newTopologyRequest().send().join();
            System.out.println("topology.getGatewayVersion() = " + topology.getGatewayVersion());

            //deploy BPMN
            final DeploymentEvent deployment = client.newDeployResourceCommand().addResourceFromClasspath("simplest.bpmn").send().join();

            //get info about deployed BPMN
            Process process = deployment.getProcesses().getFirst();
            final int version = process.getVersion();
            String resourceName = process.getResourceName();
            String bpmnProcessId = process.getBpmnProcessId();
            System.out.printf("Workflow deployed.\n\tBpmn id: %s\n\tResource name: %s\n\tVersion: %s\n",bpmnProcessId, resourceName, version);

//          Create process instance and wait result
            ProcessInstanceResult instanceResult = client.newCreateInstanceCommand()
                    .bpmnProcessId(bpmnProcessId)
                    .latestVersion()
                    .withResult()
                    .send()
                    .join();

            Instant resultOfPI = Instant.now();
            String processInstanceKey = Long.toString(instanceResult.getProcessInstanceKey());
            System.out.println("processInstanceKey = " + processInstanceKey);

            while (true) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("http://localhost:8081/v1/process-instances/"+processInstanceKey))
                        .GET()
                        .build();
//                HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
//                String body = httpResponse.body();
//                System.out.println("httpResponse.body() = " + body);
                HttpResponse<Void> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                if(httpResponse.statusCode()==200)
                    break;
                Thread.sleep(50);
            }

            Instant foundInOperate = Instant.now();
            System.out.printf("Time waited: %.2f seconds\n",Duration.between(resultOfPI, foundInOperate).toMillis()/1000d);

        } catch (InterruptedException | URISyntaxException | IOException e) {
            throw new RuntimeException(e);
        }

    }
}