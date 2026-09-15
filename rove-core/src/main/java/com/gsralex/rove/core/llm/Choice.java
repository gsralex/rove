package com.gsralex.rove.core.llm;

import com.gsralex.rove.core.common.Message;

public record Choice(int index, Message message, String finishReason) {}
