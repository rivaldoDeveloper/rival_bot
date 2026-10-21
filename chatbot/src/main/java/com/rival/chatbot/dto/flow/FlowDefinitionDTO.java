package com.rival.chatbot.dto.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowDefinitionDTO(
        List<FlowNode> nodes,
        List<FlowEdge> edges
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowNode(
            String id,
            String type,
            NodeData data
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeData(
            String text,
            String imageUrl,
            String audioUrl,
            String videoUrl,     // Adicionado para suportar o nó de vídeo
            Boolean active,      // Adicionado para controlar o bypass do nó
            String fieldToExtract // NOVO: Adicionado corretamente como atributo para a extração CRM
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowEdge(
            String id,
            String sourceId,
            String targetId,
            String condition
    ) {}
}