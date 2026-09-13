package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.repository.telegram.TelegramBotConfigRepository;
import com.rival.chatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    //configurar aplication
    // -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT -Dcom.sun.net.ssl.checkRevocation=false

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private final ChatService chatService;
    private final TelegramBotConfigRepository telegramBotConfigRepository;

    public TelegramBotService(ChatService chatService, TelegramBotConfigRepository telegramBotConfigRepository) {
        super(configureUnsafeSSLAndGetOptions(), "");
        this.chatService = chatService;
        this.telegramBotConfigRepository = telegramBotConfigRepository;
    }

    @Override
    public String getBotToken() {
        // Busca dinamicamente o token do bot ativo no PostgreSQL
        return telegramBotConfigRepository.findFirstByActiveTrue()
                .map(TelegramBotConfigEntity::getBotToken)
                .orElse("");
    }

    @Override
    public String getBotUsername() {
        // Busca dinamicamente o username no PostgreSQL
        return telegramBotConfigRepository.findFirstByActiveTrue()
                .map(TelegramBotConfigEntity::getBotUsername)
                .orElse("rival_atendimento_bot");
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String userText = update.getMessage().getText().trim();
                Long chatId = update.getMessage().getChatId();
                log.info("Mensagem recebida no Telegram do chatId {}: '{}'", chatId, userText);

                if ("/start".equalsIgnoreCase(userText)) {
                    sendReply(chatId, "Olá! Sou o assistente virtual do Rival Chatbot. Como posso te ajudar hoje?");
                    return;
                }

                // Identifica o Tenant ID dinamicamente no banco
                UUID tenantId = telegramBotConfigRepository.findFirstByActiveTrue()
                        .map(TelegramBotConfigEntity::getTenantId)
                        .orElseGet(() -> UUID.nameUUIDFromBytes("TELEGRAM_TENANT".getBytes()));

                UUID sessionId = UUID.nameUUIDFromBytes(chatId.toString().getBytes());

                ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, tenantId, userText);
                ChatResponseDTO response = chatService.processMessage(chatRequest);

                sendReply(chatId, response.response());
            }
        } catch (Exception e) {
            log.error("Erro ao processar mensagem do Telegram", e);
        }
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
            log.info("Resposta enviada com sucesso para o Telegram chatId {}", chatId);
        } catch (Exception e) {
            log.error("Erro ao enviar resposta via Telegram API", e);
        }
    }

    private static DefaultBotOptions configureUnsafeSSLAndGetOptions() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            SSLContext.setDefault(sc);
            System.setProperty("com.sun.net.ssl.checkRevocation", "false");
            log.info("Configuração de bypass de SSL/PKIX concluída no escopo do Telegram Service.");
        } catch (Exception e) {
            log.error("Erro ao configurar SSL inseguro", e);
        }
        return new DefaultBotOptions();
    }
}