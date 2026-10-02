package com.rival.chatbot.dto.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowDefinitionDTO(
        List<FlowNode> nodes,
        List<FlowEdge> edges
) {
    public record FlowNode(
            String id,
            String type,
            NodeData data
    ) {}

    public record NodeData(
            String text,
            String audioUrl
    ) {}

    public record FlowEdge(
            String id,           // ✅ Adicionado para bater certo com o Angular
            String sourceId,     // ✅ Corrigido (antes era 'source')
            String targetId,     // ✅ Corrigido (antes era 'target')
            String condition
    ) {}
}