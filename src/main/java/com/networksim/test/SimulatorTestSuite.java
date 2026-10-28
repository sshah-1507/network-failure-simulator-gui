package com.networksim.test;

import com.networksim.core.NetworkSimulator;
import com.networksim.model.NetworkEvent;

import java.util.List;

public class SimulatorTestSuite {

    private static int testsRun = 0;
    private static int testsPassed = 0;

    private static void check(String testName, boolean condition, String details) {
        testsRun++;
        if (condition) {
            testsPassed++;
            System.out.println("[PASS] " + testName);
        } else {
            System.err.println("[FAIL] " + testName + " -> " + details);
        }
    }

    public static void main(String[] args) {
        System.out.println("====================================================");
        System.out.println("  Running Simulator Test Suite");
        System.out.println("====================================================");

        testInitialTopologyAndRoutePriority();
        testDynamicLinkFailureAndRerouting();
        testDynamicRouterFailureAndRerouting();
        testMultiFailureAllRoutesDown();
        testRestorationAndReactivationOfPreferredRoute();
        testUndoStackLIFO();
        testSchedulerValidationAndChronologicalOrder();
        testSameTimestampFIFOQueue();
        testLRUCacheBehavior();

        System.out.println("====================================================");
        System.out.println("  SUMMARY: " + testsPassed + " / " + testsRun + " tests passed.");
        System.out.println("====================================================");

        if (testsPassed != testsRun) {
            System.exit(1);
        }
    }

    private static void testInitialTopologyAndRoutePriority() {
        NetworkSimulator sim = NetworkSimulator.demo();
        check("Initial Clock is 0", sim.getClock() == 0, "Clock: " + sim.getClock());
        check("Initial Active Route A->D is R1", "R1".equals(sim.getActiveRoute("A", "D").id()), "");
        check("R1 priority is 1", sim.getActiveRoute("A", "D").priority() == 1, "");
        check("Routers count is 6", sim.getRouters().size() == 6, "");
        check("Links count is 7", sim.getLinks().size() == 7, "");
        check("Routes count is 3", sim.getRoutes().size() == 3, "");
    }

