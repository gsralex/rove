# Rove 文档

> 与 `rove-core` 代码同步：改公开 API 时改这些 md。

## 阅读顺序

| 顺序 | 文档 | 内容 |
| --- | --- | --- |
| 1 | [agent-graph-design.md](./agent-graph-design.md) | Agent / Graph / Skill / 边界类型 / 订票示例 / 要点表 |
| 2 | [runtime.md](./runtime.md) | Graph vs AgentLoop；Filter / Listener 挂载与钩子时序 |
| 3 | [agent-loop.md](./agent-loop.md) | AgentLoop 主循环、失败语义、入口、与 LlmNode 分工、验收 |
| — | [a2a.md](./a2a.md) | A2A 协议与 Rove 映射（本版不做，存档） |

## 一句话分工

| 文档 | 一句话 |
| --- | --- |
| **agent-graph-design** | 「钉死步骤」怎么建：Node / Edge / Skill / Agent 职责 |
| **runtime** | 「怎么跑」：谁调谁、钩子打在哪、UI/门禁怎么挂 |
| **agent-loop** | 「模型自己规划」那条 while 循环 |
| **a2a** | 以后 Agent↔Agent；本版不实现 |

## 要点速查

读正文时可用下面当目录锚：

- **唯一入口**：`agent.run(...)`（无单独 `chat`）→ [agent-graph-design §要点](./agent-graph-design.md#要点)
- **三种实例形态**：只用 Graph / 只用 AgentLoop / Graph 嵌 `AgentLoopNode`
- **LlmNode**：至多一轮；多轮自选工具 → 必须 `AgentLoopNode` → [agent-loop §9](./agent-loop.md#9-与-llmnode--skillnode-的分工防混用)
- **Filter / Listener**：只挂在 Agent，向下传 → [runtime §挂载](./runtime.md#用户在哪里挂-filter--listener)
- **Filter deny / 空响应 / 空 choices**：错误给用户 + **等下一条 user message** → [agent-loop §6](./agent-loop.md#6-边界与失败语义)
- **Skill / MCP / Tool**：Skill 按需 `skill_search` / `load_skill`；MCP 按配置名 `mount_mcp`；Tool **用户定义**；**LLM 按 name 自选** → [agent-graph-design §Skill](./agent-graph-design.md#skill-与动态获取)
- **无独立 Memory**：会话=`messages`，图=`state`，context=当次拼装 → [agent-graph-design §State](./agent-graph-design.md#state--messages--context本版不做独立-memory)
- **A2A**：本版不做 → [a2a.md](./a2a.md)

## 交叉链接约定

- 各文顶部有「相关文档」块，底部有同一套索引表（与本页一致）。
- 细节以各文正文为准；冲突时以 **agent-graph-design 的要点表** + **agent-loop §6 失败表** 为准。
