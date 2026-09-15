package com.gsralex.rove.core.llm;

import java.util.List;

public record LlmResp(List<Choice> choices) {

    public LlmResp {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public static LlmResp of(Choice... choices) {
        return new LlmResp(List.of(choices));
    }

    public boolean isEmpty() {
        return choices.isEmpty();
    }

    public Choice first() {
        return choices.getFirst();
    }
}
