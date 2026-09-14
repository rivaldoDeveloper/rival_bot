package com.rival.chatbot.service.whatsapp;

import com.rival.chatbot.domain.whatsapp.WhatsAppAccountEntity;
import java.util.List;

public interface WhatsAppAccountService {
    WhatsAppAccountEntity saveOrUpdateAccount(WhatsAppAccountEntity account);
    List<WhatsAppAccountEntity> getAllAccounts();
}