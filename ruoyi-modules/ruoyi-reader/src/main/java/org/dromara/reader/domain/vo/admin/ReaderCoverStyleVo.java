package org.dromara.reader.domain.vo.admin;

import lombok.Data;

import java.util.List;

/** 管理端封面背景配置与内置预设。 */
@Data
public class ReaderCoverStyleVo {
    private String mode;
    private String color;
    private Long backgroundOssId;
    private String backgroundImageUrl;
    private List<ReaderCoverPresetVo> presets;
}
