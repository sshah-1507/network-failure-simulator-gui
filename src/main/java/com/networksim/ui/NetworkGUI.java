package com.networksim.ui;

import com.networksim.core.NetworkSimulator;
import com.networksim.model.Link;
import com.networksim.model.NetworkEvent;
import com.networksim.model.Route;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;

public class NetworkGUI extends JFrame {

    private NetworkSimulator sim;
    private final NetworkPanel networkPanel;

    private final JTextArea consoleOutput = new JTextArea();
    private final JLabel clockLabel = new JLabel("Simulation Clock: t=0");
    private final JLabel activeRouteLabel = new JLabel("Active Route (A->D): R1 [A -> B -> C -> D]");
    private final JLabel pendingCountLabel = new JLabel("Pending Events: 0");

    private final JComboBox<String> routerComboBox = new JComboBox<>();
    private final JLabel routerStatusLabel = new JLabel("Status: UP");

    private final JComboBox<String> linkComboBox = new JComboBox<>();
    private final JLabel linkStatusLabel = new JLabel("Status: UP");

    private final JTextField fromField = new JTextField("A", 3);
    private final JTextField toField = new JTextField("D", 3);

    private final JTextField timestampField = new JTextField("10", 4);
    private final JComboBox<NetworkEvent.Type> eventTypeCombo = new JComboBox<>(NetworkEvent.Type.values());
    private final JComboBox<String> eventTargetCombo = new JComboBox<>();

    private static final Color HEADER_BG = new Color(15, 23, 42);
    private static final Color SIDEBAR_BG = new Color(241, 245, 249);
    private static final Color ACCENT_BTN = new Color(37, 99, 235);
    private static final Color FAIL_BTN = new Color(220, 38, 38);
    private static final Color RESTORE_BTN = new Color(22, 163, 74);
    private static final Color NEUTRAL_BTN = new Color(71, 85, 105);

    public NetworkGUI() {
        super("Network Failure Simulation & Rerouting — DSA Edition");
        this.sim = NetworkSimulator.demo();
        this.networkPanel = new NetworkPanel(sim);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        setSize(1240, 820);
        setMinimumSize(new Dimension(1080, 720));
        setLocationRelativeTo(null);

        add(createTopBanner(), BorderLayout.NORTH);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, networkPanel, createConsolePanel());
        centerSplit.setResizeWeight(0.65);
        centerSplit.setDividerSize(6);
        add(centerSplit, BorderLayout.CENTER);

        JScrollPane sidebarScroll = new JScrollPane(createSidebarControls());
        sidebarScroll.setBorder(null);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(14);
        sidebarScroll.setPreferredSize(new Dimension(380, 700));
        add(sidebarScroll, BorderLayout.WEST);

