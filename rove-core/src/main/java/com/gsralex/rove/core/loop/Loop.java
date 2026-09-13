package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.Tool;
import com.gsralex.rove.core.common.Message;
import java.util.List;

/** ReAct cycle: LLM → tool calls → tool results → LLM, until a text reply. */
public interface Loop {

    String run(List<Message> messages, List<Tool> tools);
}
