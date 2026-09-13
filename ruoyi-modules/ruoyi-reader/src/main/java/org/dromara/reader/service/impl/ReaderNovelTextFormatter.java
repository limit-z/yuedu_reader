package org.dromara.reader.service.impl;

import java.util.Arrays;
import java.util.stream.Collectors;

/** 将不同书源的空格和换行统一为小说段落格式。 */
public final class ReaderNovelTextFormatter {

    private ReaderNovelTextFormatter() {
    }

    public static String format(String source) {
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
