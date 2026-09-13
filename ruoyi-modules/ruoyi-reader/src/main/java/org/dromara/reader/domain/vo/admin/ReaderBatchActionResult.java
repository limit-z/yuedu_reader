package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** 管理端批量操作结果，保留逐条失败原因。 */
@Data
public class ReaderBatchActionResult {

    private int requestedCount;
    private int successCount;
    private int failureCount;
    private List<ReaderBatchActionFailure> failures = new ArrayList<>();

    public static ReaderBatchActionResult execute(Collection<Long> ids, Function<Long, String> action) {
        ReaderBatchActionResult result = new ReaderBatchActionResult();
        if (ids == null) {
            return result;
        }
        ids.stream().filter(id -> id != null).distinct().forEach(id -> {
            result.requestedCount++;
            try {
                String failure = action.apply(id);
                if (failure == null || failure.isBlank()) {
                    result.successCount++;
                } else {
                    result.failureCount++;
                    result.failures.add(new ReaderBatchActionFailure(id, failure));
                }
            } catch (Exception ex) {
                result.failureCount++;
                result.failures.add(new ReaderBatchActionFailure(id, messageOf(ex)));
            }
        });
        return result;
    }

    private static String messageOf(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    @Data
    public static class ReaderBatchActionFailure {
        private final Long id;
        private final String reason;

        public ReaderBatchActionFailure(Long id, String reason) {
            this.id = id;
            this.reason = reason;
        }
    }
}
