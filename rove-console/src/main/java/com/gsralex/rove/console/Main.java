package com.gsralex.rove.console;

import com.gsralex.rove.core.agent.Agent;
import com.gsralex.rove.core.common.Message;
import com.gsralex.rove.core.common.Role;
import com.gsralex.rove.core.llm.LlmClient;
import com.gsralex.rove.core.loop.AgentLoop;
import com.gsralex.rove.core.loop.Listener;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.tool.BashTool;
import com.gsralex.rove.core.tool.ToolCall;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        List<String> argv = new ArrayList<>();
        if (args != null) {
            for (String a : args) {
                if (a != null) {
                    argv.add(a);
                }
            }
        }
        if (argv.isEmpty() || "chat".equals(argv.getFirst())) {
            chat();
            return;
        }
        String cmd = argv.getFirst();
        if ("config".equals(cmd) || "llm".equals(cmd)) {
            config(argv.subList(1, argv.size()));
            return;
        }
        if ("help".equals(cmd) || "-h".equals(cmd) || "--help".equals(cmd)) {
            help();
            return;
        }
        System.err.println("unknown command: " + cmd);
        help();
        System.exit(1);
    }

    private static void config(List<String> args) {
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            if (args.isEmpty()) {
                interactiveConfig(in);
                return;
            }
            String sub = args.getFirst();
            if ("show".equals(sub)) {
                printConfig(CliConfig.load());
                return;
            }
            if ("set".equals(sub)) {
                if (args.size() == 1) {
                    interactiveConfig(in);
                    return;
                }
                CliConfig cfg = CliConfig.loadFile();
                for (int i = 1; i < args.size(); i++) {
                    String a = args.get(i);
                    if ("--base-url".equals(a) && i + 1 < args.size()) {
                        cfg.baseUrl(args.get(++i));
                    } else if ("--api-key".equals(a) && i + 1 < args.size()) {
                        cfg.apiKey(args.get(++i));
                    } else if ("--model".equals(a) && i + 1 < args.size()) {
                        cfg.model(args.get(++i));
                    } else if ("--stream".equals(a) && i + 1 < args.size()) {
                        cfg.stream(Boolean.parseBoolean(args.get(++i)));
                    } else {
                        System.err.println("unknown option: " + a);
                        System.err.println(
                                "usage: rove config set [--base-url URL] [--api-key KEY] [--model MODEL] [--stream true|false]");
                        System.exit(1);
                    }
                }
                if (!cfg.hasApiKey()) {
                    System.err.println("apiKey 不能为空");
                    System.exit(1);
                }
                cfg.save();
                System.out.println("saved " + CliConfig.path());
                printConfig(cfg);
                return;
            }
            System.err.println("usage: rove config | rove llm | rove config show | rove config set ...");
            System.exit(1);
        }
    }

    private static void interactiveConfig(Scanner in) {
        CliConfig cfg = CliConfig.loadFile();
        cfg.promptAndSave(in);
        printConfig(cfg);
    }

    private static void printConfig(CliConfig cfg) {
        System.out.println("file:    " + CliConfig.path());
        System.out.println("baseUrl: " + cfg.baseUrl());
        System.out.println("model:   " + cfg.model());
        System.out.println("apiKey:  " + CliConfig.mask(cfg.apiKey()));
        System.out.println("stream:  " + cfg.stream());
    }

    private static void chat() {
        try (Scanner in = new Scanner(System.in, StandardCharsets.UTF_8)) {
            CliConfig cfg = ensureLlm(in);
            if (!BashTool.available()) {
                System.err.println("本机找不到 bash（需要 macOS/Linux，或 Windows 上 PATH 中有 Git Bash/WSL）。");
                System.exit(1);
            }
            int[] streamedChars = new int[1];
            Agent agent = buildAgent(cfg, streamedChars);

            System.out.println("rove chat · model=" + cfg.model() + " · bash 已挂载");
            System.out.println("模型会自行决定是否调用 bash。");
            System.out.println("命令: /config 改 LLM · /llm 同 /config · exit 退出");

            int cursor = 0;
            while (true) {
                System.out.print("\nyou> ");
                if (!in.hasNextLine()) {
                    break;
                }
                String line = in.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    break;
                }
                if ("/config".equals(line) || "/llm".equals(line)) {
                    try {
                        interactiveConfig(in);
                        cfg = CliConfig.load();
                        agent = buildAgent(cfg, streamedChars);
                        cursor = agent.messages().size();
                        System.out.println("已用新配置重建会话。");
                    } catch (RuntimeException e) {
                        System.err.println("config failed: " + e.getMessage());
                    }
                    continue;
                }
                try {
                    streamedChars[0] = 0;
                    agent.run(line);
                    List<Message> messages = agent.messages();
                    if (streamedChars[0] > 0) {
                        System.out.println();
                    } else {
                        printAssistant(messages, cursor);
                    }
                    cursor = messages.size();
                } catch (RuntimeException e) {
                    System.err.println("error: " + e.getMessage());
                }
            }
        }
    }

    private static CliConfig ensureLlm(Scanner in) {
        CliConfig cfg = CliConfig.load();
        if (cfg.hasApiKey()) {
            return cfg;
        }
        System.out.println("尚未配置 LLM，先设置 API Base URL / API Key / Model。");
        CliConfig file = CliConfig.loadFile();
        file.promptAndSave(in);
        return CliConfig.load();
    }

    private static Agent buildAgent(CliConfig cfg, int[] streamedChars) {
        LlmClient llm = new LlmClient(cfg.toLlmConfig());
        LoopContext context = new LoopContext(llm).stream(cfg.stream());
        AgentLoop loop = new AgentLoop(context, 24);
        Path workspace = Path.of(context.id()).toAbsolutePath();
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new RuntimeException("cannot create agent workspace " + workspace + ": " + e.getMessage(), e);
        }
        System.out.println("workspace: " + workspace);
        return Agent.builder()
                .name("rove-cli")
                .llm(llm)
                .loop(loop)
                .system("""
                        你是 Rove CLI 助手，可以调用 bash 工具在本机执行命令。
                        需要查文件、跑命令、收集信息时自行选择 bash；不必事事请示。
                        危险操作（删除、覆盖重要文件、外发敏感数据）先征得用户确认。
                        用简短中文回复。""")
                .tool(new BashTool(workspace))
                .listener(new Listener() {
                    @Override
                    public void onToken(String token) {
                        streamedChars[0] += token.length();
                        System.out.print(token);
                        System.out.flush();
                    }

                    @Override
                    public void onToolCall(ToolCall call) {
                        System.out.printf("%n→ %s %s%n", call.name(), call.args());
                    }

                    @Override
                    public void onToolResult(String toolName, String result) {
                        System.out.println("← " + clip(result, 1000));
                    }

                    @Override
                    public void onError(Throwable error) {
                        System.err.println("! " + error.getMessage());
                    }
                })
                .build();
    }

    private static void printAssistant(List<Message> messages, int from) {
        for (int i = from; i < messages.size(); i++) {
            Message m = messages.get(i);
            if (m.role() != Role.ASSISTANT) {
                continue;
            }
            if (m.content() != null && !m.content().isBlank()) {
                System.out.println("assistant> " + m.content());
            }
        }
    }

    private static void help() {
        System.out.println("""
                rove — Rove CLI（AgentLoop + Llm + BashTool）

                  rove / rove chat     多轮对话（无配置时会交互询问 API）
                  rove config          交互配置 LLM（写入 ~/.rove/llm）
                  rove llm             同上
                  rove config show     查看当前配置
                  rove config set \\
                    --base-url URL \\
                    --api-key KEY \\
                    --model MODEL      非交互写入
                    --stream true|false  是否流式输出（默认 true）
                  对话中 /config|/llm  随时改配置并重建会话
                  rove help

                本地文件: ~/.rove/llm
                环境变量可覆盖: ROVE_API_KEY / OPENAI_API_KEY、ROVE_BASE_URL、ROVE_MODEL""");
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }
}
