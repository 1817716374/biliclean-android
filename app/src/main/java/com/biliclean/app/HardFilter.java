package com.biliclean.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class HardFilter {
    private HardFilter() {
    }

    static List<String> reasons(JSONObject item) {
        List<String> reasons = new ArrayList<>();
        if ("banner".equals(item.optString("card_goto"))) reasons.add("card_goto=banner");
        if ("banner_v8".equals(item.optString("card_type"))) reasons.add("card_type=banner_v8");
        if ("live".equals(item.optString("goto"))) reasons.add("goto=live");
        if (truthy(item.opt("room_info"))) reasons.add("room_info");
        if (truthy(item.opt("business_info"))) reasons.add("business_info");
        scan(item, reasons, "");
        return reasons;
    }

    private static void scan(Object node, List<String> reasons, String path) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONArray names = object.names();
            if (names == null) return;
            for (int i = 0; i < names.length(); i++) {
                String key = names.optString(i);
                Object child = object.opt(key);
                String lower = key.toLowerCase();
                String childPath = path.isEmpty() ? key : path + "." + key;
                if ("ad_banner".equals(lower) && truthy(child)) add(reasons, "ad_banner");
                if (("is_ad".equals(lower) || "is_ad_loc".equals(lower)) && truthy(child)) add(reasons, childPath);
                if (("creative_id".equals(lower) || "creativeid".equals(lower)) && truthy(child)) add(reasons, "creative_id");
                if ("type".equals(lower) && "ad".equals(String.valueOf(child))) add(reasons, "type=ad");
                if ((lower.contains("goods") || lower.contains("shopping")) && truthy(child)) add(reasons, "goods_panel");
                scan(child, reasons, childPath);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                scan(array.opt(i), reasons, path + "[" + i + "]");
            }
        }
    }

    private static void add(List<String> reasons, String reason) {
        if (!reasons.contains(reason)) reasons.add(reason);
    }

    private static boolean truthy(Object value) {
        if (value == null || value == JSONObject.NULL) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof String) {
            String s = ((String) value).trim();
            return !s.isEmpty() && !"0".equals(s) && !"false".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s);
        }
        if (value instanceof JSONArray) return ((JSONArray) value).length() > 0;
        if (value instanceof JSONObject) return ((JSONObject) value).length() > 0;
        return true;
    }
}

