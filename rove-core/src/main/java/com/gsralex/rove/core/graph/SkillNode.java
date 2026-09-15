package com.gsralex.rove.core.graph;

import com.google.common.base.Preconditions;
import com.gsralex.rove.core.loop.FilterResult;
import com.gsralex.rove.core.loop.LoopContext;
import com.gsralex.rove.core.skills.Skill;
import com.gsralex.rove.core.skills.SkillRegistry;
import com.gsralex.rove.core.tool.ToolCall;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class SkillNode implements Node {

    private final String name;
    private final String skillName;
    private final SkillRegistry skills;
    private final Function<Map<String, Object>, Map<String, Object>> argsFrom;
    private final String outputKey;

    private SkillNode(
            String name,
            String skillName,
            SkillRegistry skills,
            Function<Map<String, Object>, Map<String, Object>> argsFrom,
            String outputKey) {
        this.name = name;
        this.skillName = skillName;
        this.skills = skills;
        this.argsFrom = argsFrom;
        this.outputKey = outputKey;
    }

    public static SkillNode of(String name, String skillName, SkillRegistry skills) {
        return new SkillNode(name, skillName, skills, s -> Map.of(), skillName);
    }

    public SkillNode argsFrom(Function<Map<String, Object>, Map<String, Object>> fn) {
        return new SkillNode(name, skillName, skills, fn, outputKey);
    }

    public SkillNode outputKey(String key) {
        return new SkillNode(name, skillName, skills, argsFrom, key);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void execute(Map<String, Object> state, LoopContext ctx) {
        SkillRegistry registry = skills != null ? skills : ctx.skills();
        Skill skill = registry == null ? null : registry.find(skillName).orElse(null);
        Preconditions.checkState(skill != null, "unknown skill %s", skillName);
        ToolCall call = new ToolCall("skill-" + name, "load_skill", "{\"name\":\"" + skillName + "\"}");
        FilterResult bt = ctx.beforeTool(call);
        if (!bt.allowed()) {
            state.put("error", bt.reason());
            return;
        }
        ctx.onToolCall(call);
        if (argsFrom != null) {
            state.put(name + ".args", argsFrom.apply(state));
        }
        for (String req : skill.requires()) {
            ctx.tools().get(req).ifPresent(t -> ctx.tools().register(t));
        }
        String key = outputKey == null ? skillName : outputKey;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", skill.name());
        payload.put("body", skill.body());
        payload.put("requires", skill.requires());
        state.put(key, payload);
        ctx.onToolResult("load_skill", skill.name());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Node n && Objects.equals(name, n.name());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
