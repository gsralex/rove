package com.gsralex.rove.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gsralex.rove.core.llm.LlmConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public final class CliConfig {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path DIR = Path.of(System.getProperty("user.home"), ".rove");
    private static final Path FILE = DIR.resolve("llm");
    private static final Path LEGACY = DIR.resolve("config.json");

    private String baseUrl = "https://api.deepseek.com";
    private String apiKey = "";
    private String model = "deepseek-flash";

    public String baseUrl() {
        return baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public CliConfig baseUrl(String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.baseUrl = baseUrl.trim();
        }
        return this;
    }

    public CliConfig apiKey(String apiKey) {
        if (apiKey != null) {
            this.apiKey = apiKey.trim();
        }
        return this;
    }

    public CliConfig model(String model) {
        if (model != null && !model.isBlank()) {
            this.model = model.trim();
        }
        return this;
    }

    public LlmConfig toLlmConfig() {
        return new LlmConfig(baseUrl, apiKey, model);
    }

    public static Path path() {
        return FILE;
    }

    public static CliConfig load() {
        return loadFile().applyEnv();
    }

    public static CliConfig loadFile() {
        CliConfig cfg = new CliConfig();
        Path src = Files.isRegularFile(FILE) ? FILE : (Files.isRegularFile(LEGACY) ? LEGACY : null);
        if (src == null) {
            return cfg;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = JSON.readValue(src.toFile(), Map.class);
            cfg.baseUrl(str(map.get("baseUrl")));
            cfg.apiKey(str(map.get("apiKey")));
            cfg.model(str(map.get("model")));
        } catch (Exception e) {
            throw new RuntimeException("failed to read " + src + ": " + e.getMessage(), e);
        }
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("baseUrl", baseUrl);
            map.put("apiKey", apiKey);
            map.put("model", model);
            JSON.writerWithDefaultPrettyPrinter().writeValue(FILE.toFile(), map);
        } catch (Exception e) {
            throw new RuntimeException("failed to write " + FILE + ": " + e.getMessage(), e);
        }
    }

    public void promptAndSave(Scanner in) {
        System.out.println("配置 LLM（写入 " + FILE + "）");
        System.out.println("直接回车保留当前值。");
        baseUrl(prompt(in, "API Base URL", baseUrl));
        apiKey(promptSecret(in, "API Key", apiKey));
        model(prompt(in, "Model", model));
        if (!hasApiKey()) {
            throw new IllegalStateException("apiKey 不能为空");
        }
        save();
        System.out.println("已保存 " + FILE);
    }

    private static String prompt(Scanner in, String label, String current) {
        System.out.print(label + " [" + (current == null || current.isBlank() ? "empty" : current) + "]: ");
        if (!in.hasNextLine()) {
            return current;
        }
        String line = in.nextLine().trim();
        return line.isEmpty() ? current : line;
    }

    private static String promptSecret(Scanner in, String label, String current) {
        String shown = current == null || current.isBlank() ? "empty" : mask(current);
        System.out.print(label + " [" + shown + "]: ");
        if (!in.hasNextLine()) {
            return current;
        }
        String line = in.nextLine().trim();
        return line.isEmpty() ? current : line;
    }

    static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "(empty)";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "…" + apiKey.substring(apiKey.length() - 4);
    }

    private CliConfig applyEnv() {
        String envKey = firstEnv("ROVE_API_KEY", "OPENAI_API_KEY");
        if (envKey != null) {
            apiKey = envKey;
        }
        String envUrl = System.getenv("ROVE_BASE_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            baseUrl = envUrl.trim();
        }
        String envModel = System.getenv("ROVE_MODEL");
        if (envModel != null && !envModel.isBlank()) {
            model = envModel.trim();
        }
        return this;
    }

    private static String firstEnv(String... keys) {
        for (String k : keys) {
            String v = System.getenv(k);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
