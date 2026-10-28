package com.networksim.core;

import com.networksim.model.Link;
import com.networksim.model.NetworkEvent;
import com.networksim.model.Route;

import java.util.*;

public class NetworkSimulator {

    private final Map<String, Map<String, Link>> adjacency = new LinkedHashMap<>();
    private final Map<String, Link> links = new HashMap<>();
    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final Map<String, PriorityQueue<Route>> candidates = new HashMap<>();
    private final Map<String, Set<String>> linkDeps = new HashMap<>();
    private final Map<String, Set<String>> routerDeps = new HashMap<>();
    private final Map<String, String> activeRoutes = new HashMap<>();
    private final Set<String> failedLinks = new HashSet<>();
    private final Set<String> failedRouters = new HashSet<>();
    private final TreeMap<Integer, Queue<NetworkEvent>> scheduled = new TreeMap<>();
    private final Deque<NetworkEvent> undoStack = new ArrayDeque<>();
    private final List<String> history = new ArrayList<>();
    private final LinkedHashMap<String, String> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 32;
        }
    };
    private int clock = 0;

    private static String cleanId(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Router identifier cannot be empty");
        }
        return s.trim().toUpperCase(Locale.ROOT);
    }

    private void requireRouter(String routerId) {
        if (!adjacency.containsKey(routerId)) {
            throw new IllegalArgumentException("Router '" + routerId + "' does not exist in topology");
        }
    }

    private void log(String message) {
        history.add("[t=" + clock + "] " + message);
    }

    public void addRouter(String id) {
        id = cleanId(id);
        if (adjacency.putIfAbsent(id, new LinkedHashMap<>()) != null) {
            throw new IllegalArgumentException("Router '" + id + "' already exists");
        }
        log("Router added: " + id);
    }

    public void addLink(String a, String b, int cost) {
        a = cleanId(a);
        b = cleanId(b);
        requireRouter(a);
        requireRouter(b);

        Link link = new Link(a, b, cost);
        if (links.putIfAbsent(link.key(), link) != null) {
            throw new IllegalArgumentException("Link between " + a + " and " + b + " already exists");
        }

        adjacency.get(a).put(b, link);
        adjacency.get(b).put(a, link);
        log("Link added: " + link);
    }

    public void addRoute(String id, int priority, List<String> hops) {
        id = cleanId(id);
        if (routes.containsKey(id)) {
            throw new IllegalArgumentException("Route ID '" + id + "' already exists");
        }

        List<String> sanitized = new ArrayList<>();
        for (String h : hops) {
            sanitized.add(cleanId(h));
        }

        Route route = new Route(id, sanitized, priority);

        for (int i = 0; i < sanitized.size(); i++) {
            requireRouter(sanitized.get(i));
            if (i > 0) {
                String linkKey = Link.key(sanitized.get(i - 1), sanitized.get(i));
                if (!links.containsKey(linkKey)) {
                    throw new IllegalArgumentException("Missing link for route " + id + ": "
                            + sanitized.get(i - 1) + "-" + sanitized.get(i));
                }
            }
        }

        routes.put(id, route);

        candidates.computeIfAbsent(route.pair(), k -> new PriorityQueue<>(
                Comparator.comparingInt(Route::priority).thenComparing(Route::id)
        )).add(route);

        for (String hop : sanitized) {
            routerDeps.computeIfAbsent(hop, k -> new HashSet<>()).add(id);
        }

        for (int i = 1; i < sanitized.size(); i++) {
            String linkKey = Link.key(sanitized.get(i - 1), sanitized.get(i));
            linkDeps.computeIfAbsent(linkKey, k -> new HashSet<>()).add(id);
        }

        refreshActiveRoute(route.pair());
        log("Route configured: " + route);
    }

    public boolean isRouteOperational(Route route) {
        if (route == null) {
            return false;
        }
        for (String hop : route.hops()) {
            if (failedRouters.contains(hop)) {
                return false;
            }
        }
        for (int i = 1; i < route.hops().size(); i++) {
            String linkKey = Link.key(route.hops().get(i - 1), route.hops().get(i));
            if (failedLinks.contains(linkKey)) {
                return false;
            }
        }
        return true;
    }

    private void refreshActiveRoute(String pair) {
        PriorityQueue<Route> queue = candidates.get(pair);
        if (queue == null || queue.isEmpty()) {
            activeRoutes.remove(pair);
            cache.remove(pair);
            return;
        }

        Route best = null;
        for (Route r : queue) {
            if (isRouteOperational(r)) {
                if (best == null
                        || r.priority() < best.priority()
                        || (r.priority() == best.priority() && r.id().compareTo(best.id()) < 0)) {
                    best = r;
                }
            }
        }

        if (best == null) {
            activeRoutes.remove(pair);
        } else {
            activeRoutes.put(pair, best.id());
        }
        cache.remove(pair);
    }

    private void handleAffectedRoutes(Set<String> affectedRouteIds) {
        Set<String> pairs = new HashSet<>();
        for (String routeId : affectedRouteIds) {
            Route r = routes.get(routeId);
            if (r != null) {
                pairs.add(r.pair());
            }
        }

        for (String p : pairs) {
            String before = activeRoutes.get(p);
            refreshActiveRoute(p);
            String after = activeRoutes.get(p);

            if (!Objects.equals(before, after)) {
                log("Reroute [" + p + "]: " + (before == null ? "NO ROUTE" : before)
                        + " -> " + (after == null ? "NO BACKUP" : after));
            }
        }
    }

    public void failLink(String a, String b) {
        changeLinkState(a, b, true, true);
    }

    public void restoreLink(String a, String b) {
        changeLinkState(a, b, false, true);
    }

    private void changeLinkState(String a, String b, boolean fail, boolean recordUndo) {
        a = cleanId(a);
        b = cleanId(b);
        String key = Link.key(a, b);

        if (!links.containsKey(key)) {
            throw new IllegalArgumentException("Link " + a + "-" + b + " does not exist in topology");
        }

        boolean changed = fail ? failedLinks.add(key) : failedLinks.remove(key);
        if (!changed) {
            throw new IllegalStateException("Link " + a + "-" + b + " is already " + (fail ? "FAILED" : "OPERATIONAL"));
        }

        if (recordUndo) {
            undoStack.push(new NetworkEvent(clock, fail ? NetworkEvent.Type.RESTORE_LINK : NetworkEvent.Type.FAIL_LINK, a, b));
        }

        log((fail ? "FAILED" : "RESTORED") + " link " + a + "-" + b);
        handleAffectedRoutes(linkDeps.getOrDefault(key, Collections.emptySet()));
    }

    public void failRouter(String id) {
        changeRouterState(id, true, true);
    }

    public void restoreRouter(String id) {
        changeRouterState(id, false, true);
    }

    private void changeRouterState(String id, boolean fail, boolean recordUndo) {
        id = cleanId(id);
        requireRouter(id);

        boolean changed = fail ? failedRouters.add(id) : failedRouters.remove(id);
        if (!changed) {
            throw new IllegalStateException("Router " + id + " is already " + (fail ? "FAILED" : "OPERATIONAL"));
        }

        if (recordUndo) {
            undoStack.push(new NetworkEvent(clock, fail ? NetworkEvent.Type.RESTORE_ROUTER : NetworkEvent.Type.FAIL_ROUTER, id, null));
        }

        log((fail ? "FAILED" : "RESTORED") + " router " + id);
        handleAffectedRoutes(routerDeps.getOrDefault(id, Collections.emptySet()));
    }

    public void undo() {
        if (undoStack.isEmpty()) {
            throw new NoSuchElementException("Undo stack is empty: no operations to revert");
        }
        NetworkEvent inverse = undoStack.pop();
        log("UNDO executed: " + inverse.targetDescription());
        applyEvent(inverse, false);
    }

    public void schedule(int timestamp, NetworkEvent.Type type, String a, String b) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp must be non-negative: " + timestamp);
        }
        if (timestamp < clock) {
            throw new IllegalArgumentException("Cannot schedule in the past (requested t="
                    + timestamp + ", clock t=" + clock + ")");
        }

        a = cleanId(a);
        requireRouter(a);
        if (type.isLinkEvent()) {
            if (b == null || b.isBlank()) {
                throw new IllegalArgumentException("Link event requires both endpoints");
            }
            b = cleanId(b);
            requireRouter(b);
            String linkKey = Link.key(a, b);
            if (!links.containsKey(linkKey)) {
                throw new IllegalArgumentException("Link " + a + "-" + b + " does not exist");
            }
        }

        NetworkEvent event = new NetworkEvent(timestamp, type, a, b);
        scheduled.computeIfAbsent(timestamp, t -> new ArrayDeque<>()).add(event);
        log("SCHEDULED: " + event);
    }

    public String runNext() {
        if (scheduled.isEmpty()) {
            throw new NoSuchElementException("No pending scheduled events");
        }

        Map.Entry<Integer, Queue<NetworkEvent>> entry = scheduled.firstEntry();
        this.clock = entry.getKey();
        Queue<NetworkEvent> queue = entry.getValue();
        NetworkEvent event = queue.remove();

        if (queue.isEmpty()) {
            scheduled.pollFirstEntry();
        }

        try {
            applyEvent(event, true);
            return "Executed " + event;
        } catch (Exception ex) {
            String err = "Skipped " + event + ": " + ex.getMessage();
            log(err);
            return err;
        }
    }

    public int runAll() {
        int count = 0;
        while (!scheduled.isEmpty()) {
            runNext();
            count++;
        }
        return count;
    }

    private void applyEvent(NetworkEvent event, boolean recordUndo) {
        switch (event.type()) {
            case FAIL_LINK -> changeLinkState(event.a(), event.b(), true, recordUndo);
            case RESTORE_LINK -> changeLinkState(event.a(), event.b(), false, recordUndo);
            case FAIL_ROUTER -> changeRouterState(event.a(), true, recordUndo);
            case RESTORE_ROUTER -> changeRouterState(event.a(), false, recordUndo);
        }
    }

    public int getClock() {
        return clock;
    }

    public boolean isRouterFailed(String routerId) {
        if (routerId == null) return false;
        return failedRouters.contains(cleanId(routerId));
    }

    public boolean isLinkFailed(String a, String b) {
        if (a == null || b == null) return false;
        return failedLinks.contains(Link.key(a, b));
    }

    public Set<String> getRouters() {
        return Collections.unmodifiableSet(adjacency.keySet());
    }

    public Collection<Link> getLinks() {
        return Collections.unmodifiableCollection(links.values());
    }

    public Collection<Route> getRoutes() {
        return Collections.unmodifiableCollection(routes.values());
    }

    public Route getActiveRoute(String source, String destination) {
        String pair = cleanId(source) + "->" + cleanId(destination);
        String routeId = activeRoutes.get(pair);
        return routeId == null ? null : routes.get(routeId);
    }

    public List<NetworkEvent> getPendingEvents() {
        List<NetworkEvent> list = new ArrayList<>();
        for (Queue<NetworkEvent> q : scheduled.values()) {
            list.addAll(q);
        }
        return list;
    }

    public String routeStatus(String source, String destination) {
        String pair = cleanId(source) + "->" + cleanId(destination);
        String cached = cache.get(pair);
        if (cached != null) {
            return "[Cached] " + cached;
        }

        String activeId = activeRoutes.get(pair);
        String result;
        if (activeId == null) {
            result = "No operational configured route available for " + pair;
        } else {
            Route route = routes.get(activeId);
            result = "Active Route: " + route.id() + " (" + String.join(" -> ", route.hops())
                    + ") [Priority " + route.priority() + "]";
        }
        cache.put(pair, result);
        return result;
    }

    public String topologyReport() {
        StringBuilder sb = new StringBuilder("=== NETWORK TOPOLOGY ===\n");
        for (Map.Entry<String, Map<String, Link>> entry : adjacency.entrySet()) {
            String router = entry.getKey();
            sb.append("Router ").append(router)
              .append(failedRouters.contains(router) ? " [FAILED]" : " [UP]")
              .append(" -> Neighbors: ");
            if (entry.getValue().isEmpty()) {
                sb.append("None");
            } else {
                List<String> list = new ArrayList<>();
                for (Map.Entry<String, Link> n : entry.getValue().entrySet()) {
                    String status = failedLinks.contains(n.getValue().key()) ? "[FAILED]" : "[UP]";
                    list.add(n.getKey() + " (cost=" + n.getValue().cost() + " " + status + ")");
                }
                sb.append(String.join(", ", list));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public String routeReport() {
        StringBuilder sb = new StringBuilder("=== CONFIGURED ROUTES REPORT ===\n");
        for (Route r : routes.values()) {
            boolean op = isRouteOperational(r);
            boolean act = Objects.equals(activeRoutes.get(r.pair()), r.id());
            sb.append(String.format("%-4s %-28s Priority: %-2d Status: %-13s %s\n",
                    r.id(),
                    String.join("->", r.hops()),
                    r.priority(),
                    op ? "[OPERATIONAL]" : "[UNAVAILABLE]",
                    act ? ">>> ACTIVE ROUTE <<<" : ""
            ));
        }
        return sb.toString();
    }

    public String linkDependencies(String a, String b) {
        String key = Link.key(a, b);
        if (!links.containsKey(key)) {
            throw new IllegalArgumentException("Link " + a + "-" + b + " does not exist");
        }
        Set<String> dependent = new TreeSet<>(linkDeps.getOrDefault(key, Collections.emptySet()));
        if (dependent.isEmpty()) {
            return "No configured routes depend on link " + a + "-" + b;
        }
        return "Routes dependent on link " + a + "-" + b + ": " + dependent;
    }

    public String routerDependencies(String routerId) {
        routerId = cleanId(routerId);
        requireRouter(routerId);
        Set<String> dependent = new TreeSet<>(routerDeps.getOrDefault(routerId, Collections.emptySet()));
        if (dependent.isEmpty()) {
            return "No configured routes depend on router " + routerId;
        }
        return "Routes dependent on router " + routerId + ": " + dependent;
    }

    public String stats() {
        return "DATA STRUCTURE STATISTICS:\n"
                + "- Routers (Adjacency Map): " + adjacency.size() + " (" + failedRouters.size() + " failed)\n"
                + "- Links (HashMap/HashSet): " + links.size() + " (" + failedLinks.size() + " failed)\n"
                + "- Configured Routes: " + routes.size() + "\n"
                + "- Active Route Mappings: " + activeRoutes.size() + "\n"
                + "- LRU Cache Entries: " + cache.size() + " / 32 max\n"
                + "- Scheduled Event Timestamps (TreeMap): " + scheduled.size() + " (Pending: " + getPendingEvents().size() + ")\n"
                + "- Undo Operations (ArrayDeque Stack): " + undoStack.size() + "\n"
                + "- Simulation Clock: t=" + clock;
    }

    public String history() {
        if (history.isEmpty()) {
            return "Event history is empty.";
        }
        return "=== SIMULATION EVENT LOG ===\n" + String.join("\n", history);
    }

    public static NetworkSimulator demo() {
        NetworkSimulator sim = new NetworkSimulator();

        for (String r : List.of("A", "B", "C", "D", "E", "F")) {
            sim.addRouter(r);
        }

        sim.addLink("A", "B", 2);
        sim.addLink("B", "C", 2);
        sim.addLink("C", "D", 2);
        sim.addLink("A", "E", 3);
        sim.addLink("E", "F", 3);
        sim.addLink("F", "D", 3);
        sim.addLink("B", "E", 4);

        sim.addRoute("R1", 1, List.of("A", "B", "C", "D"));
        sim.addRoute("R2", 2, List.of("A", "E", "F", "D"));
        sim.addRoute("R3", 3, List.of("A", "B", "E", "F", "D"));

        return sim;
    }
}
