package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.repository.telegram.TelegramBotConfigRepository;
import com.rival.chatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final ChatService chatService;
    private final TelegramBotConfigRepository telegramBotConfigRepository;
    private final CustomerDataRepository customerDataRepository;

    public TelegramBotService(ChatService chatService,
                              TelegramBotConfigRepository telegramBotConfigRepository,
                              CustomerDataRepository customerDataRepository) {
        super(configureUnsafeSSLAndGetOptions(), "");
        this.chatService = chatService;
        this.telegramBotConfigRepository = telegramBotConfigRepository;
        this.customerDataRepository = customerDataRepository;
    }

    @Override
    public String getBotToken() {
        return telegramBotConfigRepository.findFirstByActiveTrue()
                .map(TelegramBotConfigEntity::getBotToken)
                .orElse("");
    }

    @Override
    public String getBotUsername() {
        return telegramBotConfigRepository.findFirstByActiveTrue()
                .map(TelegramBotConfigEntity::getBotUsername)
                .orElse("rival_atendimento_bot");
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Long chatId = update.getMessage().getChatId();
                UUID tenantId = telegramBotConfigRepository.findFirstByActiveTrue()
                        .map(TelegramBotConfigEntity::getTenantId)
                        .orElseGet(() -> UUID.nameUUIDFromBytes("TELEGRAM_TENANT".getBytes()));
                UUID sessionId = UUID.nameUUIDFromBytes(chatId.toString().getBytes());

                String userText = "";
                String mediaUrl = null;
                String botToken = this.getBotToken();

                // 1. Captura Texto normal ou Legenda
                if (update.getMessage().hasText()) {
                    userText = update.getMessage().getText().trim();
                } else if (update.getMessage().getCaption() != null) {
                    userText = update.getMessage().getCaption().trim();
                }

                // 2. Captura Animações (GIFs), Fotos ou Vídeos
                if (update.getMessage().hasAnimation()) {
                    String fileId = update.getMessage().getAnimation().getFileId();
                    mediaUrl = getTelegramMediaUrl(fileId, botToken);
                } else if (update.getMessage().hasPhoto()) {
                    var photos = update.getMessage().getPhoto();
                    String fileId = photos.get(photos.size() - 1).getFileId();
                    mediaUrl = getTelegramMediaUrl(fileId, botToken);
                } else if (update.getMessage().hasVideo()) {
                    String fileId = update.getMessage().getVideo().getFileId();
                    mediaUrl = getTelegramMediaUrl(fileId, botToken);
                }

                // 3. Junta o texto com a URL da mídia
                String finalContent = userText;
                if (mediaUrl != null) {
                    finalContent = finalContent.isEmpty() ? mediaUrl : finalContent + "\n" + mediaUrl;
                }

                if (!finalContent.isEmpty()) {
                    if ("/start".equalsIgnoreCase(finalContent)) {
                        sendReply(chatId, "Olá! Sou o assistente virtual inteligente. Como posso ajudar?");
                        return;
                    }

                    ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, tenantId, finalContent, "TELEGRAM", chatId.toString());
                    ChatResponseDTO response = chatService.processMessage(chatRequest);

                    User sender = update.getMessage().getFrom();
                    if (sender != null) {
                        String firstName = sender.getFirstName() != null ? sender.getFirstName() : "";
                        String lastName = sender.getLastName() != null ? sender.getLastName() : "";
                        String fullName = (firstName + " " + lastName).trim();
                        String botName = this.getBotUsername();

                        if (!fullName.isEmpty()) {
                            customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                                if (customer.getName() == null) {
                                    customer.setName(fullName + " (" + botName + ")");
                                    customerDataRepository.save(customer);
                                }
                            });
                        }
                    }

                    sendReply(chatId, response.response());
                }
            }
        } catch (Exception e) {
            log.error("Erro no TelegramBotService", e);
        }
    }

    // A MÁGICA ACONTECE AQUI: Pegar o link da imagem no Telegram
    private String getTelegramMediaUrl(String fileId, String botToken) {
        try {
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
            return "https://api.telegram.org/file/bot" + botToken + "/" + file.getFilePath();
        } catch (Exception e) {
            log.error("Erro ao converter FileId do Telegram em URL", e);
            return null;
        }
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try {
            execute(message);
        } catch (Exception e) {
            log.error("Erro ao enviar texto", e);
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
        } catch (Exception e) {}
        return new DefaultBotOptions();
    }

    public void sendMessageToClient(String chatId, String text) {
        sendReply(Long.valueOf(chatId), text);
    }
}