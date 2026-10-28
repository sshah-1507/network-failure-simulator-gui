package com.networksim.ui;

import com.networksim.core.NetworkSimulator;
import com.networksim.model.Link;
import com.networksim.model.Route;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.*;
import java.util.List;

public class NetworkPanel extends JPanel {

    private NetworkSimulator sim;
    private final Map<String, Point> defaultPositions = new LinkedHashMap<>();

    private static final Color BG_COLOR = new Color(248, 250, 252);
    private static final Color ROUTER_UP_FILL = new Color(224, 242, 254);
    private static final Color ROUTER_UP_BORDER = new Color(2, 132, 199);
    private static final Color ROUTER_FAILED_FILL = new Color(254, 226, 226);
    private static final Color ROUTER_FAILED_BORDER = new Color(220, 38, 38);
    private static final Color LINK_UP_COLOR = new Color(148, 163, 184);
    private static final Color LINK_FAILED_COLOR = new Color(239, 68, 68);
    private static final Color ACTIVE_ROUTE_COLOR = new Color(16, 185, 129);
    private static final Color TEXT_DARK = new Color(30, 41, 59);

    public NetworkPanel(NetworkSimulator simulator) {
        this.sim = simulator;
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(650, 460));

        defaultPositions.put("A", new Point(100, 230));
        defaultPositions.put("B", new Point(250, 110));
        defaultPositions.put("C", new Point(430, 110));
        defaultPositions.put("D", new Point(570, 230));
        defaultPositions.put("E", new Point(250, 350));
        defaultPositions.put("F", new Point(430, 350));
    }

    public void setSimulator(NetworkSimulator simulator) {
        this.sim = simulator;
        repaint();
    }

    private Point getRouterPosition(String routerId, List<String> allRouters, int width, int height) {
        if (defaultPositions.containsKey(routerId)) {
            return defaultPositions.get(routerId);
        }
        int index = allRouters.indexOf(routerId);
        if (index == -1) index = 0;
        int n = Math.max(allRouters.size(), 1);
        double angle = 2 * Math.PI * index / n;
        int cx = width / 2;
        int cy = height / 2;
        int radius = Math.min(width, height) / 3;
        return new Point((int) (cx + radius * Math.cos(angle)), (int) (cy + radius * Math.sin(angle)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        List<String> routerList = new ArrayList<>(sim.getRouters());
        Route activeRoute = sim.getActiveRoute("A", "D");

        for (Link link : sim.getLinks()) {
            Point p1 = getRouterPosition(link.a(), routerList, w, h);
            Point p2 = getRouterPosition(link.b(), routerList, w, h);

            boolean isFailed = sim.isLinkFailed(link.a(), link.b());
            boolean onActiveRoute = (activeRoute != null && activeRoute.containsLink(link.a(), link.b()));

            if (isFailed) {
                Stroke dashed = new BasicStroke(4.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{9.0f, 6.0f}, 0.0f);
                g2.setStroke(dashed);
                g2.setColor(LINK_FAILED_COLOR);
            } else if (onActiveRoute) {
                g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(ACTIVE_ROUTE_COLOR);
            } else {
                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(LINK_UP_COLOR);
            }

            g2.draw(new Line2D.Double(p1.x, p1.y, p2.x, p2.y));

            int midX = (p1.x + p2.x) / 2;
            int midY = (p1.y + p2.y) / 2;

            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            String costLabel = link.displayName() + " (" + link.cost() + ")";
            if (isFailed) {
                costLabel += " [FAILED]";
            }

            FontMetrics fm = g2.getFontMetrics();
            int strW = fm.stringWidth(costLabel);

            g2.setColor(new Color(255, 255, 255, 210));
            g2.fillRoundRect(midX - strW / 2 - 3, midY - 14, strW + 6, 16, 4, 4);

            g2.setColor(isFailed ? LINK_FAILED_COLOR : (onActiveRoute ? new Color(5, 120, 80) : TEXT_DARK));
            g2.drawString(costLabel, midX - strW / 2, midY - 2);
        }

        int nodeRadius = 26;
        for (String routerId : routerList) {
            Point p = getRouterPosition(routerId, routerList, w, h);
            boolean isFailed = sim.isRouterFailed(routerId);
            boolean onActiveRoute = (activeRoute != null && activeRoute.containsRouter(routerId));

            if (onActiveRoute && !isFailed) {
                g2.setColor(new Color(16, 185, 129, 60));
                g2.fill(new Ellipse2D.Double(p.x - nodeRadius - 6, p.y - nodeRadius - 6, (nodeRadius + 6) * 2, (nodeRadius + 6) * 2));
            }

            g2.setColor(isFailed ? ROUTER_FAILED_FILL : (onActiveRoute ? new Color(209, 250, 229) : ROUTER_UP_FILL));
            g2.fill(new Ellipse2D.Double(p.x - nodeRadius, p.y - nodeRadius, nodeRadius * 2, nodeRadius * 2));

            g2.setStroke(new BasicStroke(isFailed ? 3.5f : (onActiveRoute ? 3.0f : 2.5f)));
            g2.setColor(isFailed ? ROUTER_FAILED_BORDER : (onActiveRoute ? ACTIVE_ROUTE_COLOR : ROUTER_UP_BORDER));
            g2.draw(new Ellipse2D.Double(p.x - nodeRadius, p.y - nodeRadius, nodeRadius * 2, nodeRadius * 2));

            g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
            FontMetrics fm = g2.getFontMetrics();
            int sw = fm.stringWidth(routerId);
            int sh = fm.getAscent();

            g2.setColor(isFailed ? ROUTER_FAILED_BORDER : TEXT_DARK);
            g2.drawString(routerId, p.x - sw / 2, p.y + sh / 2 - 3);

            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            String tag = isFailed ? "FAILED" : "UP";
            FontMetrics tagFm = g2.getFontMetrics();
            int tagW = tagFm.stringWidth(tag);
            g2.setColor(isFailed ? ROUTER_FAILED_BORDER : new Color(100, 116, 139));
            g2.drawString(tag, p.x - tagW / 2, p.y + nodeRadius + 14);
        }

        drawLegend(g2, w, h, activeRoute);
    }

    private void drawLegend(Graphics2D g2, int w, int h, Route activeRoute) {
        int lx = 15;
        int ly = h - 60;
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(lx, ly, 380, 50, 6, 6);
        g2.setColor(new Color(203, 213, 225));
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(lx, ly, 380, 50, 6, 6);

        g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        g2.setColor(TEXT_DARK);

        g2.setColor(ROUTER_UP_BORDER);
        g2.fillOval(lx + 10, ly + 10, 10, 10);
        g2.setColor(TEXT_DARK);
        g2.drawString("Operational", lx + 26, ly + 19);

        g2.setColor(ROUTER_FAILED_BORDER);
        g2.fillOval(lx + 105, ly + 10, 10, 10);
        g2.setColor(TEXT_DARK);
        g2.drawString("Failed (Red)", lx + 121, ly + 19);

        g2.setColor(ACTIVE_ROUTE_COLOR);
        g2.fillOval(lx + 200, ly + 10, 10, 10);
        g2.setColor(TEXT_DARK);
        String activeInfo = "Active Route: " + (activeRoute != null ? activeRoute.id() : "None");
        g2.drawString(activeInfo, lx + 216, ly + 19);

        g2.setColor(new Color(100, 116, 139));
        g2.drawString("Simulates backup route selection upon component failure", lx + 10, ly + 38);
    }
}