        refreshDynamicLists();
        updateStatusBanner();
        printLog("Network Failure Simulator initialized successfully with 6-node demo topology.\n"
                + "Preconfigured routes loaded: R1 (Priority 1), R2 (Priority 2), R3 (Priority 3).\n"
                + "Select any router or link to test failure failover.");
    }

    private JPanel createTopBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(HEADER_BG);
        banner.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLabel = new JLabel("NETWORK FAILURE SIMULATOR & REROUTING");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        JPanel statusSubpanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statusSubpanel.setOpaque(false);

        clockLabel.setForeground(new Color(254, 240, 138));
        clockLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        activeRouteLabel.setForeground(new Color(110, 231, 183));
        activeRouteLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        pendingCountLabel.setForeground(new Color(224, 231, 255));
        pendingCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        statusSubpanel.add(clockLabel);
        statusSubpanel.add(new JSeparator(SwingConstants.VERTICAL));
        statusSubpanel.add(activeRouteLabel);
        statusSubpanel.add(new JSeparator(SwingConstants.VERTICAL));
        statusSubpanel.add(pendingCountLabel);

        banner.add(titleLabel, BorderLayout.WEST);
        banner.add(statusSubpanel, BorderLayout.EAST);
        return banner;
    }

    private JPanel createConsolePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder(new LineBorder(new Color(203, 213, 225)), "Simulation Output & Log Console"));

        consoleOutput.setEditable(false);
        consoleOutput.setFont(new Font("Consolas", Font.PLAIN, 12));
        consoleOutput.setBackground(new Color(250, 250, 250));
        consoleOutput.setForeground(new Color(15, 23, 42));
        consoleOutput.setRows(9);

        JScrollPane scroll = new JScrollPane(consoleOutput);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSidebarControls() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(SIDEBAR_BG);
        sidebar.setBorder(new EmptyBorder(10, 12, 10, 12));

        sidebar.add(createSectionRouterControls());
        sidebar.add(Box.createVerticalStrut(10));

        sidebar.add(createSectionLinkControls());
        sidebar.add(Box.createVerticalStrut(10));

        sidebar.add(createSectionRouteMonitoring());
        sidebar.add(Box.createVerticalStrut(10));

        sidebar.add(createSectionScheduler());
        sidebar.add(Box.createVerticalStrut(10));

        sidebar.add(createSectionReportsAndUndo());

        return sidebar;
    }

    private JPanel createSectionRouterControls() {
        JPanel p = createSectionBox("1. Dynamic Router Management");

        JPanel selectRow = new JPanel(new BorderLayout(5, 5));
        selectRow.setOpaque(false);
        selectRow.add(new JLabel("Select Router:"), BorderLayout.WEST);
        selectRow.add(routerComboBox, BorderLayout.CENTER);
        selectRow.add(routerStatusLabel, BorderLayout.EAST);

        routerComboBox.addActionListener(e -> updateSelectedRouterStatus());

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 6, 0));
        btnRow.setOpaque(false);

        JButton failBtn = createStyledButton("Fail Router", FAIL_BTN);
        failBtn.addActionListener(e -> handleFailRouter());

        JButton restoreBtn = createStyledButton("Restore", RESTORE_BTN);
        restoreBtn.addActionListener(e -> handleRestoreRouter());

        JButton depsBtn = createStyledButton("Dependencies", NEUTRAL_BTN);
        depsBtn.addActionListener(e -> handleRouterDependencies());

        btnRow.add(failBtn);
        btnRow.add(restoreBtn);
        btnRow.add(depsBtn);

        p.add(selectRow);
        p.add(Box.createVerticalStrut(6));
        p.add(btnRow);
        return p;
    }

    private JPanel createSectionLinkControls() {
        JPanel p = createSectionBox("2. Dynamic Link Management");

        JPanel selectRow = new JPanel(new BorderLayout(5, 5));
        selectRow.setOpaque(false);
        selectRow.add(new JLabel("Select Link:"), BorderLayout.WEST);
        selectRow.add(linkComboBox, BorderLayout.CENTER);
        selectRow.add(linkStatusLabel, BorderLayout.EAST);

        linkComboBox.addActionListener(e -> updateSelectedLinkStatus());

        JPanel btnRow = new JPanel(new GridLayout(1, 3, 6, 0));
        btnRow.setOpaque(false);

        JButton failBtn = createStyledButton("Fail Link", FAIL_BTN);
        failBtn.addActionListener(e -> handleFailLink());

        JButton restoreBtn = createStyledButton("Restore", RESTORE_BTN);
        restoreBtn.addActionListener(e -> handleRestoreLink());

        JButton depsBtn = createStyledButton("Dependencies", NEUTRAL_BTN);
        depsBtn.addActionListener(e -> handleLinkDependencies());

        btnRow.add(failBtn);
        btnRow.add(restoreBtn);
        btnRow.add(depsBtn);

        p.add(selectRow);
        p.add(Box.createVerticalStrut(6));
        p.add(btnRow);
        return p;
    }

    private JPanel createSectionRouteMonitoring() {
        JPanel p = createSectionBox("3. Route Monitoring & Pair Query");

        JPanel queryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        queryRow.setOpaque(false);
        queryRow.add(new JLabel("From:"));
        queryRow.add(fromField);
        queryRow.add(new JLabel("To:"));
        queryRow.add(toField);

        JButton checkBtn = createStyledButton("Check Active Route", ACCENT_BTN);
        checkBtn.addActionListener(e -> handleCheckRoute());
        queryRow.add(checkBtn);

        JButton showAllBtn = createStyledButton("Show All Routes", NEUTRAL_BTN);
        showAllBtn.addActionListener(e -> printLog(sim.routeReport()));

        p.add(queryRow);
        p.add(Box.createVerticalStrut(4));
        p.add(showAllBtn);
        return p;
    }

    private JPanel createSectionScheduler() {
        JPanel p = createSectionBox("4. Event Scheduler (TreeMap + Queue)");

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row1.setOpaque(false);
        row1.add(new JLabel("Timestamp (t >= 0):"));
        row1.add(timestampField);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2.setOpaque(false);
        row2.add(new JLabel("Type:"));
        row2.add(eventTypeCombo);
        row2.add(new JLabel("Target:"));
        row2.add(eventTargetCombo);

        eventTypeCombo.addActionListener(e -> updateSchedulerTargetCombo());

        JPanel row3 = new JPanel(new GridLayout(1, 3, 6, 0));
        row3.setOpaque(false);

        JButton scheduleBtn = createStyledButton("Schedule Event", ACCENT_BTN);
        scheduleBtn.addActionListener(e -> handleScheduleEvent());

        JButton runNextBtn = createStyledButton("Run Next", new Color(13, 148, 136));
        runNextBtn.addActionListener(e -> handleRunNextEvent());

        JButton runAllBtn = createStyledButton("Run All", new Color(15, 118, 110));
        runAllBtn.addActionListener(e -> handleRunAllEvents());

        row3.add(scheduleBtn);
        row3.add(runNextBtn);
        row3.add(runAllBtn);

        JButton showPendingBtn = createStyledButton("Show Pending Queue", NEUTRAL_BTN);
        showPendingBtn.addActionListener(e -> handleShowPendingEvents());

        p.add(row1);
        p.add(row2);
        p.add(Box.createVerticalStrut(6));
        p.add(row3);
        p.add(Box.createVerticalStrut(6));
        p.add(showPendingBtn);
        return p;
    }

    private JPanel createSectionReportsAndUndo() {
        JPanel p = createSectionBox("5. Reports, Undo & Reset");

        JPanel grid = new JPanel(new GridLayout(2, 2, 6, 6));
        grid.setOpaque(false);

        JButton statsBtn = createStyledButton("Data Structure Stats", NEUTRAL_BTN);
        statsBtn.addActionListener(e -> printLog(sim.stats()));

        JButton historyBtn = createStyledButton("Event History", NEUTRAL_BTN);
        historyBtn.addActionListener(e -> printLog(sim.history()));

        JButton undoBtn = createStyledButton("Undo Last Operation", new Color(217, 119, 6));
        undoBtn.addActionListener(e -> handleUndo());

        JButton resetBtn = createStyledButton("Reset Demo", FAIL_BTN);
        resetBtn.addActionListener(e -> handleReset());

        grid.add(statsBtn);
        grid.add(historyBtn);
        grid.add(undoBtn);
        grid.add(resetBtn);

        p.add(grid);
        return p;
    }

    private void refreshDynamicLists() {
        String currentRouter = (String) routerComboBox.getSelectedItem();
        routerComboBox.removeAllItems();
        for (String r : sim.getRouters()) {
            routerComboBox.addItem(r);
        }
        if (currentRouter != null && sim.getRouters().contains(currentRouter)) {
            routerComboBox.setSelectedItem(currentRouter);
        }

        String currentLink = (String) linkComboBox.getSelectedItem();
        linkComboBox.removeAllItems();
        for (Link link : sim.getLinks()) {
            linkComboBox.addItem(link.displayName());
        }
        if (currentLink != null) {
            linkComboBox.setSelectedItem(currentLink);
        }

        updateSelectedRouterStatus();
        updateSelectedLinkStatus();
        updateSchedulerTargetCombo();
    }

    private void updateSelectedRouterStatus() {
        String selected = (String) routerComboBox.getSelectedItem();
        if (selected == null) {
            routerStatusLabel.setText("");
            return;
        }
        boolean failed = sim.isRouterFailed(selected);
        routerStatusLabel.setText(failed ? "FAILED" : "UP");
        routerStatusLabel.setForeground(failed ? FAIL_BTN : RESTORE_BTN);
        routerStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }

    private void updateSelectedLinkStatus() {
        String selected = (String) linkComboBox.getSelectedItem();
        if (selected == null) {
            linkStatusLabel.setText("");
            return;
        }
        String[] parts = selected.split("-");
        if (parts.length == 2) {
            boolean failed = sim.isLinkFailed(parts[0], parts[1]);
            linkStatusLabel.setText(failed ? "FAILED" : "UP");
            linkStatusLabel.setForeground(failed ? FAIL_BTN : RESTORE_BTN);
            linkStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        }
    }

    private void updateSchedulerTargetCombo() {
        NetworkEvent.Type type = (NetworkEvent.Type) eventTypeCombo.getSelectedItem();
        if (type == null) return;

        eventTargetCombo.removeAllItems();
        if (type.isLinkEvent()) {
            for (Link link : sim.getLinks()) {
                eventTargetCombo.addItem(link.displayName());
            }
        } else {
            for (String router : sim.getRouters()) {
                eventTargetCombo.addItem(router);
            }
        }
    }

    private void updateStatusBanner() {
        clockLabel.setText("Simulation Clock: t=" + sim.getClock());

        Route active = sim.getActiveRoute("A", "D");
        if (active != null) {
            activeRouteLabel.setText("Active Route (A->D): " + active.id() + " [" + String.join(" -> ", active.hops()) + "]");
            activeRouteLabel.setForeground(new Color(110, 231, 183));
        } else {
            activeRouteLabel.setText("Active Route (A->D): [NO OPERATIONAL ROUTE]");
            activeRouteLabel.setForeground(new Color(248, 113, 113));
        }

        List<NetworkEvent> pending = sim.getPendingEvents();
        pendingCountLabel.setText("Pending Events: " + pending.size());
    }

    private void syncUI() {
        updateSelectedRouterStatus();
        updateSelectedLinkStatus();
        updateStatusBanner();
        networkPanel.repaint();
    }

    private void handleFailRouter() {
        String selected = (String) routerComboBox.getSelectedItem();
        if (selected == null) return;
        try {
            sim.failRouter(selected);
            printLog("ACTION: Failed Router " + selected + ".");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Router Failure Error", ex.getMessage());
        }
        syncUI();
    }

    private void handleRestoreRouter() {
        String selected = (String) routerComboBox.getSelectedItem();
        if (selected == null) return;
        try {
            sim.restoreRouter(selected);
            printLog("ACTION: Restored Router " + selected + ".");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Router Restoration Error", ex.getMessage());
        }
        syncUI();
    }

    private void handleRouterDependencies() {
        String selected = (String) routerComboBox.getSelectedItem();
        if (selected == null) return;
        try {
            printLog(sim.routerDependencies(selected));
        } catch (Exception ex) {
            showError("Dependency Query Error", ex.getMessage());
        }
    }

    private void handleFailLink() {
        String selected = (String) linkComboBox.getSelectedItem();
        if (selected == null) return;
        String[] parts = selected.split("-");
        if (parts.length != 2) return;
        try {
            sim.failLink(parts[0], parts[1]);
            printLog("ACTION: Failed Link " + selected + ".");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Link Failure Error", ex.getMessage());
        }
        syncUI();
    }

    private void handleRestoreLink() {
        String selected = (String) linkComboBox.getSelectedItem();
        if (selected == null) return;
        String[] parts = selected.split("-");
        if (parts.length != 2) return;
        try {
            sim.restoreLink(parts[0], parts[1]);
            printLog("ACTION: Restored Link " + selected + ".");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Link Restoration Error", ex.getMessage());
        }
        syncUI();
    }

    private void handleLinkDependencies() {
        String selected = (String) linkComboBox.getSelectedItem();
        if (selected == null) return;
        String[] parts = selected.split("-");
        if (parts.length != 2) return;
        try {
            printLog(sim.linkDependencies(parts[0], parts[1]));
        } catch (Exception ex) {
            showError("Dependency Query Error", ex.getMessage());
        }
    }

    private void handleCheckRoute() {
        String src = fromField.getText().trim();
        String dst = toField.getText().trim();
        if (src.isEmpty() || dst.isEmpty()) {
            showError("Invalid Input", "Please provide both Source and Destination router IDs.");
            return;
        }
        try {
            String status = sim.routeStatus(src, dst);
            printLog("QUERY: " + status);
        } catch (Exception ex) {
            showError("Route Query Error", ex.getMessage());
        }
    }

    private void handleScheduleEvent() {
        String timeStr = timestampField.getText().trim();
        int timestamp;
        try {
            timestamp = Integer.parseInt(timeStr);
        } catch (NumberFormatException nfe) {
            showError("Invalid Timestamp Input",
                    "Timestamp must be a valid non-negative integer number (e.g. 10, 25).\n"
                            + "You entered: '" + timeStr + "'\n"
                            + "Please do not enter link or router names into the timestamp field.");
            return;
        }

        if (timestamp < 0) {
            showError("Invalid Timestamp", "Simulation timestamp cannot be negative: " + timestamp);
            return;
        }

        if (timestamp < sim.getClock()) {
            showError("Timestamp in Past",
                    "Cannot schedule event at t=" + timestamp + " because the current simulation clock is already at t="
                            + sim.getClock() + ".\nPlease choose a timestamp >= " + sim.getClock() + ".");
            return;
        }

        NetworkEvent.Type type = (NetworkEvent.Type) eventTypeCombo.getSelectedItem();
        String target = (String) eventTargetCombo.getSelectedItem();
        if (target == null) {
            showError("Missing Target", "Please select a target router or link.");
            return;
        }

        String a, b = null;
        if (type.isLinkEvent()) {
            String[] parts = target.split("-");
            a = parts[0];
            b = parts[1];
        } else {
            a = target;
        }

        try {
            sim.schedule(timestamp, type, a, b);
            printLog("EVENT SCHEDULED: " + type.getDisplay() + " on " + target + " at t=" + timestamp + ".");
            syncUI();
        } catch (Exception ex) {
            showError("Scheduling Error", ex.getMessage());
        }
    }

    private void handleRunNextEvent() {
        try {
            String result = sim.runNext();
            printLog("EXECUTION: " + result);
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Scheduler Notice", ex.getMessage());
        }
        syncUI();
    }

    private void handleRunAllEvents() {
        try {
            int count = sim.runAll();
            printLog("EXECUTION: Finished processing all " + count + " pending event(s).");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Scheduler Error", ex.getMessage());
        }
        syncUI();
    }

    private void handleShowPendingEvents() {
        List<NetworkEvent> pending = sim.getPendingEvents();
        if (pending.isEmpty()) {
            printLog("No pending events in the scheduler queue (Current clock t=" + sim.getClock() + ").");
            return;
        }
        StringBuilder sb = new StringBuilder("=== PENDING SCHEDULED EVENTS ===\n");
        sb.append("Current Clock: t=").append(sim.getClock()).append("\n");
        for (int i = 0; i < pending.size(); i++) {
            sb.append(String.format(" %2d. %s\n", (i + 1), pending.get(i).toString()));
        }
        printLog(sb.toString());
    }

    private void handleUndo() {
        try {
            sim.undo();
            printLog("ACTION: Reverted last operation.");
            printLog(sim.routeReport());
        } catch (Exception ex) {
            showError("Undo Notice", ex.getMessage());
        }
        syncUI();
    }

    private void handleReset() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Reset simulator to initial demo topology?",
                "Confirm Demo Reset",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            this.sim = NetworkSimulator.demo();
            this.networkPanel.setSimulator(sim);
            refreshDynamicLists();
            syncUI();
            printLog("Simulator reset to initial demo state (Clock t=0).");
        }
    }

    private void printLog(String message) {
        consoleOutput.append(message + "\n\n");
        consoleOutput.setCaretPosition(consoleOutput.getDocument().getLength());
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
        printLog("[ERROR] " + title + ": " + message);
    }

    private JPanel createSectionBox(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(203, 213, 225)), title, TitledBorder.LEFT, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 12), new Color(15, 23, 42)),
                new EmptyBorder(6, 8, 8, 8)
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(360, 220));
        return p;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return b;
    }

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            NetworkGUI gui = new NetworkGUI();
            gui.setVisible(true);
        });
    }
}
