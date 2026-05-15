package com.hub.gisdatahub.user.type;

public enum UserStatus {
    ACTIVATE("활성"),
    INACTIVATE("비활성");

    private final String method;

    UserStatus(final String method) {
        this.method = method;
    }
}
