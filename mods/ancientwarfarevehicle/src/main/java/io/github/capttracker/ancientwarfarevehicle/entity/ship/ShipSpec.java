/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the fixed design of one big ship.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.ship;

import java.util.ArrayList;
import java.util.List;

/**
 * The fixed design of one ship type: the outline of its hull as rows of cells, how high the hull and its rails stand, where the masts and the
 * hardpoints are and how it is driven. Everything is in blocks, in the ship's own frame: {@code forward} is along the bow (positive), {@code horizontal}
 * to the side (positive is the {@code yaw + 90} side, the one the seat offsets use), the origin is the middle of the hull at its keel.
 *
 * <p>The hull is a grid of 1 x 1 cells, a row for each block of length, from the bow to the stern ({@link #rows} holds the width of each row). Every
 * cell is one {@link ShipPart} with an axis aligned box; as the ship turns the cells move round the middle, so the boxes follow the hull and players can
 * walk on them. A cell is {@link Kind#DECK} (walkable, the top of the hull), {@link Kind#RAIL} (the hull and a rail above it) or {@link Kind#MAST}
 * (the hull and a mast). The edge cells of a row are rails unless the row is a gangway.
 */
public final class ShipSpec {
    /** What a cell of the hull is. */
    public enum Kind { DECK, RAIL, MAST }

    /** The weapon a hardpoint takes: it decides the shot, the sound and the model. */
    public enum Weapon { BALLISTA, CANNON }

    /** One part of the hull: the middle of its cell and the height of its box above the keel. */
    public record Cell(float forward, float horizontal, Kind kind, float top) {}

    public record Mast(float forward, float horizontal, float height) {}

    /**
     * A turret mount. {@code centerYaw} is the middle of its arc, {@code arc} half the width of the arc, both in degrees from the bow (positive turns to
     * the side of negative {@code horizontal}, as vehicle yaw does); {@code pitchMin} and {@code pitchMax} are the elevation limits.
     */
    public record Hardpoint(Weapon weapon, float forward, float horizontal, float vertical, float centerYaw, float arc, float pitchMin, float pitchMax) {}

    /** The most cells a ship may have: every ship entity carries this many (unused) part entities, see {@link ShipHelper}. */
    public static final int MAX_PARTS = 64;

    public final String name;
    /** The width in cells of each row, bow first. */
    public final int[] rows;
    /** How deep the keel is below the waterline, and how high the deck is above it. */
    public final float draft;
    public final float deckAbove;
    public final float railHeight;
    private final List<Integer> gangwayRows = new ArrayList<>();
    public final List<Mast> masts = new ArrayList<>();
    public final List<Hardpoint> hardpoints = new ArrayList<>();
    private final List<Cell> cells = new ArrayList<>();

    /** Fractions of the type's top speed: what the helmsman's own effort gives, what the sails give at full wind from astern, and what all rowers give. */
    public float drivePower = 0.f;
    public float sailPower = 0.f;
    public float rowPower = 0.f;
    /** How fast the ship gains speed and loses it, in blocks per tick every tick; how fast it gets into and out of a turn, as a fraction of the top turn rate per tick. */
    public float accel = 0.003f;
    public float decel = 0.004f;
    public float turnAccel = 0.06f;
    /** The bow ram: damage at full speed, 0 for no ram; how far in front of the bow it reaches. */
    public float ramDamage = 0.f;
    public float ramReach = 1.2f;
    /** The height of the masts' collision box over the deck. */
    public float mastBox = 3.0f;

    public ShipSpec(String name, int[] rows, float draft, float deckAbove, float railHeight) {
        this.name = name;
        this.rows = rows.clone();
        this.draft = draft;
        this.deckAbove = deckAbove;
        this.railHeight = railHeight;
    }

    public ShipSpec gangway(int... rowIndices) {
        for (int row : rowIndices) {
            gangwayRows.add(row);
        }
        return this;
    }

    public ShipSpec mast(float forward, float horizontal, float height) {
        masts.add(new Mast(forward, horizontal, height));
        return this;
    }

    public ShipSpec hardpoint(Weapon weapon, float forward, float horizontal, float vertical, float centerYaw, float arc, float pitchMin, float pitchMax) {
        hardpoints.add(new Hardpoint(weapon, forward, horizontal, vertical, centerYaw, arc, pitchMin, pitchMax));
        return this;
    }

    public ShipSpec power(float drive, float sail, float row) {
        this.drivePower = drive;
        this.sailPower = sail;
        this.rowPower = row;
        return this;
    }

    public ShipSpec motion(float accel, float decel, float turnAccel) {
        this.accel = accel;
        this.decel = decel;
        this.turnAccel = turnAccel;
        return this;
    }

    public ShipSpec ram(float damage, float reach) {
        this.ramDamage = damage;
        this.ramReach = reach;
        return this;
    }

    /** Lays out the cells; call once after the design is complete. */
    public ShipSpec build() {
        cells.clear();
        int length = rows.length;
        for (int r = 0; r < length; r++) {
            float f = length / 2.0f - r - 0.5f;
            int width = rows[r];
            for (int k = 0; k < width; k++) {
                float h = -(width - 1) / 2.0f + k;
                Kind kind = Kind.DECK;
                float top = deckTop();
                boolean edge = width >= 3 && (k == 0 || k == width - 1);
                if ((edge && !gangwayRows.contains(r)) || r == 0) {
                    kind = Kind.RAIL;
                    top += railHeight;
                }
                for (Mast mast : masts) {
                    if (Math.abs(mast.forward - f) <= 0.5f && Math.abs(mast.horizontal - h) <= 0.5f) {
                        kind = Kind.MAST;
                        top = deckTop() + mastBox;
                    }
                }
                cells.add(new Cell(f, h, kind, top));
            }
        }
        if (cells.size() > MAX_PARTS) {
            throw new IllegalStateException("ship " + name + " has " + cells.size() + " cells, at most " + MAX_PARTS);
        }
        return this;
    }

    public List<Cell> cells() {
        return cells;
    }

    /** The height of the deck over the keel. */
    public float deckTop() {
        return draft + deckAbove;
    }

    public int length() {
        return rows.length;
    }

    public int width() {
        int max = 0;
        for (int w : rows) {
            max = Math.max(max, w);
        }
        return max;
    }

    /** The row of a forward coordinate (rows are 1 block long, the bow row is 0), -1 if it is off the hull. */
    public int rowAt(float forward) {
        int row = (int) Math.floor(rows.length / 2.0f - forward);
        return row >= 0 && row < rows.length ? row : -1;
    }

    /** True if the point (in the ship's frame) is over the hull, with a margin. */
    public boolean covers(float forward, float horizontal, float margin) {
        int row = rowAt(forward);
        return row >= 0 && Math.abs(horizontal) <= rows[row] / 2.0f + margin;
    }

    /** The tip of the bow, in blocks forward of the middle. */
    public float bow() {
        return rows.length / 2.0f;
    }
}
