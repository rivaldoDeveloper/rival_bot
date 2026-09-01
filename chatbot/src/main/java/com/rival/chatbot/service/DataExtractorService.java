package com.rival.chatbot.service;

import com.rival.chatbot.domain.CustomerDataEntity;
import com.rival.chatbot.repository.CustomerDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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
    private static final Pattern NAME_PATTERN = Pattern.compile("(?i)(?:meu nome e|me chamo|sou o|sou a)\\s+([A-Za-zÀ-ÿ]+(?:\\s+[A-Za-zÀ-ÿ]+)*)");
    private static final Pattern CITY_PATTERN = Pattern.compile("(?i)(?:moro em|cidade de|moro na cidade)\\s+([A-Za-zÀ-ÿ]+(?:\\s+[A-Za-zÀ-ÿ]+)*)");
    private static final Pattern NEIGHBORHOOD_PATTERN = Pattern.compile("(?i)(?:bairro|no bairro)\\s+([A-Za-zÀ-ÿ]+(?:\\s+[A-Za-zÀ-ÿ]+)*)");

    public DataExtractorService(CustomerDataRepository repository) {
        this.repository = repository;
    }

    @Async
    @Transactional
    public void extractAndSave(UUID sessionId, UUID tenantId, String message) {
        try {
            CustomerDataEntity customerData = repository.findBySessionId(sessionId)
                    .orElseGet(() -> {
                        CustomerDataEntity entity = new CustomerDataEntity();
                        entity.setSessionId(sessionId);
                        entity.setTenantId(tenantId);
                        return entity;
                    });

            boolean updated = false;

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

            Matcher cityMatcher = CITY_PATTERN.matcher(message);
            if (cityMatcher.find()) {
                customerData.setCity(cityMatcher.group(1));
                updated = true;
            }

            Matcher neighborhoodMatcher = NEIGHBORHOOD_PATTERN.matcher(message);
            if (neighborhoodMatcher.find()) {
                customerData.setNeighborhood(neighborhoodMatcher.group(1));
                updated = true;
            }

            if (updated) {
                repository.save(customerData);
                log.info("Dados de lead extraídos e salvos com sucesso para a sessão {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Erro ao processar extração de dados em background para a sessão {}", sessionId, e);
        }
    }
}