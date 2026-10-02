# 🚀 Rival Chatbot Omnichannel API

Um backend poderoso, escalável e multi-tenant construído em **Spring Boot 3 (Java 17+)** que centraliza a comunicação de empresas através de múltiplos canais (WhatsApp e Telegram). Integra motores de Inteligência Artificial nativos e externos, Extração de Dados (RegEx) e Visão Computacional (OCR) para automatizar o atendimento ao cliente.

## 🌟 Principais Funcionalidades

*   **Arquitetura Multi-Tenant:** Preparado para servir múltiplas empresas (`tenantId`) na mesma instância, com total isolamento lógico de conversas, fluxos e robôs.
*   **Gateway WhatsApp:** Integração bidirecional com a *Evolution API*. Gere as instâncias nativamente, gera QR Codes, Pairing Codes e recebe mensagens via Webhooks (`messages.upsert`). Suporta áudios, imagens, vídeos, PDFs e stickers.
*   **Gateway Telegram:** Motor customizado de robôs dinâmicos. Inicializa e escuta múltiplos Bots de Telegram simultaneamente (via `TelegramBotsApi` e *Long Polling*), controlados diretamente por registos na base de dados, sem necessidade de reiniciar a aplicação.
*   **Live Chat Centralizado:** Interceção, monitorização e *Human Handoff* (transbordo humano). Os operadores humanos podem desativar a IA, assumir a conversa e responder com texto e multimédia (arrastar-e-largar) num único painel.
*   **Motor NLP Local (Intenções & Vetores):** Um motor nativo construído do zero (`LocalVectorNlpService`) utilizando **Similaridade de Cossenos** e vetores de 128 dimensões. Dispensa serviços na nuvem caros para intenções básicas. As intenções (Gatilhos e Respostas) são treináveis via API.
*   **Integração Generativa (Gemini AI):** Fallback inteligente utilizando a API nativa da Google (`gemini-2.5-flash`) via `RestClient` do Spring.
*   **Motor de Fluxos Visuais (Flow Engine):** Interpreta diagramas JSON em tempo real (ex: React Flow), avançando os clientes passo-a-passo (`currentNodeId`) num funil estruturado de conversação.
*   **Extração de Dados Oculta (CRM Automático):** Analisa continuamente o texto do cliente (`DataExtractorService` via `@Async`) identificando Nomes, E-mails, CPFs e localizações com RegEx, enriquecendo o CRM de forma invisível.
*   **Módulo OCR:** Lê texto dentros das imagens (JPG/PNG) enviadas pelo cliente utilizando a biblioteca *Tess4J (Tesseract)*.

---

## 🛠 Stack Tecnológico

*   **Linguagem:** Java 17+
*   **Framework:** Spring Boot 3.x
*   **Persistência:** Spring Data JPA / Hibernate
*   **Base de Dados:** PostgreSQL (Hospedagem sugerida: NeonDB)
*   **Integrações:**
    *   Evolution API (Para protocolo WhatsApp Baileys)
    *   TelegramBots API (Long Polling)
    *   Google Gemini API (Modelos Generativos)
    *   Tess4J (Optical Character Recognition)
*   **Gestão Assíncrona:** `@EnableAsync` (Para extração de dados e processamentos em background)
*   **WebSockets:** Configurado via `STOMP` para suporte a respostas em tempo real (Live Chat).

---

## 📂 Estrutura de Diretórios (Domain-Driven Design)

```text
com.rival.chatbot
  ├── config/          # Definições de CORS, Assincronia, WebSockets e Inicializador Telegram
  ├── controller/      # Endpoints REST (Chat, History, NLP, Webhooks, File Uploads)
  ├── domain/          # Entidades JPA (PostgreSQL) - NlpIntent, ChatMessage, CustomerData...
  ├── dto/             # Records Java imutáveis para transferência de dados (Req/Res)
  ├── exception/       # GlobalExceptionHandler (Gestão unificada de erros e retornos)
  ├── repository/      # Interfaces de acesso ao banco (Spring Data JPA)
  ├── service/         # Camada de Regras de Negócio
  │   ├── ai/          # Motores NLP Vetoriais e Generativos
  │   ├── telegram/    # Registo Dinâmico e Serviço de Long Polling do Telegram
  │   └── whatsapp/    # Comunicação com a Evolution API (Gestão, Envios e Webhooks)
  └── util/            # Utilitários (Normalizadores de Texto, Distância de Levenshtein)