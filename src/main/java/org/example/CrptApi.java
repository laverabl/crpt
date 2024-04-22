package org.example;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class CrptApi {

    private final HttpClient httpClient;

    private final Gson gson;

    private final BlockingQueue<Object> tokenBucket;

    private final int requestLimit;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
        this.requestLimit = requestLimit;
        this.tokenBucket = new LinkedBlockingDeque<>(requestLimit);

        for (int i = 0; i < requestLimit; i++) {
            tokenBucket.offer(new Object());
        }

        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::replenishToken, 0, timeUnit.toMillis(1), TimeUnit.MILLISECONDS);
    }

    private void replenishToken() {
        synchronized (tokenBucket) {
            if (tokenBucket.size() < requestLimit) {
                tokenBucket.offer(new Object());
            }
        }
    }

    public void createDocument(Document document, String signature) {

        Object token = tokenBucket.poll();

        if (token == null) {
            System.err.println("Failed to create document. Request limit eexeceeded.");
            return;
        }

        try {
            String requestBody = gson.toJson(document);

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create")).header("Content-Type", "application/json").header("Authorization", "Bearer " + signature).POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Document created successfully.");
            } else {
                System.err.println("Failed to create document. Status code: " + response.statusCode());
            }
            tokenBucket.offer(token);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            tokenBucket.offer(new Object());
        }
    }


    public static class Document {
        private final String participantInn;
        private final String docId;

        public Document(String participantInn, String docId) {
            this.participantInn = participantInn;
            this.docId = docId;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public String getDocId() {
            return docId;
        }
    }

    public static void main(String[] args) {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);

        CrptApi.Document document = new CrptApi.Document("1234567890", "123");
        String signature = "example_signature";

        crptApi.createDocument(document, signature);
    }
}
