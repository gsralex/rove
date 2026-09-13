package com.gsralex.rove.core.loop;

public record FilterResult(boolean allowed, String reason) {

    public static FilterResult allow() {
        return new FilterResult(true, "");
    }

    public static FilterResult deny(String reason) {
        return new FilterResult(false, reason);
    }
}
