package org.dromara.reader.worker;

import java.util.Arrays;
import java.util.stream.Collectors;

final class ChapterTextFormatter {

    private ChapterTextFormatter() {
    }

    static String format(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String normalized = source.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00a0', ' ')
            .replaceAll("(?:[ \\t\\f]{2,}|\\u3000{2,})", "\n\n");
        return Arrays.stream(normalized.split("\\n+"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .collect(Collectors.joining("\n\n"));
    }
}
