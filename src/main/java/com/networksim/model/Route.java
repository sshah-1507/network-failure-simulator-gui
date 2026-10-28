package com.networksim.model;

import java.util.List;
import java.util.Objects;

public record Route(String id, List<String> hops, int priority) {

    public Route {
        Objects.requireNonNull(id);
        Objects.requireNonNull(hops);
        if (hops.size() < 2) {
            throw new IllegalArgumentException("A route requires at least 2 hops");
        }
        if (priority < 1) {
            throw new IllegalArgumentException("Priority must be at least 1: " + priority);
        }
        hops = List.copyOf(hops);
    }

    public String source() {
        return hops.get(0);
    }

    public String destination() {
        return hops.get(hops.size() - 1);
    }

    public String pair() {
        return source() + "->" + destination();
    }

    public boolean containsRouter(String routerId) {
        if (routerId == null) {
            return false;
        }
        String target = routerId.trim().toUpperCase();
        for (String hop : hops) {
            if (hop.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsLink(String a, String b) {
        String targetKey = Link.key(a, b);
        for (int i = 1; i < hops.size(); i++) {
            if (Link.key(hops.get(i - 1), hops.get(i)).equals(targetKey)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return id + " [" + String.join(" -> ", hops) + "] (Priority " + priority + ")";
    }
}
