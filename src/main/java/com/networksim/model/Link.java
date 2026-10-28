package com.networksim.model;

import java.util.Objects;

public record Link(String a, String b, int cost) {

    public Link {
        Objects.requireNonNull(a);
        Objects.requireNonNull(b);
        if (a.equalsIgnoreCase(b)) {
            throw new IllegalArgumentException("Link cannot connect a router to itself (" + a + ")");
        }
        if (cost < 1) {
            throw new IllegalArgumentException("Cost must be positive: " + cost);
        }
    }

    public String key() {
        return key(a, b);
    }

    public static String key(String a, String b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Router endpoints cannot be null");
        }
        String u1 = a.trim().toUpperCase();
        String u2 = b.trim().toUpperCase();
        return u1.compareTo(u2) <= 0 ? u1 + "|" + u2 : u2 + "|" + u1;
    }

    public String displayName() {
        String u1 = a.trim().toUpperCase();
        String u2 = b.trim().toUpperCase();
        return u1.compareTo(u2) <= 0 ? u1 + "-" + u2 : u2 + "-" + u1;
    }

    @Override
    public String toString() {
        return displayName() + " (cost: " + cost + ")";
    }
}
