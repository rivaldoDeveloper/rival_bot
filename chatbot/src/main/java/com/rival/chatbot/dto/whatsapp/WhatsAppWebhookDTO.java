package com.rival.chatbot.dto.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WhatsAppWebhookDTO(
        String object,
        List<Entry> entry
) {
    public record Entry(
            String id,
            List<Change> changes
    ) {}

    public record Change(
            Value value,
            String field
    ) {}

    public record Value(
            @JsonProperty("messaging_product")
            String messagingProduct,
            Metadata metadata,
            List<Contact> contacts,
            List<Message> messages
    ) {}

    public record Metadata(
            @JsonProperty("display_phone_number")
            String displayPhoneNumber,
            @JsonProperty("phone_number_id")
            String phoneNumberId
    ) {}

    public record Contact(
            Profile profile,
            @JsonProperty("wa_id")
            String waId
    ) {}

    public record Profile(
            String name
    ) {}

    public record Message(
            String from,
            String id,
            String timestamp,
            String type,
            Text text
    ) {}

    public record Text(
            String body
    ) {}
}