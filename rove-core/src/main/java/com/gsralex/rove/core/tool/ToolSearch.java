package com.gsralex.rove.core.tool;

import java.util.List;

/** Rank/filter tools by a natural-language or keyword query. */
public interface ToolSearch {

    List<Tool> search(String query);
}
