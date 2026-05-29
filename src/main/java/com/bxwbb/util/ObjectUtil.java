package com.bxwbb.util;

import org.bukkit.entity.Display;

import java.util.List;
import java.util.Map;

public class ObjectUtil {

    public static final Map<String, List<Display>> displays = new java.util.HashMap<>();

    private ObjectUtil() {}

    public static void addDisplay(String key, Display... display) {
        if (!displays.containsKey(key)) {
            displays.put(key, new java.util.ArrayList<>());
        }
        for (Display display1 : display) {
            displays.get(key).add(display1);
        }
    }

    public static void removeDisplay(String key) {
        if (!displays.containsKey(key)) return;
        for (Display display : displays.get(key)) {
            SpawnUtil.removeDisplay(display);
        }
        displays.remove(key);
    }

    public static void removeAll() {
        for (String key : displays.keySet()) {
            for (Display display : displays.get(key)) {
                SpawnUtil.removeDisplay(display);
            }
        }
        displays.clear();
    }

}
