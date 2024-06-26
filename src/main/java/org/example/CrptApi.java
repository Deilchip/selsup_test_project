package org.example;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    @Getter
    @Setter
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @Getter
    @Setter
    public static class Description {
        private String participantInn;
    }

    @Getter
    @Setter
    public static class MainDocument {
        private Description description;
        private String docId;
        private String docStatus;
        private String docType;
        private boolean importRequest;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private Date productionDate;
        private String productionType;
        private List<Product> products;
        private Date regDate;
        private String regNumber;
    }

    private final OkHttpClient client;
    private final String url = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Gson gson = new Gson();
    private final int requestLimit;
    private final BlockingQueue<Long> requestTimes;
    private final long intervalMillis;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.client = new OkHttpClient();
        this.requestLimit = requestLimit;
        this.intervalMillis = timeUnit.toMillis(1);
        this.requestTimes = new ArrayBlockingQueue<>(requestLimit);
    }

    public void createDocument(Object document, String signature) throws InterruptedException, IOException {
        synchronized (requestTimes) {
            while (requestTimes.size() >= requestLimit) {
                Long oldestRequestTime = requestTimes.poll();
                if (oldestRequestTime != null && System.currentTimeMillis() - oldestRequestTime < intervalMillis) {
                    requestTimes.wait(intervalMillis - (System.currentTimeMillis() - oldestRequestTime));
                }
            }
            requestTimes.add(System.currentTimeMillis());
        }

        RequestBody body = RequestBody.create(
                gson.toJson(document),
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Signature", signature)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 10);
        Object document = new MainDocument();
        String signature = "some text";
        api.createDocument(document, signature);
    }
}