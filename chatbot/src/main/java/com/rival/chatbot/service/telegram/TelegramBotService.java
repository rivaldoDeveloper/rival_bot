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
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.nio.file.Files;
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

                if (update.getMessage().hasText()) {
                    userText = update.getMessage().getText().trim();
                } else if (update.getMessage().getCaption() != null) {
                    userText = update.getMessage().getCaption().trim();
                }

                String fileId = null;
                String extension = ".bin";

                if (update.getMessage().hasPhoto()) {
                    var photos = update.getMessage().getPhoto();
                    fileId = photos.get(photos.size() - 1).getFileId();
                    extension = ".jpg";
                } else if (update.getMessage().hasVideo()) {
                    fileId = update.getMessage().getVideo().getFileId();
                    extension = ".mp4";
                } else if (update.getMessage().hasVoice()) {
                    fileId = update.getMessage().getVoice().getFileId();
                    extension = ".ogg";
                } else if (update.getMessage().hasAudio()) {
                    fileId = update.getMessage().getAudio().getFileId();
                    extension = ".mp3";
                } else if (update.getMessage().hasAnimation()) {
                    fileId = update.getMessage().getAnimation().getFileId();
                    extension = ".mp4";
                } else if (update.getMessage().hasDocument()) {
                    fileId = update.getMessage().getDocument().getFileId();
                    String fileName = update.getMessage().getDocument().getFileName();
                    if (fileName != null && fileName.contains(".")) {
                        extension = fileName.substring(fileName.lastIndexOf("."));
                    } else {
                        extension = ".pdf";
                    }
                }

                if (fileId != null) {
                    mediaUrl = downloadTelegramMedia(fileId, extension);
                }

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

                        if (!fullName.isEmpty()) {
                            customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                                // ✅ CORREÇÃO: Salva apenas o nome do cliente puro (ou corrige se estiver com parênteses)
                                if (customer.getName() == null || customer.getName().contains("(")) {
                                    customer.setName(fullName);
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

    private String downloadTelegramMedia(String fileId, String extension) {
        try {
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFileMethod);

            java.io.File dir = new java.io.File("uploads");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "tg_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            java.io.File localFile = new java.io.File(dir, fileName);

            downloadFile(telegramFile, localFile);
            return "http://localhost:8080/uploads/" + fileName;
        } catch (Exception e) {
            log.error("Erro ao baixar arquivo do Telegram", e);
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

    public void sendMediaToClient(String chatId, String caption, File file) {
        try {
            String fileName = file.getName().toLowerCase();
            String mimeType = Files.probeContentType(file.toPath());

            boolean isAudio = (mimeType != null && mimeType.startsWith("audio")) || fileName.contains("gravacao_") || fileName.endsWith(".mp3") || fileName.endsWith(".webm") || fileName.endsWith(".ogg");
            boolean isVideo = (mimeType != null && mimeType.startsWith("video")) && !isAudio;

            if (mimeType != null && mimeType.startsWith("image")) {
                SendPhoto msg = new SendPhoto();
                msg.setChatId(chatId);
                msg.setPhoto(new InputFile(file));
                if (caption != null && !caption.isBlank()) msg.setCaption(caption);
                execute(msg);
            }
            else if (isVideo) {
                SendVideo msg = new SendVideo();
                msg.setChatId(chatId);
                msg.setVideo(new InputFile(file));
                if (caption != null && !caption.isBlank()) msg.setCaption(caption);
                execute(msg);
            }
            else if (isAudio) {
                SendAudio msg = new SendAudio();
                msg.setChatId(chatId);
                msg.setAudio(new InputFile(file));
                if (caption != null && !caption.isBlank()) msg.setCaption(caption);
                execute(msg);
            }
            else {
                SendDocument msg = new SendDocument();
                msg.setChatId(chatId);
                msg.setDocument(new InputFile(file));
                if (caption != null && !caption.isBlank()) msg.setCaption(caption);
                execute(msg);
            }
        } catch (Exception e) {
            log.error("Erro ao enviar mídia via Telegram", e);
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