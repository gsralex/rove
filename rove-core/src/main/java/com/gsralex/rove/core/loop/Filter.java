package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.common.ToolCall;

/** Runs before an action. {@code allowed == false} blocks it. */
public interface Filter {

    default FilterResult beforeRequest(String prompt) {
        return FilterResult.allow();
    }

    default FilterResult beforeTool(ToolCall call) {
        return FilterResult.allow();
    }

    default FilterResult beforeReply(String reply) {
        return FilterResult.allow();
    }
}
