package com.rival.chatbot.dto.flow;

import java.util.List;

public record FlowDefinition(
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
            String source,
            String target,
            String condition
    ) {}
}