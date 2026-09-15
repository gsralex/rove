package com.gsralex.rove.core.skills;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class FileSkillRegistry implements SkillRegistry {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final Path dir;
    private final Path overlay;
    private final Map<String, Skill> index = new LinkedHashMap<>();

    public FileSkillRegistry(Path dir) {
        this(dir, null);
    }

    public FileSkillRegistry(Path dir, Path overlay) {
        this.dir = dir;
        this.overlay = overlay;
        reload();
    }

    public static FileSkillRegistry load(Path dir) {
        return new FileSkillRegistry(dir);
    }

    public Skill install(Path skillMd) {
        try {
            Path src = Files.isDirectory(skillMd) ? skillMd.resolve("SKILL.md") : skillMd;
            Skill parsed = parse(Files.readString(src));
            Path root = overlay != null ? overlay : dir;
            Path dest = root.resolve(parsed.name()).resolve("SKILL.md");
            Files.createDirectories(dest.getParent());
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            reload();
            return find(parsed.name()).orElse(parsed);
        } catch (IOException e) {
            throw new RuntimeException("install skill: " + skillMd, e);
        }
    }

    @Override
    public List<Skill> list() {
        return List.copyOf(index.values());
    }

    @Override
    public Optional<Skill> find(String name) {
        return Optional.ofNullable(index.get(name));
    }

    private void reload() {
        index.clear();
        scan(dir);
        scan(overlay);
    }

    private void scan(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> "SKILL.md".equals(p.getFileName().toString()))
                    .forEach(md -> {
                        try {
                            Skill s = parse(Files.readString(md));
                            index.put(s.name(), s);
                        } catch (IOException e) {
                            throw new RuntimeException("read " + md, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("scan " + root, e);
        }
    }

    static Skill parse(String raw) {
        String text = raw == null ? "" : raw;
        if (!text.startsWith("---")) {
            return new Skill("unnamed", "", text, List.of());
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return new Skill("unnamed", "", text, List.of());
        }
        try {
            Map<String, Object> meta = YAML.readValue(text.substring(3, end), MAP);
            String body = text.substring(end + 4).stripLeading();
            return new Skill(
                    str(meta.get("name"), "unnamed"),
                    str(meta.get("description"), ""),
                    body,
                    requires(meta.get("requires")));
        } catch (IOException e) {
            throw new RuntimeException("parse skill frontmatter", e);
        }
    }

    private static String str(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

    private static List<String> requires(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        if (raw instanceof String s && !s.isBlank()) {
            return List.of(s);
        }
        return List.of();
    }
}
