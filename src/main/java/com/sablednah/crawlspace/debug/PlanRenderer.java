package com.sablednah.crawlspace.debug;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Role;
import com.sablednah.crawlspace.plan.Room;

/**
 * Draws a plan as a top-down map: one panel per level, all cropped to the same
 * square so a stair down on one panel sits exactly where the stair up is on the
 * next. Used by the tests to put layouts in front of a person, which is the
 * only way to judge whether a dungeon looks good.
 *
 * <p>Uses AWT, headless. Never call it from a client: AWT and the game window
 * do not mix on every platform.</p>
 */
public final class PlanRenderer {

    private static final Color BG = new Color(0x15, 0x14, 0x18);
    private static final Color ROCK = new Color(0x1d, 0x1c, 0x21);
    private static final Color WALL = new Color(0x55, 0x52, 0x5c);
    private static final Color CORRIDOR = new Color(0xb5, 0xa4, 0x86);
    private static final Color DOOR = new Color(0x8f, 0x5b, 0x2a);
    private static final Color LOCKED = new Color(0xd6, 0x3c, 0x30);
    private static final Color SECRET = new Color(0xa0, 0x52, 0xc8);
    private static final Color PILLAR = new Color(0x3a, 0x38, 0x40);
    private static final Color STAIR_UP = new Color(0x5c, 0xc8, 0x6a);
    private static final Color STAIR_DOWN = new Color(0xf0, 0x9a, 0x2a);
    private static final Color PIT = new Color(0x05, 0x05, 0x05);
    private static final Color POOL = new Color(0x3d, 0x7f, 0xd0);
    private static final Color TEXT = new Color(0xee, 0xea, 0xe0);

    private PlanRenderer() {
    }

    /** One image of every level, {@code cols} panels wide. */
    public static BufferedImage render(DungeonPlan plan, int scale, int cols) {
        int[] crop = crop(plan.levels());
        int side = (crop[2] - crop[0] + 1) * scale;
        int title = 22;
        int pad = 10;
        int n = plan.levels().size();
        cols = Math.min(cols, n);
        int rows = (n + cols - 1) / cols;
        int legend = 26;
        int width = pad + cols * (side + pad);
        int height = title + 6 + rows * (side + title + pad) + legend;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, width, height);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString("Seed " + plan.seed() + "  ·  " + n + " levels", pad, 18);
        for (int i = 0; i < n; i++) {
            int ox = pad + (i % cols) * (side + pad);
            int oy = title + 6 + (i / cols) * (side + title + pad);
            LevelPlan level = plan.levels().get(i);
            g.setColor(TEXT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            String label = "Level " + (i + 1) + " — " + level.theme.name() + "  (" + level.rooms.size()
                    + " rooms, " + level.links.size() + " links"
                    + (level.attempts > 1 ? ", try " + level.attempts : "") + ")";
            g.drawString(label, ox, oy + 15);
            drawLevel(g, level, crop, scale, ox, oy + title);
        }
        drawLegend(g, pad, height - legend + 4);
        g.dispose();
        return img;
    }

    public static void write(DungeonPlan plan, int scale, int cols, File file) throws IOException {
        file.getParentFile().mkdirs();
        ImageIO.write(render(plan, scale, cols), "png", file);
    }

