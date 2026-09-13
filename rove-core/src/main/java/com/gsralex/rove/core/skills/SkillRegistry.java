package com.gsralex.rove.core.skills;

import java.util.List;
import java.util.Optional;

public interface SkillRegistry {

    List<Skill> list();

    Optional<Skill> find(String name);
}
