package com.rival.chatbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rival.chatbot.domain.NlpIntentEntity;
import com.rival.chatbot.dto.NlpIntentDTO;
import com.rival.chatbot.service.NlpTrainingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/nlp/training")
@Tag(name = "NLP Training", description = "Endpoints para o treinamento e gestão da rede neural local")
public class NlpTrainingController {

    private static final Logger log = LoggerFactory.getLogger(NlpTrainingController.class);
    private final NlpTrainingService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NlpTrainingController(NlpTrainingService service) {
        this.service = service;
    }

    private UUID resolveTenantId(UUID headerTenantId, String authHeader) {
        if (headerTenantId != null) {
            return headerTenantId;
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7).trim();
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                    JsonNode node = objectMapper.readTree(payloadJson);

                    for (String field : List.of("tenantId", "tenant_id", "userId", "id", "sub")) {
                        if (node.hasNonNull(field)) {
                            String val = node.get(field).asText().trim();
                            try {
                                return UUID.fromString(val);
                            } catch (IllegalArgumentException ignored) {
                                if ("sub".equals(field)) {
                                    return UUID.nameUUIDFromBytes(val.getBytes(StandardCharsets.UTF_8));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Não foi possível extrair tenantId do token JWT: {}", e.getMessage());
            }
        }
        return null;
    }

    @PostMapping("/intents")
    public ResponseEntity<?> createIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody NlpIntentDTO dto) {
        try {
            UUID resolvedTenant = resolveTenantId(tenantId, authHeader);
            NlpIntentEntity saved = service.createIntent(resolvedTenant, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        }
    }

    @Operation(summary = "Importar Intenções via JSON (Único ou Lote)", description = "Recebe uma lista de intenções JSON e salva em lote para o Tenant.")
    @PostMapping("/intents/bulk")
    public ResponseEntity<?> importBulkIntents(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody List<NlpIntentDTO> dtos) {
        try {
            UUID resolvedTenant = resolveTenantId(tenantId, authHeader);
            Map<String, Object> result = service.importBulkIntents(resolvedTenant, dtos);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Erro inesperado ao importar JSON em lote:", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", 500,
                    "error", "Internal Server Error",
                    "message", "Erro ao salvar lote no banco: " + e.getMessage()
            ));
        }
    }

    @Operation(summary = "Carregar arquivo JSON a partir de um link do Google Drive ou URL pública")
    @RequestMapping(value = "/fetch-json", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> fetchJsonFromUrl(
            @RequestParam(value = "url", required = false) String queryUrl,
            @RequestBody(required = false) Map<String, String> bodyPayload) {

        String rawUrl = (queryUrl != null && !queryUrl.isBlank())
                ? queryUrl
                : (bodyPayload != null ? bodyPayload.get("url") : "");

        if (rawUrl == null || rawUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "message", "Por favor, informe o link do Google Drive."
            ));
        }

        String cleanUrl = rawUrl.trim();
        try {
            while (cleanUrl.startsWith("http%3A") || cleanUrl.startsWith("https%3A")) {
                cleanUrl = java.net.URLDecoder.decode(cleanUrl, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception ignored) {}

        List<String> candidateUrls = convertGoogleDriveUrl(cleanUrl);

        // CookieManager é essencial para o Google Drive aceitar o redirecionamento de download público!
        java.net.CookieManager cookieManager = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .cookieHandler(cookieManager)
                .proxy(java.net.ProxySelector.getDefault())
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(8))
                .build();

        boolean sawGoogleLoginScreen = false;
        String lastError = "Não foi possível transferir o ficheiro.";

        for (String downloadUrl : candidateUrls) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept", "application/json, text/plain, */*")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String body = response.body() != null ? response.body().trim() : "";
                    if (body.startsWith("\uFEFF")) {
                        body = body.substring(1).trim();
                    }

                    // 1. Se o corpo já é um JSON puro (começa com '[' ou '{')
                    if ((body.startsWith("[") && body.endsWith("]")) || (body.startsWith("{") && body.endsWith("}"))) {
                        return ResponseEntity.ok(Map.of("content", body));
                    }

                    // 2. Verifica se é realmente uma página HTML de bloqueio/login
                    String lowerBody = body.toLowerCase();
                    if (lowerBody.startsWith("<!doctype") || lowerBody.startsWith("<html")) {
                        if (lowerBody.contains("servicelogin") || lowerBody.contains("accounts.google.com/v3/signin")) {
                            sawGoogleLoginScreen = true;
                        }
                        // Continua para testar a próxima URL da lista em vez de abortar logo na primeira!
                        continue;
                    }

                    // 3. Caso seja um texto com JSON dentro (ex: export de Google Docs com espaços/linhas antes)
                    int firstBracket = body.indexOf('[');
                    int lastBracket = body.lastIndexOf(']');
                    if (firstBracket != -1 && lastBracket > firstBracket) {
                        String extracted = body.substring(firstBracket, lastBracket + 1);
                        try {
                            objectMapper.readTree(extracted);
                            return ResponseEntity.ok(Map.of("content", extracted));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ex) {
                log.warn("Tentativa falhou para URL [{}]: {}", downloadUrl, ex.getMessage());
                lastError = ex.getMessage();
            }
        }

        if (sawGoogleLoginScreen) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "message", "O ficheiro no Google Drive ainda está privado ('Restrito'). No Google Drive: botão direito no ficheiro -> Partilhar -> Acesso geral -> 'Qualquer pessoa com o link'."
            ));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "Não foi possível ler o JSON deste link (" + lastError + "). Dica: clique no botão 'Abrir .JSON' ao lado para carregar o ficheiro direto do seu computador!"
        ));
    }

    private List<String> convertGoogleDriveUrl(String url) {
        Pattern fileIdPattern = Pattern.compile("/(?:file|document)/d/([a-zA-Z0-9_-]+)");
        Matcher matcher = fileIdPattern.matcher(url);
        String fileId = null;

        if (matcher.find()) {
            fileId = matcher.group(1);
        } else {
            Pattern paramIdPattern = Pattern.compile("[?&]id=([a-zA-Z0-9_-]+)");
            Matcher paramMatcher = paramIdPattern.matcher(url);
            if (url.contains("google.com") && paramMatcher.find()) {
                fileId = paramMatcher.group(1);
            }
        }

        if (fileId != null) {
            return List.of(
                    "https://drive.google.com/uc?export=download&id=" + fileId,
                    "https://drive.google.com/uc?id=" + fileId + "&export=download&confirm=t",
                    "https://drive.usercontent.google.com/download?id=" + fileId + "&export=download&confirm=t",
                    "https://docs.google.com/document/d/" + fileId + "/export?format=txt"
            );
        }

        return List.of(url);
    }

    @PutMapping("/intents/{id}")
    public ResponseEntity<?> updateIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody NlpIntentDTO dto) {
        try {
            UUID resolvedTenant = resolveTenantId(tenantId, authHeader);
            return service.updateIntent(resolvedTenant, id, dto)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", 400,
                    "error", "Bad Request",
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/intents")
    public ResponseEntity<Page<NlpIntentEntity>> listIntents(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID resolvedTenant = resolveTenantId(tenantId, authHeader);
        return ResponseEntity.ok(service.listIntents(resolvedTenant, search, page, size));
    }

    @DeleteMapping("/intents/{id}")
    public ResponseEntity<Void> deleteIntent(
            @RequestHeader(value = "X-Tenant-Id", required = false) UUID tenantId,
            @PathVariable UUID id) {
        service.deleteIntent(id);
        return ResponseEntity.noContent().build();
    }
}