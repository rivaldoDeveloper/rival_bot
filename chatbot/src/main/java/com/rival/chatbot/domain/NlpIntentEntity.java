package com.rival.chatbot.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "nlp_intents")
public class NlpIntentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 10)
    private String language;

    // Mantido para compatibilidade com os controllers e DTOs de treinamento
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nlp_intent_keywords", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "keyword")
    @Fetch(FetchMode.SUBSELECT) // <-- EVITA MÚLTIPLOS SELECTS INDIVIDUAIS
    @OnDelete(action = OnDeleteAction.CASCADE) // <-- PERMITE DELETAR O PAI APAGANDO AS RESPOSTAS JUNTO
    private List<String> keywords = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "nlp_intent_responses", joinColumns = @JoinColumn(name = "intent_id"))
    @Column(name = "response", columnDefinition = "TEXT")
    @Fetch(FetchMode.SUBSELECT) // <-- EVITA MÚLTIPLOS SELECTS INDIVIDUAIS
    @OnDelete(action = OnDeleteAction.CASCADE) // <-- PERMITE DELETAR O PAI APAGANDO AS RESPOSTAS JUNTO
    private List<String> responses = new ArrayList<>();

    @Transient
    private float[] semanticEmbedding;

    public NlpIntentEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public List<String> getResponses() { return responses; }
    public void setResponses(List<String> responses) { this.responses = responses; }

    public float[] getSemanticEmbedding() { return semanticEmbedding; }
    public void setSemanticEmbedding(float[] semanticEmbedding) { this.semanticEmbedding = semanticEmbedding; }
}