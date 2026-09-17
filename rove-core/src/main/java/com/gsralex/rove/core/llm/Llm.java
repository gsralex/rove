package com.gsralex.rove.core.llm;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.tool.Tool;
import java.util.List;
import java.util.function.Consumer;

public interface Llm {

    LlmResp chat(List<Message> messages);

    LlmResp chat(List<Message> messages, List<Tool> tools);

    default LlmResp stream(List<Message> messages, Consumer<String> onToken) {
        return stream(messages, List.of(), onToken);
    }

    default LlmResp stream(List<Message> messages, List<Tool> tools, Consumer<String> onToken) {
        LlmResp resp = tools == null || tools.isEmpty() ? chat(messages) : chat(messages, tools);
        if (onToken != null && !resp.isEmpty()) {
            Message first = resp.first().message();
            String text = first == null ? null : first.content();
            if (text != null && !text.isEmpty()) {
                onToken.accept(text);
            }
        }
        return resp;
    }
}
