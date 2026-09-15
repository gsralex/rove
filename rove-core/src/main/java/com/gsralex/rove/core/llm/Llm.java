package com.gsralex.rove.core.llm;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.tool.Tool;
import java.util.List;

public interface Llm {

    LlmResp chat(List<Message> messages);

    LlmResp chat(List<Message> messages, List<Tool> tools);
}
