package com.localserver.logging;

import java.util.List;
import java.util.Map;

public final class LoggingDebuger {

    public static void printPretty(Object value) {
        StringBuilder str = new StringBuilder();
        str.append("\n");
        printPretty(value, 0, str);
        String result = str.toString();
        LoggingManger.info(result);
    }

    private static void printPretty(Object value, int depth, StringBuilder str) {
        String indent = "    ".repeat(depth);

        if (value instanceof Map<?, ?> map) {
            str.append("{\n");

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                str.append(indent + "    " + entry.getKey() + ": ");
                printPretty(entry.getValue(), depth + 1, str);
            }

            str.append(indent + "}\n");

        } else if (value instanceof List<?> list) {
            str.append("[\n");

            for (Object item : list) {
                str.append(indent + "    ");
                printPretty(item, depth + 1, str);
            }

            str.append(indent + "]\n");

        } else {
            str.append(value.toString() + "\n");
        }

    }
}