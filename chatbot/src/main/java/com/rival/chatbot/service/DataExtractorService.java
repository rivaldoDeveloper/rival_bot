package com.rival.chatbot.service;

import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.CustomerDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataExtractorService {

    private static final Logger log = LoggerFactory.getLogger(DataExtractorService.class);

    private final CustomerDataRepository repository;

    private static final Pattern CPF_PATTERN = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)(?:meu nome e|me chamo|sou o|sou a)\\s+([A-Za-z ]+(?:\\s+[A-Za-z ]+)*)");

    public DataExtractorService(CustomerDataRepository repository) {
        this.repository = repository;
    }

    // ✅ CORREÇÃO CRÍTICA: @Async REMOVIDO!
    // Ao rodar na mesma linha cronológica, o Loop de Repetição do Flow Engine desaparece para sempre.
    @Transactional
    public void extractAndSave(UUID sessionId, UUID tenantId, String message, String channel, String externalId) {
        try {
            CustomerDataEntity customerData = repository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        CustomerDataEntity entity = new CustomerDataEntity();
                        entity.setSessionId(sessionId);
                        entity.setTenantId(tenantId);
                        entity.setUnreadCount(0);
                        return entity;
                    });

            boolean updated = false;

            if (customerData.getUnreadCount() == null) {
                customerData.setUnreadCount(0);
            }
            customerData.setUnreadCount(customerData.getUnreadCount() + 1);
            updated = true;

            if (channel != null && (customerData.getChannel() == null || !customerData.getChannel().equals(channel))) {
                customerData.setChannel(channel);
                updated = true;
            }

            if (externalId != null && (customerData.getExternalId() == null || !customerData.getExternalId().equals(externalId))) {
                customerData.setExternalId(externalId);
                updated = true;
            }

            if (message != null && !message.isBlank()) {
                Matcher cpfMatcher = CPF_PATTERN.matcher(message);
                if (cpfMatcher.find()) {
                    customerData.setCpf(cpfMatcher.group().replaceAll("[^0-9]", ""));
                    updated = true;
                }
                Matcher emailMatcher = EMAIL_PATTERN.matcher(message);
                if (emailMatcher.find()) {
                    customerData.setEmail(emailMatcher.group());
                    updated = true;
                }
                Matcher nameMatcher = NAME_PATTERN.matcher(message);
                if (nameMatcher.find()) {
                    customerData.setName(nameMatcher.group(1));
                    updated = true;
                }
            }

            if (updated) {
                repository.save(customerData);
            }
        } catch (Exception e) {
            log.error("Erro ao extrair dados", e);
        }
    }
}