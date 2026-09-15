package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.tool.ToolCall;
import java.util.List;

public interface Filter {

    default FilterResult beforeRequest(List<Message> messages) {
        return FilterResult.allow();
    }

    default FilterResult beforeTool(ToolCall call) {
        return FilterResult.allow();
    }

    default FilterResult beforeReply(String reply) {
        return FilterResult.allow();
    }
}
