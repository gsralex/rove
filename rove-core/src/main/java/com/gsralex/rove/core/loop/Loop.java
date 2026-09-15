package com.gsralex.rove.core.loop;

import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.tool.Tool;
import java.util.List;

public interface Loop {

    String id();

    String run(List<Message> messages, List<Tool> tools);
}
