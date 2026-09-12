package com.rival.chatbot.service.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

@Service
public class WhatsAppSenderService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSenderService.class);
    private final RestTemplate restTemplate;

    public WhatsAppSenderService() {
        this.restTemplate = createUnsafeRestTemplate();
    }

    public void sendMessage(String phoneNumberId, String apiToken, String toPhoneNumber, String messageText) {
        String url = "https://graph.facebook.com/v20.0/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiToken);

        Map<String, Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhoneNumber);
        body.put("type", "text");

        Map<String, String> text = new HashMap<>();
        text.put("body", messageText);
        body.put("text", text);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mensagem enviada com sucesso via WhatsApp para o numero {}. Resposta Meta: {}", toPhoneNumber, response.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("Erro retornado pela Graph API da Meta (Status {}): {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Erro desconhecido ao enviar mensagem dinamicamente para o numero {}", toPhoneNumber, e);
        }
    }

    /**
     * Cria um RestTemplate capaz de ignorar bloqueios de SSL em ambiente local/desenvolvimento
     */
    private RestTemplate createUnsafeRestTemplate() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    if (connection instanceof HttpsURLConnection httpsConnection) {
                        httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());
                        httpsConnection.setHostnameVerifier(allHostsValid);
                    }
                    super.prepareConnection(connection, httpMethod);
                }
            };

            return new RestTemplate(factory);
        } catch (Exception e) {
            log.error("Erro ao configurar cliente SSL customizado", e);
            return new RestTemplate();
        }
    }
}