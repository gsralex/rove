package com.gsralex.rove.core.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSkillRegistryTest {

    @Test
    void parseFrontmatterAndBody() {
        Skill s = FileSkillRegistry.parse("""
                ---
                name: weather
                description: look up weather
                requires:
                  - curl
                  - jq
                ---
                # Weather

                Call curl then jq.
                """);
        assertEquals("weather", s.name());
        assertEquals("look up weather", s.description());
        assertEquals(List.of("curl", "jq"), s.requires());
        assertTrue(s.body().contains("Call curl then jq."));
    }

    @Test
    void parseWithoutFrontmatter() {
        Skill s = FileSkillRegistry.parse("just markdown");
        assertEquals("unnamed", s.name());
        assertEquals("", s.description());
        assertEquals(List.of(), s.requires());
        assertEquals("just markdown", s.body());
    }

    @Test
    void parseRequiresAsString() {
        Skill s = FileSkillRegistry.parse("""
                ---
                name: one
                description: d
                requires: bash
                ---
                body
                """);
        assertEquals(List.of("bash"), s.requires());
    }

    @Test
    void walksNestedSkillMd(@TempDir Path dir) throws Exception {
        writeSkill(dir.resolve("a/SKILL.md"), "a", "top", "A");
        writeSkill(dir.resolve("deep/nested/b/SKILL.md"), "b", "nested", "B");
        FileSkillRegistry reg = FileSkillRegistry.load(dir);
        assertEquals(2, reg.list().size());
        assertEquals("top", reg.find("a").orElseThrow().description());
        assertEquals("B", reg.find("b").orElseThrow().body().trim());
    }

    @Test
    void overlayReplacesByName(@TempDir Path base, @TempDir Path overlay) throws Exception {
        writeSkill(base.resolve("weather/SKILL.md"), "weather", "base desc", "base body");
        writeSkill(base.resolve("other/SKILL.md"), "other", "keep", "other body");
        writeSkill(overlay.resolve("weather/SKILL.md"), "weather", "overlay desc", "overlay body");
        FileSkillRegistry reg = new FileSkillRegistry(base, overlay);
        assertEquals(2, reg.list().size());
        Skill weather = reg.find("weather").orElseThrow();
        assertEquals("overlay desc", weather.description());
        assertEquals("overlay body", weather.body().trim());
        assertEquals("keep", reg.find("other").orElseThrow().description());
    }

    @Test
    void installCopiesIntoOverlayAndReloads(@TempDir Path dir, @TempDir Path overlay) throws Exception {
        Path src = Files.createTempFile("skill-", ".md");
        Files.writeString(src, """
                ---
                name: installed
                description: from install
                ---
                installed body
                """);
        FileSkillRegistry reg = new FileSkillRegistry(dir, overlay);
        assertTrue(reg.list().isEmpty());
        Skill installed = reg.install(src);
        assertEquals("installed", installed.name());
        assertEquals("from install", installed.description());
        assertTrue(Files.isRegularFile(overlay.resolve("installed/SKILL.md")));
        assertEquals(
                "installed body", reg.find("installed").orElseThrow().body().trim());
    }

    @Test
    void installIntoDirWhenNoOverlay(@TempDir Path dir) throws Exception {
        Path srcDir = Files.createTempDirectory("skill-src");
        writeSkill(srcDir.resolve("SKILL.md"), "pack", "p", "pack body");
        FileSkillRegistry reg = FileSkillRegistry.load(dir);
        reg.install(srcDir);
        assertTrue(Files.isRegularFile(dir.resolve("pack/SKILL.md")));
        assertEquals("pack body", reg.find("pack").orElseThrow().body().trim());
    }

    private static void writeSkill(Path path, String name, String description, String body) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, """
                ---
                name: %s
                description: %s
                ---
                %s
                """.formatted(name, description, body));
    }
}
