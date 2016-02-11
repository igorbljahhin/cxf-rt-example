package com.example.client;

import java.util.Map;

public class ContextMediator {

    private static final ThreadLocal<Map<String, Object>> LOCAL = new ThreadLocal<>();

    private ContextMediator() {
    }

    public static Map<String, Object> get() {
        return LOCAL.get();
    }

    public static void set(Map<String, Object> context) {
        LOCAL.set(context);
    }
}
