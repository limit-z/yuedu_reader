package org.dromara.reader.service;

import org.dromara.reader.config.ReaderCoverProperties;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.reader.service.impl.ReaderCoverService;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.dromara.system.service.ISysConfigService;
import org.dromara.system.service.ISysOssService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ReaderAllEnvTest
class ReaderCoverServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void missingCoverShouldGenerateLightPortraitAndLandscapePngsAndPersistUrls() throws Exception {
        ReaderWorkMapper mapper = mock(ReaderWorkMapper.class);
        ReaderCoverService service = service(mapper);
        ReaderWork work = work(11L, "诡秘之主", "爱潜水的乌贼");

        assertTrue(service.ensureGeneratedCover(work));

        Path portraitPath = tempDir.resolve("11").resolve("portrait.png");
        Path landscapePath = tempDir.resolve("11").resolve("landscape.png");
        assertTrue(Files.isRegularFile(portraitPath));
        assertTrue(Files.isRegularFile(landscapePath));
        BufferedImage portrait = ImageIO.read(portraitPath.toFile());
        BufferedImage landscape = ImageIO.read(landscapePath.toFile());
        assertEquals(600, portrait.getWidth());
        assertEquals(800, portrait.getHeight());
        assertEquals(1200, landscape.getWidth());
        assertEquals(675, landscape.getHeight());
        assertTrue(countSampledColors(portrait) > 8);
        assertTrue(countSampledColors(landscape) > 8);
        assertTrue(averageSampledLuminance(portrait) > 0.68);
        assertTrue(averageSampledLuminance(landscape) > 0.68);
        assertEquals("/reader/app/covers/11/portrait.png?v=cover-1", work.getCoverUrl());
        assertEquals("/reader/app/covers/11/landscape.png?v=cover-1", work.getCoverLandscapeUrl());
        ArgumentCaptor<ReaderWork> captor = ArgumentCaptor.forClass(ReaderWork.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("/reader/app/covers/11/portrait.png?v=cover-1", captor.getValue().getCoverUrl());
        assertEquals("/reader/app/covers/11/landscape.png?v=cover-1", captor.getValue().getCoverLandscapeUrl());
    }

    @Test
    void existingSourceCoverShouldBePreserved() {
        ReaderWorkMapper mapper = mock(ReaderWorkMapper.class);
        ReaderCoverService service = service(mapper);
        ReaderWork work = work(12L, "三体", "刘慈欣");
        work.setCoverUrl("https://cdn.example.com/three-body.jpg");
        work.setCoverLandscapeUrl("https://cdn.example.com/three-body-wide.jpg");

        assertFalse(service.ensureGeneratedCover(work));

        assertEquals("https://cdn.example.com/three-body.jpg", work.getCoverUrl());
        assertEquals("https://cdn.example.com/three-body-wide.jpg", work.getCoverLandscapeUrl());
        verify(mapper, never()).updateById(any(ReaderWork.class));
    }

    @Test
    void backfillShouldGenerateOnlyOnceForMissingWorks() {
        ReaderWorkMapper mapper = mock(ReaderWorkMapper.class);
        ReaderCoverService service = service(mapper);
        ReaderWork first = work(21L, "第一本书", "作者甲");
        ReaderWork second = work(22L, "第二本书", "作者乙");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));

        assertEquals(2, service.backfillMissingCovers());
        assertEquals(0, service.backfillMissingCovers());

        assertTrue(Files.isRegularFile(tempDir.resolve("21/portrait.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("21/landscape.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("22/portrait.png")));
        assertTrue(Files.isRegularFile(tempDir.resolve("22/landscape.png")));
    }

    @Test
    void generatedCoverShouldUseGlobalConfiguredColor() throws Exception {
        ReaderWorkMapper mapper = mock(ReaderWorkMapper.class);
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("reader.cover.defaultStyle"))
            .thenReturn("{\"mode\":\"COLOR\",\"color\":\"#EEF9F3\"}");
        ReaderCoverService service = service(mapper, configService, mock(ISysOssService.class));

        assertTrue(service.ensureGeneratedCover(work(31L, "浅色封面", "作者")));

        BufferedImage portrait = ImageIO.read(tempDir.resolve("31/portrait.png").toFile());
        int rgb = portrait.getRGB(5, 5) & 0xffffff;
        assertTrue(colorDistance(rgb, 0xEEF9F3) < 45);
    }

    private ReaderCoverService service(ReaderWorkMapper mapper) {
        ISysConfigService configService = mock(ISysConfigService.class);
        when(configService.selectConfigByKey("reader.cover.defaultStyle")).thenReturn("");
        return service(mapper, configService, mock(ISysOssService.class));
    }

    private ReaderCoverService service(ReaderWorkMapper mapper, ISysConfigService configService, ISysOssService ossService) {
        ReaderCoverProperties properties = new ReaderCoverProperties();
        properties.setStoragePath(tempDir.toString());
        properties.setPublicPath("/reader/app/covers");
        return new ReaderCoverService(properties, mapper, configService, ossService);
    }

    private ReaderWork work(Long id, String title, String author) {
        ReaderWork work = new ReaderWork();
        work.setId(id);
        work.setTitle(title);
        work.setAuthorName(author);
        return work;
    }

    private int countSampledColors(BufferedImage image) {
        return (int) java.util.stream.IntStream.range(0, 60)
            .boxed()
            .flatMap(x -> java.util.stream.IntStream.range(0, 80)
                .mapToObj(y -> image.getRGB(
                    x * (image.getWidth() - 1) / 59,
                    y * (image.getHeight() - 1) / 79)))
            .distinct()
            .count();
    }

    private double averageSampledLuminance(BufferedImage image) {
        return java.util.stream.IntStream.range(0, 12)
            .boxed()
            .flatMapToDouble(x -> java.util.stream.IntStream.range(0, 12).mapToDouble(y -> {
                int rgb = image.getRGB(x * (image.getWidth() - 1) / 11, y * (image.getHeight() - 1) / 11);
                double red = (rgb >> 16 & 0xff) / 255.0;
                double green = (rgb >> 8 & 0xff) / 255.0;
                double blue = (rgb & 0xff) / 255.0;
                return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
            }))
            .average()
            .orElse(0);
    }

    private int colorDistance(int first, int second) {
        int red = Math.abs((first >> 16 & 0xff) - (second >> 16 & 0xff));
        int green = Math.abs((first >> 8 & 0xff) - (second >> 8 & 0xff));
        int blue = Math.abs((first & 0xff) - (second & 0xff));
        return Math.max(red, Math.max(green, blue));
    }
}
