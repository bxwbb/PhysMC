package com.bxwbb.util;

import org.bukkit.entity.Display;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ObjectUtil {

    public static final Map<String, List<Display>> displays = new java.util.HashMap<>();
    public static final Map<String, Map<String, List<Display>>> groups = new java.util.HashMap<>();
    private static final List<List<Display>> grabbableGroups = new ArrayList<>();

    private ObjectUtil() {}

    public static void addDisplay(String key, Display... display) {
        addDisplay(key, key, display);
    }

    public static void addDisplay(String group, String name, Display... display) {
        groups.computeIfAbsent(group, ignored -> new LinkedHashMap<>());
        groups.get(group).computeIfAbsent(name, ignored -> new ArrayList<>());
        if (!displays.containsKey(group)) {
            displays.put(group, new java.util.ArrayList<>());
        }
        for (Display display1 : display) {
            displays.get(group).add(display1);
            groups.get(group).get(name).add(display1);
        }
    }

    public static void removeDisplay(String key) {
        removeDisplay(key, null);
    }

    public static void removeDisplay(String group, String name) {
        List<Display> targets = name == null ? displays.get(group) : namedDisplays(group, name);
        if (targets == null) return;
        for (Display display : new ArrayList<>(targets)) {
            SpawnUtil.removeDisplay(display);
        }
        if (name == null) {
            displays.remove(group);
            groups.remove(group);
        } else {
            Map<String, List<Display>> names = groups.get(group);
            if (names != null) {
                names.remove(name);
                if (names.isEmpty()) groups.remove(group);
            }
            List<Display> groupDisplays = displays.get(group);
            if (groupDisplays != null) {
                groupDisplays.removeAll(targets);
                if (groupDisplays.isEmpty()) displays.remove(group);
            }
        }
        pruneGrabbableGroups();
    }

    public static List<Display> namedDisplays(String group, String name) {
        Map<String, List<Display>> names = groups.get(group);
        if (names == null) return null;
        return names.get(name);
    }

    public static void removeAll() {
        for (String key : displays.keySet()) {
            for (Display display : displays.get(key)) {
                SpawnUtil.removeDisplay(display);
            }
        }
        displays.clear();
        groups.clear();
        grabbableGroups.clear();
    }

    public static void addGrabbableGroup(List<? extends Display> group) {
        List<Display> displays = new ArrayList<>();
        for (Display display : group) {
            if (display != null && display.isValid()) {
                displays.add(display);
            }
        }
        if (displays.size() > 1) {
            grabbableGroups.add(displays);
        }
    }

    public static List<Display> groupFor(Display target) {
        pruneGrabbableGroups();
        for (List<Display> group : grabbableGroups) {
            if (group.contains(target)) {
                return new ArrayList<>(group);
            }
        }
        return List.of(target);
    }

    private static void pruneGrabbableGroups() {
        grabbableGroups.removeIf(group -> {
            group.removeIf(display -> display == null || !display.isValid() || display.isDead());
            return group.size() < 2;
        });
    }

}
