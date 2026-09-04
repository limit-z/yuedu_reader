package org.dromara.reader.service;

import org.dromara.reader.domain.vo.app.AppBookshelfItemVo;
import org.dromara.reader.domain.vo.app.AppHistoryItemVo;

import java.util.List;

/**
 * 阅读器模块代码，承载 IReaderBookshelfService 相关业务能力。
 */
public interface IReaderBookshelfService {

    /**
     * 将作品加入当前用户书架。
     */
    void add(Long workId);

    /**
     * 将作品移出当前用户书架。
     */
    void remove(Long workId);

    /**
     * 查询当前用户书架列表。
     */
    default List<AppBookshelfItemVo> listBookshelf() {
        return listBookshelf(null);
    }

    /**
     * 查询当前用户书架列表。
     */
    List<AppBookshelfItemVo> listBookshelf(String sortBy);

    /**
     * 批量移出书架。
     */
    void batchRemove(List<Long> workIds);

    /**
     * 设置作品在书架中的置顶状态。
     */
    void setTopPin(Long workId, boolean pinned);

    /**
     * 按前端传入顺序重排书架。
     */
    void reorder(List<Long> workIds);

    /**
     * 查询当前用户阅读历史。
     */
    List<AppHistoryItemVo> listHistory();

    /**
     * 删除单条阅读历史。
     */
    void removeHistory(Long historyId);

    /**
     * 清空当前用户阅读历史。
     */
    void clearHistory();
}
