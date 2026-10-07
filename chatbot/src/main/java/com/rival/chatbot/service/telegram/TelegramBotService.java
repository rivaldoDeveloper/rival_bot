package com.rival.chatbot.service.telegram;

import com.rival.chatbot.domain.telegram.TelegramBotConfigEntity;
import com.rival.chatbot.dto.ChatRequestDTO;
import com.rival.chatbot.dto.ChatResponseDTO;
import com.rival.chatbot.repository.CustomerDataRepository;
import com.rival.chatbot.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
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

public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private final ChatService chatService;
    private final CustomerDataRepository customerDataRepository;
    private final TelegramBotConfigEntity botConfig;

    public TelegramBotService(ChatService chatService, CustomerDataRepository customerDataRepository, TelegramBotConfigEntity botConfig) {
        super(configureUnsafeSSLAndGetOptions(), botConfig.getBotToken());
        this.chatService = chatService;
        this.customerDataRepository = customerDataRepository;
        this.botConfig = botConfig;
    }

    @Override
    public String getBotToken() { return botConfig.getBotToken(); }

    @Override
    public String getBotUsername() { return botConfig.getBotUsername(); }

    private File downloadFromUrl(String urlStr, String extension) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.io.InputStream in = url.openStream();
            File dir = new File("uploads");
            if (!dir.exists()) dir.mkdirs();
            File tempFile = new File(dir, "tg_dl_" + UUID.randomUUID().toString().substring(0,8) + extension);
            java.nio.file.Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        } catch (Exception e) {
            log.error("Erro ao baixar ficheiro da nuvem para Telegram: " + urlStr, e);
            return null;
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Long chatId = update.getMessage().getChatId();
                UUID tenantId = botConfig.getTenantId();
                UUID sessionId = UUID.nameUUIDFromBytes(chatId.toString().getBytes());

                String userText = "";
                String mediaUrl = null;

                if (update.getMessage().hasText()) userText = update.getMessage().getText().trim();
                else if (update.getMessage().getCaption() != null) userText = update.getMessage().getCaption().trim();

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
                }

                if (fileId != null) mediaUrl = downloadTelegramMedia(fileId, extension);

                String finalContent = userText;
                if (mediaUrl != null) finalContent = finalContent.isEmpty() ? mediaUrl : finalContent + "\n" + mediaUrl;

                if (!finalContent.isEmpty()) {
                    ChatRequestDTO chatRequest = new ChatRequestDTO(sessionId, tenantId, finalContent, null, "TELEGRAM", chatId.toString());
                    ChatResponseDTO response = chatService.processFlowMessage(chatRequest);

                    User sender = update.getMessage().getFrom();
                    if (sender != null && !sender.getFirstName().isEmpty()) {
                        customerDataRepository.findBySessionId(sessionId).ifPresent(customer -> {
                            if (customer.getName() == null || !customer.getName().contains("(")) {
                                customer.setName(sender.getFirstName() + " (@" + this.getBotUsername() + ")");
                                customerDataRepository.save(customer);
                            }
                        });
                    }

                    if (response != null) {
                        String respText = response.response();

                        if (respText != null && (respText.contains("|||") || respText.startsWith("TEXT:") || respText.startsWith("AUDIO:") || respText.startsWith("VIDEO:"))) {
                            String[] parts = respText.split("\\|\\|\\|");
                            for (String part : parts) {
                                if (part.startsWith("AUDIO:")) {
                                    String url = part.substring(6).trim();
                                    if (url.contains("cloudinary.com") && url.lastIndexOf('.') > url.lastIndexOf('/')) {
                                        url = url.substring(0, url.lastIndexOf('.')) + ".mp3";
                                    }
                                    File localFile = downloadFromUrl(url, ".mp3");
                                    if (localFile != null) {
                                        try {
                                            SendAudio audioMsg = new SendAudio();
                                            audioMsg.setChatId(chatId.toString());
                                            audioMsg.setAudio(new InputFile(localFile));
                                            execute(audioMsg);
                                        } catch (Exception ex) { log.error("Erro ao enviar áudio no Telegram", ex); }
                                    }
                                } else if (part.startsWith("VIDEO:")) {
                                    String url = part.substring(6).trim();
                                    if (url.contains("cloudinary.com") && url.lastIndexOf('.') > url.lastIndexOf('/')) {
                                        url = url.substring(0, url.lastIndexOf('.')) + ".mp4";
                                    }
                                    File localFile = downloadFromUrl(url, ".mp4");
                                    if (localFile != null) {
                                        try {
                                            SendVideo videoMsg = new SendVideo();
                                            videoMsg.setChatId(chatId.toString());
                                            videoMsg.setVideo(new InputFile(localFile));
                                            execute(videoMsg);
                                        } catch (Exception ex) { log.error("Erro ao enviar vídeo no Telegram", ex); }
                                    }
                                } else if (part.startsWith("TEXT:")) {
                                    sendReply(chatId, part.substring(5).trim());
                                }
                            }
                        } else {
                            if (respText != null && !respText.isBlank()) {
                                sendReply(chatId, respText);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erro estrutural no TelegramBotService", e);
        }
    }

    private InputFile getTelegramInputFile(String urlStr) {
        if (urlStr.contains("localhost") || urlStr.contains("127.0.0.1")) {
            try {
                String fileName = urlStr.substring(urlStr.lastIndexOf("/") + 1);
                File localFile = new File(System.getProperty("user.dir") + File.separator + "uploads", fileName);
                if (localFile.exists()) return new InputFile(localFile, fileName);
            } catch(Exception e) { return null; }
        }
        return new InputFile(urlStr);
    }

    private String downloadTelegramMedia(String fileId, String extension) {
        try {
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFileMethod);
            File dir = new File("uploads");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "tg_" + UUID.randomUUID().toString().substring(0, 8) + extension;
            File localFile = new File(dir, fileName);
            downloadFile(telegramFile, localFile);
            return "http://localhost:8080/uploads/" + fileName;
        } catch (Exception e) { return null; }
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try { execute(message); } catch (Exception e) {}
    }

    // MÉTODO 1: Usado pelo Webhook (Ficheiros vindos do Flow Engine via URL convertido para local)
    public void sendMediaUrlToClient(String chatId, String caption, String urlStr, String mimeType) {
        try {
            boolean isAudio = mimeType != null && mimeType.startsWith("audio");
            boolean isVideo = mimeType != null && mimeType.startsWith("video");

            if (urlStr.contains("cloudinary.com")) {
                if (isAudio) {
                    urlStr = urlStr.substring(0, urlStr.lastIndexOf('.')) + ".ogg";
                } else if (isVideo) {
                    urlStr = urlStr.substring(0, urlStr.lastIndexOf('.')) + ".mp4";
                }
            }

            InputFile inputFile = getTelegramInputFile(urlStr);
            if (mimeType != null && mimeType.startsWith("image")) {
                SendPhoto msg = new SendPhoto(); msg.setChatId(chatId); msg.setPhoto(inputFile); if (caption != null) msg.setCaption(caption); execute(msg);
            } else if (isVideo) {
                SendVideo msg = new SendVideo(); msg.setChatId(chatId); msg.setVideo(inputFile); if (caption != null) msg.setCaption(caption); execute(msg);
            } else if (isAudio) {
                SendVoice msg = new SendVoice(); msg.setChatId(chatId); msg.setVoice(inputFile); if (caption != null) msg.setCaption(caption); execute(msg);
            } else {
                SendDocument msg = new SendDocument(); msg.setChatId(chatId); msg.setDocument(inputFile); if (caption != null) msg.setCaption(caption); execute(msg);
            }
        } catch (Exception e) {}
    }

    // MÉTODO 2 (RESTAURADO): Usado pelo ChatServiceImpl quando o Operador Humano anexa um ficheiro do PC
    public void sendMediaToClient(String chatId, String caption, File file) {
        try {
            String fileName = file.getName().toLowerCase();
            String mimeType = Files.probeContentType(file.toPath());

            boolean isAudio = (mimeType != null && mimeType.startsWith("audio")) || fileName.endsWith(".mp3") || fileName.endsWith(".webm") || fileName.endsWith(".ogg");
            boolean isVideo = (mimeType != null && mimeType.startsWith("video")) && !isAudio;

            if (mimeType != null && mimeType.startsWith("image")) {
                SendPhoto msg = new SendPhoto(); msg.setChatId(chatId); msg.setPhoto(new InputFile(file)); if (caption != null) msg.setCaption(caption); execute(msg);
            } else if (isVideo) {
                SendVideo msg = new SendVideo(); msg.setChatId(chatId); msg.setVideo(new InputFile(file)); if (caption != null) msg.setCaption(caption); execute(msg);
            } else if (isAudio) {
                SendVoice msg = new SendVoice(); msg.setChatId(chatId); msg.setVoice(new InputFile(file)); if (caption != null) msg.setCaption(caption); execute(msg);
            } else {
                SendDocument msg = new SendDocument(); msg.setChatId(chatId); msg.setDocument(new InputFile(file)); if (caption != null) msg.setCaption(caption); execute(msg);
            }
        } catch (Exception e) {
            log.error("Erro ao enviar media física para o cliente Telegram: ", e);
        }
    }

    public void sendMessageToClient(String chatId, String text) {
        sendReply(Long.valueOf(chatId), text);
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
}