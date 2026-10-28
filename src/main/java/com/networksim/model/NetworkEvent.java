package com.networksim.model;

import java.util.Objects;

public record NetworkEvent(int time, Type type, String a, String b) {

    public enum Type {
        FAIL_LINK("Fail Link"),
        RESTORE_LINK("Restore Link"),
        FAIL_ROUTER("Fail Router"),
        RESTORE_ROUTER("Restore Router");

        private final String display;

        Type(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }

        public boolean isLinkEvent() {
            return this == FAIL_LINK || this == RESTORE_LINK;
        }

        public boolean isRouterEvent() {
            return this == FAIL_ROUTER || this == RESTORE_ROUTER;
        }
    }

    public NetworkEvent {
        if (time < 0) {
            throw new IllegalArgumentException("Simulation timestamp cannot be negative: " + time);
        }
        Objects.requireNonNull(type);
        Objects.requireNonNull(a);
        if (type.isLinkEvent() && (b == null || b.isBlank())) {
            throw new IllegalArgumentException("Link event requires both endpoints");
        }
        a = a.trim().toUpperCase();
        if (b != null) {
            b = b.trim().toUpperCase();
        }
    }

    public String targetDescription() {
        if (type.isLinkEvent()) {
            return "Link " + Link.key(a, b).replace('|', '-');
        } else {
            return "Router " + a;
        }
    }

    @Override
    public String toString() {
        return "[t=" + time + "] " + type.getDisplay() + " -> " + targetDescription();
    }
}
