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
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.UUID;

public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    private final ChatService chatService;
    private final CustomerDataRepository customerDataRepository;
    private final TelegramBotConfigEntity botConfig;

    public TelegramBotService(ChatService chatService, CustomerDataRepository customerDataRepository, TelegramBotConfigEntity botConfig) {
        super(new DefaultBotOptions(), botConfig.getBotToken());
        this.chatService = chatService;
        this.customerDataRepository = customerDataRepository;
        this.botConfig = botConfig;
    }

    @Override
    public String getBotToken() { return botConfig.getBotToken(); }

    @Override
    public String getBotUsername() { return botConfig.getBotUsername(); }

    private void deleteQuietly(File file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file.toPath());
                File parent = file.getParentFile();
                if (parent != null && "uploads".equalsIgnoreCase(parent.getName())) {
                    String[] list = parent.list();
                    if (list == null || list.length == 0) {
                        Files.deleteIfExists(parent.toPath());
                    }
                }
            } catch (Exception ignored) {}
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

                if (fileId != null) mediaUrl = resolveTelegramFileUrl(fileId);

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
                        if (respText != null && (respText.contains("|||") || respText.startsWith("TEXT:") || respText.startsWith("IMAGE:") || respText.startsWith("AUDIO:") || respText.startsWith("VIDEO:"))) {
                            String[] parts = respText.split("\\|\\|\\|");
                            for (String part : parts) {
                                if (part.startsWith("IMAGE:")) {
                                    String url = part.substring(6).trim();
                                    // ZERO-DISK I/O: Faz stream direto da nuvem para o Telegram sem criar pasta uploads!
                                    try (InputStream in = URI.create(url).toURL().openStream()) {
                                        SendPhoto photoMsg = new SendPhoto();
                                        photoMsg.setChatId(chatId.toString());
                                        photoMsg.setPhoto(new InputFile(in, "image.jpg"));
                                        execute(photoMsg);
                                    } catch (Exception ex) {
                                        log.error("Erro ao enviar imagem no Telegram", ex);
                                    }
                                } else if (part.startsWith("AUDIO:")) {
                                    String url = part.substring(6).trim();
                                    if (url.contains("cloudinary.com") && url.lastIndexOf('.') > url.lastIndexOf('/')) {
                                        url = url.substring(0, url.lastIndexOf('.')) + ".mp3";
                                    }
                                    try (InputStream in = URI.create(url).toURL().openStream()) {
                                        SendAudio audioMsg = new SendAudio();
                                        audioMsg.setChatId(chatId.toString());
                                        audioMsg.setAudio(new InputFile(in, "audio.mp3"));
                                        execute(audioMsg);
                                    } catch (Exception ex) {
                                        log.error("Erro ao enviar áudio no Telegram", ex);
                                    }
                                } else if (part.startsWith("VIDEO:")) {
                                    String url = part.substring(6).trim();
                                    if (url.contains("cloudinary.com") && url.lastIndexOf('.') > url.lastIndexOf('/')) {
                                        url = url.substring(0, url.lastIndexOf('.')) + ".mp4";
                                    }
                                    try (InputStream in = URI.create(url).toURL().openStream()) {
                                        SendVideo videoMsg = new SendVideo();
                                        videoMsg.setChatId(chatId.toString());
                                        videoMsg.setVideo(new InputFile(in, "video.mp4"));
                                        execute(videoMsg);
                                    } catch (Exception ex) {
                                        log.error("Erro ao enviar vídeo no Telegram", ex);
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

    /**
     * Obtém o link direto da API do Telegram sem baixar ficheiro para a pasta uploads!
     */
    private String resolveTelegramFileUrl(String fileId) {
        try {
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(fileId);
            org.telegram.telegrambots.meta.api.objects.File telegramFile = execute(getFileMethod);
            return telegramFile.getFileUrl(getBotToken());
        } catch (Exception e) {
            return null;
        }
    }

    private void sendReply(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        try { execute(message); } catch (Exception ignored) {}
    }

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
        } finally {
            deleteQuietly(file);
        }
    }

    public void sendMessageToClient(String chatId, String text) {
        sendReply(Long.valueOf(chatId), text);
    }
}