    private static void testDynamicLinkFailureAndRerouting() {
        NetworkSimulator sim = NetworkSimulator.demo();

        sim.failLink("B", "C");
        check("Link B-C is failed", sim.isLinkFailed("B", "C"), "");
        check("Reroute from R1 to R2 upon B-C failure", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        boolean caughtDup = false;
        try {
            sim.failLink("B", "C");
        } catch (IllegalStateException e) {
            caughtDup = true;
        }
        check("Prevent duplicate link failure", caughtDup, "");

        sim.failLink("E", "F");
        check("No operational route left when B-C and E-F are failed",
                sim.getActiveRoute("A", "D") == null, "");
    }

    private static void testDynamicRouterFailureAndRerouting() {
        NetworkSimulator sim = NetworkSimulator.demo();

        sim.failRouter("C");
        check("Router C is failed", sim.isRouterFailed("C"), "");
        check("Reroute to R2 upon Router C failure", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        boolean caughtDup = false;
        try {
            sim.failRouter("C");
        } catch (IllegalStateException e) {
            caughtDup = true;
        }
        check("Prevent duplicate router failure", caughtDup, "");
    }

    private static void testMultiFailureAllRoutesDown() {
        NetworkSimulator sim = NetworkSimulator.demo();
        sim.failRouter("D");
        check("All routes unavailable if destination D fails", sim.getActiveRoute("A", "D") == null, "");
        check("Query report indicates no route", sim.routeStatus("A", "D").contains("No operational"), "");
    }

    private static void testRestorationAndReactivationOfPreferredRoute() {
        NetworkSimulator sim = NetworkSimulator.demo();

        sim.failLink("B", "C");
        check("Switched to backup R2", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        sim.restoreLink("B", "C");
        check("Link B-C is restored", !sim.isLinkFailed("B", "C"), "");
        check("R1 (Priority 1) reactivated upon restoration",
                "R1".equals(sim.getActiveRoute("A", "D").id()), "");
    }

    private static void testUndoStackLIFO() {
        NetworkSimulator sim = NetworkSimulator.demo();

        sim.failLink("B", "C");
        check("Active route R2 after failing B-C", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        sim.failRouter("E");
        check("Router E failed", sim.isRouterFailed("E"), "");

        sim.undo();
        check("Undo 1 restores Router E", !sim.isRouterFailed("E"), "");
        check("Active route still R2", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        sim.undo();
        check("Undo 2 restores Link B-C", !sim.isLinkFailed("B", "C"), "");
        check("Active route restored to R1", "R1".equals(sim.getActiveRoute("A", "D").id()), "");
    }

    private static void testSchedulerValidationAndChronologicalOrder() {
        NetworkSimulator sim = NetworkSimulator.demo();

        boolean caughtNeg = false;
        try {
            sim.schedule(-5, NetworkEvent.Type.FAIL_LINK, "B", "C");
        } catch (IllegalArgumentException e) {
            caughtNeg = true;
        }
        check("Reject negative timestamp", caughtNeg, "");

        sim.schedule(20, NetworkEvent.Type.RESTORE_LINK, "B", "C");
        sim.schedule(10, NetworkEvent.Type.FAIL_LINK, "B", "C");
        sim.schedule(15, NetworkEvent.Type.FAIL_ROUTER, "E", null);

        check("Pending event count is 3", sim.getPendingEvents().size() == 3, "");

        sim.runNext();
        check("Clock advanced to t=10", sim.getClock() == 10, "");
        check("Link B-C failed at t=10", sim.isLinkFailed("B", "C"), "");
        check("Active route switched to R2 at t=10", "R2".equals(sim.getActiveRoute("A", "D").id()), "");

        boolean caughtPast = false;
        try {
            sim.schedule(5, NetworkEvent.Type.FAIL_ROUTER, "A", null);
        } catch (IllegalArgumentException e) {
            caughtPast = true;
        }
        check("Reject scheduling in past", caughtPast, "");

        sim.runNext();
        check("Clock advanced to t=15", sim.getClock() == 15, "");
        check("Router E failed at t=15", sim.isRouterFailed("E"), "");

        sim.runNext();
        check("Clock advanced to t=20", sim.getClock() == 20, "");
        check("Link B-C restored at t=20", !sim.isLinkFailed("B", "C"), "");
        check("Preferred route R1 reactivated at t=20", "R1".equals(sim.getActiveRoute("A", "D").id()), "");
        check("Pending events empty", sim.getPendingEvents().isEmpty(), "");
    }

    private static void testSameTimestampFIFOQueue() {
        NetworkSimulator sim = NetworkSimulator.demo();

        sim.schedule(10, NetworkEvent.Type.FAIL_ROUTER, "B", null);
        sim.schedule(10, NetworkEvent.Type.RESTORE_ROUTER, "B", null);

        List<NetworkEvent> pending = sim.getPendingEvents();
        check("Both events recorded at t=10", pending.size() == 2, "");
        check("Event 1 is FAIL_ROUTER", pending.get(0).type() == NetworkEvent.Type.FAIL_ROUTER, "");
        check("Event 2 is RESTORE_ROUTER", pending.get(1).type() == NetworkEvent.Type.RESTORE_ROUTER, "");

        sim.runNext();
        check("Router B failed after Event 1", sim.isRouterFailed("B"), "");

        sim.runNext();
        check("Router B restored after Event 2", !sim.isRouterFailed("B"), "");
        check("Active route is R1 after FIFO sequence", "R1".equals(sim.getActiveRoute("A", "D").id()), "");
    }

    private static void testLRUCacheBehavior() {
        NetworkSimulator sim = NetworkSimulator.demo();

        String res1 = sim.routeStatus("A", "D");
        check("First query computes status", !res1.startsWith("[Cached]"), "");

        String res2 = sim.routeStatus("A", "D");
        check("Second query returns from LRU Cache", res2.startsWith("[Cached]"), "");

        sim.failLink("B", "C");
        String res3 = sim.routeStatus("A", "D");
        check("Modification invalidates cache", !res3.startsWith("[Cached]") && res3.contains("R2"), "");
    }
}
