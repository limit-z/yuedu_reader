package org.dromara.reader.controller.app;

import lombok.RequiredArgsConstructor;
import org.dromara.reader.service.impl.ReaderCoverService;
import org.dromara.reader.service.impl.ReaderCoverCrawlerService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/** 提供可由浏览器图片标签直接读取的作品封面。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/reader/app/covers")
public class ReaderCoverController {

    private final ReaderCoverService readerCoverService;
    private final ReaderCoverCrawlerService readerCoverCrawlerService;

    @GetMapping(value = "/{workId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> cover(@PathVariable Long workId) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .contentType(MediaType.IMAGE_PNG)
            .body(readerCoverService.readCover(workId));
    }

    /** 按页面场景读取 portrait 竖版或 landscape 横版封面。 */
    @GetMapping(value = "/{workId}/{orientation}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> cover(@PathVariable Long workId, @PathVariable String orientation) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
            .contentType(MediaType.IMAGE_PNG)
            .body(readerCoverService.readCover(workId, orientation));
    }

    /** 代理读取封面采集候选，避免将包含其他业务文件的 OSS 桶整体设为公开。 */
    @GetMapping(value = "/candidates/{candidateId}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> candidate(@PathVariable Long candidateId) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic().immutable())
            .contentType(MediaType.IMAGE_PNG)
            .body(readerCoverCrawlerService.readCandidate(candidateId));
    }
}
