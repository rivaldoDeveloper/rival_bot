package com.rival.chatbot.dto.meta;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MetaWebhookDTO(
        String object,
        List<Entry> entry
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String id,
            List<Messaging> messaging
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Messaging(
            Sender sender,
            Recipient recipient,
            Message message
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sender(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recipient(String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String mid,
            String text,
            List<Attachment> attachments
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Attachment(
            String type,
            Payload payload
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(String url) {}
}