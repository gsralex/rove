package com.gsralex.rove.core.skills;

import java.util.List;

public record Skill(String name, String description, String body, List<String> requires) {

    public Skill {
        requires = requires == null ? List.of() : List.copyOf(requires);
    }
}
