package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    int requestLimit;
    private final Semaphore semaphore;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Base64.Encoder encoder = Base64.getEncoder();
    private final ObjectMapper objectMapper;
    private final String STAND_URL = "https://ismp.crpt.ru/api/v3/";
    private final URI DOCUMENTS_CREATION_URI = URI.create(STAND_URL + "lk/documents/create");
    private final TimeUnit timeUnit;


    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.objectMapper = new ObjectMapper();
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        this.semaphore = new Semaphore(this.requestLimit);
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            semaphore.release(this.requestLimit - semaphore.availablePermits());
        }, 0, 1, this.timeUnit);
    }

    public <T extends Document> void createDocument(T document, String signature) {
        try {
            semaphore.acquire();
            RequestBody requestBody = getRequestBody(document, signature);
            String requestBodyJson = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(DOCUMENTS_CREATION_URI)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private <T extends Document> RequestBody getRequestBody(T document, String signature)
            throws JsonProcessingException {

        DocumentFormat documentFormat = document.getDocType().getDocumentFormat();
        return switch (documentFormat) {
            case MANUAL -> getRequestBodyWithJsonDoc(document, signature);
            default -> throw new UnrecognizedDocumentFormatException();
        };
    }

    private <T extends Document> RequestBody getRequestBodyWithJsonDoc(T document, String signature)
            throws JsonProcessingException {

        String jsonDocument = objectMapper.writeValueAsString(document);
        String encodedDocument = encoder.encodeToString(jsonDocument.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = encoder.encodeToString(signature.getBytes(StandardCharsets.UTF_8));
        return new RequestBody(
                document.getDocType().getDocumentFormat().toString(),
                encodedDocument,
                document.getDocType().toString(),
                encodedSignature);
    }

    @Getter
    public enum DocType {
        LP_INTRODUCE_GOODS(DocumentFormat.MANUAL);
        private final DocumentFormat documentFormat;

        DocType(DocumentFormat documentFormat) {
            this.documentFormat = documentFormat;
        }
    }

    public enum DocumentFormat {
        MANUAL
    }

    @Data
    @AllArgsConstructor
    public class RequestBody {
        @JsonProperty("document_format")
        private String documentFormat;
        @JsonProperty("product_document")
        private String productDocument;
        private String type;
        private String signature;
    }

    @Data
    @AllArgsConstructor
    public class Document {
        @JsonProperty("doc_type")
        private DocType docType;
        @JsonProperty("doc_id")
        private String docId;
        @JsonProperty("doc_status")
        private String docStatus;

        public Document() {
        }
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public class IntroduceGoodsDocument extends Document {
        private Description description;
        private boolean importRequest;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("participant_inn")
        private String participantInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private Date productionDate;
        @JsonProperty("production_type")
        private String productionType;
        private List<Product> products;
        @JsonProperty("reg_date")
        private Date regDate;
        @JsonProperty("reg_number")
        private String regNumber;
    }

    @Data
    class Description {
        private String participantInn;
    }

    @Data
    class Product {
        @JsonProperty("certificate_document")
        private String certificateDocument;
        @JsonProperty("certificate_document_date")
        private Date certificateDocumentDate;
        @JsonProperty("certificate_document_number")
        private String certificateDocumentNumber;
        @JsonProperty("owner_inn")
        private String ownerInn;
        @JsonProperty("producer_inn")
        private String producerInn;
        @JsonProperty("production_date")
        private Date productionDate;
        @JsonProperty("tnved_code")
        private String tnvedCode;
        @JsonProperty("uit_code")
        private String uitCode;
        @JsonProperty("uitu_code")
        private String uituCode;
    }

    private class UnrecognizedDocumentFormatException extends RuntimeException {
        public UnrecognizedDocumentFormatException() {
            super("Данный формат документа не поддерживается");
        }
    }
}