package com.bxwbb.constraint;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ConstraintSelectionStore {

    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    private ConstraintSelectionStore() {
    }

    public static void set(Player player, Anchor first, Anchor second) {
        SELECTIONS.put(player.getUniqueId(), new Selection(first, second));
    }

    public static Selection get(Player player) {
        return SELECTIONS.get(player.getUniqueId());
    }

    public static void clear(Player player) {
        SELECTIONS.remove(player.getUniqueId());
    }

    public static final class Selection {
        public final Anchor first;
        public final Anchor second;

        private Selection(Anchor first, Anchor second) {
            this.first = first;
            this.second = second;
        }

        public double distance() {
            return first.location.distance(second.location);
        }
    }

    public static final class Anchor {
        public final Display display;
        public final Entity entity;
        public final Location location;

        public Anchor(Display display, Entity entity, Location location) {
            this.display = display;
            this.entity = entity;
            this.location = location.clone();
        }

        public boolean hasPhysicalTarget() {
            return display != null || entity != null;
        }
    }
}
