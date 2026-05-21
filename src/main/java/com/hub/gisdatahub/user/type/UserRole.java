package com.hub.gisdatahub.user.type;

public enum UserRole {
    USER("사용자"),
    RESEARCHER("연구원"),
    ADMIN("관리자");

    private final String method;

    UserRole(final String method) {
        this.method = method;
    }
}
