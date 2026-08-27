package com.dpom.agent.web.diagnosisevent;

import com.dpom.agent.common.diagnosisevent.DeliveryAcknowledgement;
import com.dpom.agent.common.diagnosisevent.DeliveryOutcome;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryPort;
import com.dpom.agent.common.diagnosisevent.DiagnosisEventDeliveryRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Set;

/**
 * 使用 HTTPS 和 HMAC 投递不可变 Diagnosis Event 的 RestClient 适配器。
 */
public final class DiagnosisEventHttpDeliveryAdapter implements DiagnosisEventDeliveryPort {

    private static final Set<String> ACK_FIELDS = Set.of("outcome", "errorCode");
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final URI destination;
    private final byte[] secret;
    private final Clock clock;
    private final int maxAcknowledgementBytes;

    /** 创建有界投递适配器。 */
    public DiagnosisEventHttpDeliveryAdapter(RestClient client, ObjectMapper objectMapper, URI destination,
                                             byte[] secret, Clock clock, int maxAcknowledgementBytes) {
        validate(destination, secret, maxAcknowledgementBytes);
        this.client = client;
        this.objectMapper = objectMapper;
        this.destination = destination;
        this.secret = secret.clone();
        this.clock = clock;
        this.maxAcknowledgementBytes = maxAcknowledgementBytes;
    }

    @Override
    public DeliveryAcknowledgement deliver(DiagnosisEventDeliveryRequest request) {
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        String signature = signature(timestamp, request.canonicalSha256());
        return client.post().uri(destination)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-DPOM-Event-Id", request.eventId())
                .header("Idempotency-Key", request.idempotencyKey())
                .header("X-DPOM-Content-SHA256", request.canonicalSha256())
                .header("X-DPOM-Timestamp", timestamp)
                .header("X-DPOM-Signature", "sha256=" + signature)
                .body(request.canonicalJson())
                .exchange((httpRequest, response) -> mapResponse(response));
    }

    private DeliveryAcknowledgement mapResponse(org.springframework.http.client.ClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        if (status == 408 || status == 429 || status >= 500) {
            return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "HTTP_" + status);
        }
        if (status == 409) {
            return acknowledgement(DeliveryOutcome.IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_CONFLICT");
        }
        if (status >= 400) {
            return acknowledgement(DeliveryOutcome.PERMANENT_REJECTION, "HTTP_" + status);
        }
        if (status < 200 || status >= 300) {
            return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "HTTP_" + status);
        }
        byte[] body = response.getBody().readNBytes(maxAcknowledgementBytes + 1);
        if (body.length > maxAcknowledgementBytes) {
            return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "ACKNOWLEDGEMENT_TOO_LARGE");
        }
        return parseAcknowledgement(body);
    }

    private DeliveryAcknowledgement parseAcknowledgement(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject() || !validFields(root)) {
                return malformed();
            }
            JsonNode outcomeNode = root.get("outcome");
            if (outcomeNode == null || !outcomeNode.isTextual()) {
                return malformed();
            }
            DeliveryOutcome outcome = DeliveryOutcome.valueOf(outcomeNode.textValue());
            JsonNode errorNode = root.get("errorCode");
            String errorCode = errorNode == null || errorNode.isNull() ? null : errorNode.textValue();
            if (errorNode != null && !errorNode.isNull() && !errorNode.isTextual()) {
                return malformed();
            }
            return acknowledgement(outcome, errorCode);
        } catch (RuntimeException | IOException exception) {
            return malformed();
        }
    }

    private boolean validFields(JsonNode root) {
        var names = root.fieldNames();
        while (names.hasNext()) {
            if (!ACK_FIELDS.contains(names.next())) {
                return false;
            }
        }
        return true;
    }

    private DeliveryAcknowledgement malformed() {
        return acknowledgement(DeliveryOutcome.RETRYABLE_FAILURE, "MALFORMED_ACKNOWLEDGEMENT");
    }

    private DeliveryAcknowledgement acknowledgement(DeliveryOutcome outcome, String errorCode) {
        return new DeliveryAcknowledgement(outcome, errorCode);
    }

    private String signature(String timestamp, String bodyHash) {
        try {
            String path = destination.getRawPath().isEmpty() ? "/" : destination.getRawPath();
            String input = timestamp + '\n' + "POST" + '\n' + path + '\n' + bodyHash;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("DELIVERY_SIGNATURE_FAILURE");
        }
    }

    private static void validate(URI destination, byte[] secret, int maxBytes) {
        if (destination == null || !"https".equalsIgnoreCase(destination.getScheme())
                || destination.getHost() == null || destination.getRawUserInfo() != null
                || destination.getRawQuery() != null || destination.getRawFragment() != null) {
            throw new IllegalArgumentException("INVALID_HTTPS_DESTINATION");
        }
        if (secret == null || secret.length < 32) {
            throw new IllegalArgumentException("WEAK_DELIVERY_HMAC_SECRET");
        }
        if (maxBytes < 1 || maxBytes > 1_048_576) {
            throw new IllegalArgumentException("INVALID_ACKNOWLEDGEMENT_LIMIT");
        }
    }
}
