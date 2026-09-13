package com.gsralex.rove.core.skills;

/** Agent Skill (SKILL.md): name + description in frontmatter; markdown after that is body. */
public interface Skill {

    String name();

    String description();

    String body();
}