    /** {minX, minZ, maxX, maxZ}: a square around everything open on any level. */
    static int[] crop(List<LevelPlan> levels) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int lim = LevelPlan.RADIUS;
        for (LevelPlan level : levels) {
            for (int x = -lim; x <= lim; x++) {
                for (int z = -lim; z <= lim; z++) {
                    if (level.cell(x, z) != Cell.ROCK) {
                        minX = Math.min(minX, x);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        int size = Math.max(maxX - minX, maxZ - minZ) + 7;
        int cx = (minX + maxX) / 2;
        int cz = (minZ + maxZ) / 2;
        return new int[] {cx - size / 2, cz - size / 2, cx - size / 2 + size, cz - size / 2 + size};
    }

    private static void drawLevel(Graphics2D g, LevelPlan level, int[] crop, int s, int ox, int oy) {
        for (int x = crop[0]; x <= crop[2]; x++) {
            for (int z = crop[1]; z <= crop[3]; z++) {
                Color c = colour(level, x, z);
                g.setColor(c);
                g.fillRect(ox + (x - crop[0]) * s, oy + (z - crop[1]) * s, s, s);
            }
        }
        // Stair wells and pits get an outline so they read at a glance.
        g.setStroke(new BasicStroke(Math.max(1, s / 2f)));
        for (int[] p : level.stairsUp) {
            outline(g, p, crop, s, ox, oy, STAIR_UP.brighter());
        }
        for (int[] p : level.stairsDown) {
            outline(g, p, crop, s, ox, oy, STAIR_DOWN.brighter());
        }
        for (int[] p : level.pits) {
            outline(g, p, crop, s, ox, oy, LOCKED);
        }
        // Hidden traps: a small cross, red for darts, green for gas.
        for (int[] t : level.traps) {
            g.setColor(t[2] == 0 ? LOCKED : new Color(0x4c, 0xb0, 0x3a));
            int px = ox + (t[0] - crop[0]) * s;
            int pz = oy + (t[1] - crop[1]) * s;
            g.drawLine(px - s / 2, pz - s / 2, px + s + s / 2, pz + s + s / 2);
            g.drawLine(px + s + s / 2, pz - s / 2, px - s / 2, pz + s + s / 2);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(9, s * 3)));
        FontMetrics fm = g.getFontMetrics();
        for (Room r : level.rooms) {
            if (r.role == Role.ROOM || r.role == Role.HALL) {
                continue;
            }
            String t = String.valueOf(r.role.letter);
            int px = ox + (r.centerX() - crop[0]) * s + s / 2 - fm.stringWidth(t) / 2;
            int pz = oy + (r.centerZ() - crop[1]) * s + s / 2 + fm.getAscent() / 2 - 1;
            if (r.role == Role.ENTRY || r.role == Role.EXIT) {
                pz -= 3 * s; // above the stair well rather than on it
            }
            g.setColor(new Color(0, 0, 0, 150));
            g.drawString(t, px + 1, pz + 1);
            g.setColor(TEXT);
            g.drawString(t, px, pz);
        }
    }

    private static void outline(Graphics2D g, int[] p, int[] crop, int s, int ox, int oy, Color c) {
        g.setColor(c);
        g.drawRect(ox + (p[0] - 1 - crop[0]) * s, oy + (p[1] - 1 - crop[1]) * s, 3 * s, 3 * s);
    }

    private static Color colour(LevelPlan level, int x, int z) {
        Cell cell = level.cell(x, z);
        Color base = switch (cell) {
            case ROCK -> ROCK;
            case WALL -> WALL;
            case FLOOR -> roomColour(level, x, z);
            case CORRIDOR -> CORRIDOR;
            case DOOR -> DOOR;
            case ARCH -> CORRIDOR.darker();
            case DOOR_LOCKED -> LOCKED;
            case DOOR_SECRET -> SECRET;
            case PILLAR -> PILLAR;
            case STAIR_UP -> STAIR_UP;
            case STAIR_DOWN -> STAIR_DOWN;
            case PIT -> PIT;
            case POOL -> POOL;
        };
        if (cell == Cell.FLOOR || cell == Cell.CORRIDOR) {
            return shade(base, level.height(x, z));
        }
        return base;
    }

    private static Color roomColour(LevelPlan level, int x, int z) {
        Room r = level.room(level.region(x, z));
        if (r == null) {
            return CORRIDOR;
        }
        return switch (r.role) {
            case ENTRY -> new Color(0x9c, 0xc9, 0xa4);
            case EXIT -> new Color(0xe6, 0xc0, 0x8c);
            case LAIR -> new Color(0xc9, 0x86, 0x80);
            case TREASURE -> new Color(0xe8, 0xd6, 0x6e);
            case SECRET -> new Color(0xb9, 0x98, 0xd6);
            case KEY -> new Color(0xe0, 0x9f, 0xa0);
            case SHRINE -> new Color(0x9f, 0xc4, 0xd8);
            case GUARD -> new Color(0xc8, 0xb6, 0xa8);
            case HALL -> new Color(0xd4, 0xcc, 0xbc);
            default -> new Color(0xcf, 0xc9, 0xbd);
        };
    }

    /** Lighter for raised floors, darker for sunken ones. */
    private static Color shade(Color c, int h) {
        double f = 1 + 0.11 * h;
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    private static void drawLegend(Graphics2D g, int x, int y) {
        Object[][] items = {
                {STAIR_UP, "stair up"}, {STAIR_DOWN, "stair down"}, {PIT, "pit (lands in a pool)"},
                {POOL, "pool"}, {DOOR, "door"}, {CORRIDOR.darker(), "arch"}, {LOCKED, "locked (lever in K)"}, {SECRET, "secret"},
                {CORRIDOR, "corridor (lighter = higher)"}, {PILLAR, "pillar"},
                {LOCKED.darker(), "x dart trap"}, {new Color(0x4c, 0xb0, 0x3a), "x gas trap"},
        };
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        FontMetrics fm = g.getFontMetrics();
        for (Object[] item : items) {
            g.setColor((Color) item[0]);
            g.fillRect(x, y + 3, 12, 12);
            g.setColor(WALL);
            g.drawRect(x, y + 3, 12, 12);
            g.setColor(TEXT);
            String t = (String) item[1];
            g.drawString(t, x + 16, y + 13);
            x += 16 + fm.stringWidth(t) + 14;
        }
        g.drawString("E entry · X exit · L lair · G guard · T treasure · S secret · W shrine · K lever", x, y + 13);
    }
}
