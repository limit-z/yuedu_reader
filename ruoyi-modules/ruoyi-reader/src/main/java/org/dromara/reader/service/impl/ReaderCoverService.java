package org.dromara.reader.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.reader.config.ReaderCoverProperties;
import org.dromara.reader.domain.ReaderWork;
import org.dromara.reader.domain.bo.ReaderCoverStyleBo;
import org.dromara.reader.domain.vo.admin.ReaderCoverPresetVo;
import org.dromara.reader.domain.vo.admin.ReaderCoverStyleVo;
import org.dromara.reader.mapper.ReaderWorkMapper;
import org.dromara.system.domain.bo.SysConfigBo;
import org.dromara.system.domain.vo.SysOssVo;
import org.dromara.system.service.ISysConfigService;
import org.dromara.system.service.ISysOssService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 为缺少来源图片的作品生成带书名的浅色横竖版 PNG 封面。 */
@Service
@RequiredArgsConstructor
public class ReaderCoverService {

    public static final int PORTRAIT_WIDTH = 600;
    public static final int PORTRAIT_HEIGHT = 800;
    public static final int LANDSCAPE_WIDTH = 1200;
    public static final int LANDSCAPE_HEIGHT = 675;
    private static final String PORTRAIT = "portrait";
    private static final String LANDSCAPE = "landscape";
    private static final String GLOBAL = "GLOBAL";
    private static final String COLOR = "COLOR";
    private static final String IMAGE = "IMAGE";
    private static final String GLOBAL_CONFIG_KEY = "reader.cover.defaultStyle";
    private static final String COVER_RENDER_VERSION = "clean-background-1";
    private static final String DEFAULT_COLOR = "#FFF4F2";
    private static final List<ReaderCoverPresetVo> PRESETS = List.of(
        new ReaderCoverPresetVo("blush", "樱粉", "#FFF4F2"),
        new ReaderCoverPresetVo("mint", "薄荷", "#EEF9F3"),
        new ReaderCoverPresetVo("sky", "晴空", "#EFF7FF"),
        new ReaderCoverPresetVo("apricot", "杏黄", "#FFF8E5"),
        new ReaderCoverPresetVo("lilac", "藕荷", "#F8F1FF"),
        new ReaderCoverPresetVo("pearl", "珍珠灰", "#F4F5F2")
    );
    private static final List<String> FONT_CANDIDATES = List.of(
        "PingFang SC", "Hiragino Sans GB", "Songti SC", "Noto Sans CJK SC", "Microsoft YaHei", "SansSerif"
    );

    private final ReaderCoverProperties properties;
    private final ReaderWorkMapper workMapper;
    private final ISysConfigService configService;
    private final ISysOssService ossService;

    /** 缺少某个方向时单独生成；外部来源图片始终保留。 */
    public synchronized boolean ensureGeneratedCover(ReaderWork work) {
        return generateCover(work, false, false);
    }

    private boolean generateCover(ReaderWork work, boolean forceGenerated, boolean replaceExternal) {
        if (work == null || work.getId() == null || StringUtils.isBlank(work.getTitle())) {
            throw new ServiceException("作品ID和标题不能为空，无法生成封面");
        }
        int currentRevision = work.getCoverRevision() == null ? 0 : work.getCoverRevision();
        String portraitUrl = publicUrl(work.getId(), PORTRAIT, currentRevision);
        String landscapeUrl = publicUrl(work.getId(), LANDSCAPE, currentRevision);
        Path portraitPath = coverPath(work.getId(), PORTRAIT);
        Path landscapePath = coverPath(work.getId(), LANDSCAPE);
        boolean renderVersionReady = hasCurrentRenderVersion(work.getId());
        boolean ownsPortrait = replaceExternal || StringUtils.isBlank(work.getCoverUrl()) || isGeneratedUrl(work.getCoverUrl());
        boolean ownsLandscape = replaceExternal || StringUtils.isBlank(work.getCoverLandscapeUrl()) || isGeneratedUrl(work.getCoverLandscapeUrl());
        boolean portraitReady = !forceGenerated && renderVersionReady
            && portraitUrl.equals(work.getCoverUrl()) && Files.isRegularFile(portraitPath);
        boolean landscapeReady = !forceGenerated && renderVersionReady
            && landscapeUrl.equals(work.getCoverLandscapeUrl()) && Files.isRegularFile(landscapePath);
        if ((!ownsPortrait || portraitReady) && (!ownsLandscape || landscapeReady)) {
            return false;
        }

        CoverStyle style = resolveStyle(work);
        int[] palette = palette(style.color());
        BufferedImage backgroundImage = style.backgroundOssId() == null ? null : loadBackgroundImage(style.backgroundOssId());
        int nextRevision = currentRevision + 1;
        if (ownsPortrait && !portraitReady) {
            writeCover(work, portraitPath, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, false, palette, backgroundImage);
            work.setCoverUrl(publicUrl(work.getId(), PORTRAIT, nextRevision));
        }
        if (ownsLandscape && !landscapeReady) {
            writeCover(work, landscapePath, LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, true, palette, backgroundImage);
            work.setCoverLandscapeUrl(publicUrl(work.getId(), LANDSCAPE, nextRevision));
        }
        if ((ownsPortrait && !portraitReady) || (ownsLandscape && !landscapeReady)) {
            writeRenderVersion(work.getId());
        }
        work.setCoverRevision(nextRevision);
        workMapper.updateById(work);
        return true;
    }

    /** 补齐缺失方向，并把旧版深色本地封面升级为浅色双封面。 */
    public int backfillMissingCovers() {
        int generated = 0;
        for (ReaderWork work : workMapper.selectList(Wrappers.<ReaderWork>lambdaQuery().orderByAsc(ReaderWork::getId))) {
            if (ensureGeneratedCover(work)) {
                generated++;
            }
        }
        return generated;
    }

    public ReaderCoverStyleVo queryGlobalStyle() {
        return toStyleVo(globalStyle());
    }

    public int updateGlobalStyle(ReaderCoverStyleBo bo) {
        CoverStyle style = validateStyle(bo, false);
        ReaderCoverStyleBo stored = new ReaderCoverStyleBo();
        stored.setMode(style.mode());
        stored.setColor(style.color());
        stored.setBackgroundOssId(style.backgroundOssId());
        SysConfigBo config = new SysConfigBo();
        config.setConfigName("阅读器-自动封面全局背景");
        config.setConfigKey(GLOBAL_CONFIG_KEY);
        config.setConfigValue(JSONUtil.toJsonStr(stored));
        config.setConfigType("Y");
        config.setRemark("采集建档和自动封面使用的全局背景配置");
        if (StringUtils.isBlank(configService.selectConfigByKey(GLOBAL_CONFIG_KEY))) {
            configService.insertConfig(config);
        } else {
            configService.updateConfig(config);
        }
        int regenerated = 0;
        for (ReaderWork work : workMapper.selectList(Wrappers.<ReaderWork>lambdaQuery().orderByAsc(ReaderWork::getId))) {
            if ((StringUtils.isBlank(work.getCoverBackgroundMode()) || GLOBAL.equals(work.getCoverBackgroundMode()))
                && generateCover(work, true, false)) {
                regenerated++;
            }
        }
        return regenerated;
    }

    public void updateWorkStyle(ReaderWork work, ReaderCoverStyleBo bo) {
        CoverStyle style = validateStyle(bo, true);
        work.setCoverBackgroundMode(style.mode());
        work.setCoverBackgroundColor(COLOR.equals(style.mode()) ? style.color() : null);
        work.setCoverBackgroundOssId(IMAGE.equals(style.mode()) ? style.backgroundOssId() : null);
        generateCover(work, true, true);
    }

    public String resolveBackgroundImageUrl(Long ossId) {
        if (ossId == null) {
            return null;
        }
        SysOssVo oss = ossService.getById(ossId);
        return oss == null ? null : oss.getUrl();
    }

    /** 读取指定方向的公开封面文件。 */
    public byte[] readCover(Long workId, String orientation) {
        if (workId == null || workId <= 0 || (!PORTRAIT.equals(orientation) && !LANDSCAPE.equals(orientation))) {
            throw new ServiceException("封面不存在");
        }
        return readPath(coverPath(workId, orientation));
    }

    /** 兼容旧版 /{workId}.png 地址，优先返回新版竖版。 */
    public byte[] readCover(Long workId) {
        if (workId == null || workId <= 0) {
            throw new ServiceException("封面不存在");
        }
        Path portrait = coverPath(workId, PORTRAIT);
        return readPath(Files.isRegularFile(portrait) ? portrait : legacyCoverPath(workId));
    }

    private byte[] readPath(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new ServiceException("封面不存在");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new ServiceException("封面读取失败");
        }
    }

    private void writeCover(ReaderWork work, Path target, int width, int height, boolean landscape,
                            int[] palette, BufferedImage backgroundImage) {
        Path directory = target.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, work.getId() + "-", ".png.tmp");
            BufferedImage image = render(work, width, height, landscape, palette, backgroundImage);
            if (!ImageIO.write(image, "png", temporary.toFile())) {
                throw new IOException("PNG writer unavailable");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new ServiceException("自动封面生成失败");
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 最终文件已写入，临时文件清理失败不影响作品入库。
                }
            }
        }
    }

    private BufferedImage render(ReaderWork work, int width, int height, boolean landscape,
                                 int[] palette, BufferedImage backgroundImage) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color background = color(palette[0]);
            Color secondary = color(palette[1]);
            Color accent = color(palette[2]);
            Color text = color(palette[3]);
            if (backgroundImage == null) {
                graphics.setPaint(new GradientPaint(0, 0, background, width, height, secondary));
                graphics.fillRect(0, 0, width, height);
            } else {
                drawBackgroundImage(graphics, backgroundImage, width, height);
                graphics.setColor(new Color(255, 255, 255, 118));
                graphics.fillRect(0, 0, width, height);
            }
            drawCornerArcs(graphics, width, height, accent);
            drawFrame(graphics, width, height, accent);
            drawTitle(graphics, work.getTitle().trim(), width, height, landscape, text, accent);
            drawAuthor(graphics, work.getAuthorName(), width, height, landscape, text);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void drawBackgroundImage(Graphics2D graphics, BufferedImage source, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int scaledWidth = (int) Math.ceil(source.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(source.getHeight() * scale);
        int x = (width - scaledWidth) / 2;
        int y = (height - scaledHeight) / 2;
        graphics.drawImage(source, x, y, scaledWidth, scaledHeight, null);
    }

    private void drawCornerArcs(Graphics2D graphics, int width, int height, Color accent) {
        // Keep the background color/image clean. The former diagonal stripe layer
        // made a configured solid background look like a textured image.
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.13f));
        graphics.setColor(accent);
        int unit = Math.max(48, Math.min(width, height) / 8);
        for (int i = 0; i < 5; i++) {
            int size = unit * (2 + i);
            graphics.setStroke(new BasicStroke(Math.max(2f, width / 400f)));
            graphics.draw(new Ellipse2D.Double(width - size * 0.62, -size * 0.38, size, size));
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private void drawFrame(Graphics2D graphics, int width, int height, Color accent) {
        int margin = Math.max(30, Math.min(width, height) / 17);
        graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 115));
        graphics.setStroke(new BasicStroke(Math.max(2f, width / 360f)));
        graphics.drawRoundRect(margin, margin, width - margin * 2, height - margin * 2, 18, 18);
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawRoundRect(margin + 10, margin + 10, width - (margin + 10) * 2, height - (margin + 10) * 2, 14, 14);
    }

    private void drawTitle(Graphics2D graphics, String title, int width, int height, boolean landscape,
                           Color text, Color accent) {
        int maxWidth = landscape ? 700 : 450;
        int maxHeight = landscape ? 260 : 330;
        int maxLines = landscape ? 4 : 6;
        int maxFont = landscape ? 92 : 72;
        TitleLayout layout = fitTitle(graphics, title, maxWidth, maxHeight, maxLines, maxFont);
        graphics.setFont(layout.font());
        graphics.setColor(text);
        FontMetrics metrics = graphics.getFontMetrics();
        int lineHeight = metrics.getHeight() + (landscape ? 12 : 10);
        int totalHeight = layout.lines().size() * lineHeight;
        int baseline = height / 2 - 20 - totalHeight / 2 + metrics.getAscent();
        for (String line : layout.lines()) {
            int x = landscape ? 100 : (width - metrics.stringWidth(line)) / 2;
            graphics.drawString(line, x, baseline);
            baseline += lineHeight;
        }
        graphics.setColor(accent);
        int ruleWidth = landscape ? 170 : 130;
        int ruleX = landscape ? 100 : (width - ruleWidth) / 2;
        int ruleY = height - (landscape ? 175 : 174);
        graphics.fillRoundRect(ruleX, ruleY, ruleWidth, landscape ? 7 : 5, 6, 6);
    }

    private void drawAuthor(Graphics2D graphics, String authorName, int width, int height,
                            boolean landscape, Color text) {
        String author = StringUtils.isBlank(authorName) ? "未知作者" : authorName.trim();
        graphics.setFont(new Font(resolveFontFamily(), Font.PLAIN, landscape ? 30 : 26));
        FontMetrics metrics = graphics.getFontMetrics();
        String authorText = "作者  " + author;
        graphics.setColor(new Color(text.getRed(), text.getGreen(), text.getBlue(), 205));
        int authorX = landscape ? 100 : (width - metrics.stringWidth(authorText)) / 2;
        int authorY = landscape ? height - 120 : height - 108;
        graphics.drawString(authorText, authorX, authorY);
        graphics.setFont(new Font("Serif", Font.PLAIN, landscape ? 20 : 18));
        String mark = "YUE READ";
        metrics = graphics.getFontMetrics();
        int markX = landscape ? width - metrics.stringWidth(mark) - 92 : (width - metrics.stringWidth(mark)) / 2;
        graphics.drawString(mark, markX, height - 62);
    }

    private TitleLayout fitTitle(Graphics2D graphics, String title, int maxWidth, int maxHeight,
                                 int maxLines, int maxFontSize) {
        String family = resolveFontFamily();
        for (int size = maxFontSize; size >= 30; size -= 2) {
            Font font = new Font(family, Font.BOLD, size);
            graphics.setFont(font);
            List<String> lines = wrapTitle(title, graphics.getFontMetrics(), maxWidth);
            if (lines.size() <= maxLines && lines.size() * (graphics.getFontMetrics().getHeight() + 12) <= maxHeight) {
                return new TitleLayout(font, lines);
            }
        }
        Font font = new Font(family, Font.BOLD, 28);
        graphics.setFont(font);
        return new TitleLayout(font, wrapTitle(title, graphics.getFontMetrics(), maxWidth));
    }

    private List<String> wrapTitle(String title, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        title.codePoints().forEach(codePoint -> {
            String character = new String(Character.toChars(codePoint));
            if (!line.isEmpty() && metrics.stringWidth(line + character) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            line.append(character);
        });
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String resolveFontFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        return FONT_CANDIDATES.stream().filter(available::contains).findFirst().orElse("SansSerif");
    }

    private Path coverPath(Long workId, String orientation) {
        return Path.of(properties.getStoragePath()).toAbsolutePath().normalize()
            .resolve(workId.toString()).resolve(orientation + ".png");
    }

    private Path legacyCoverPath(Long workId) {
        return Path.of(properties.getStoragePath()).toAbsolutePath().normalize().resolve(workId + ".png");
    }

    private Path renderVersionPath(Long workId) {
        return Path.of(properties.getStoragePath()).toAbsolutePath().normalize()
            .resolve(workId.toString()).resolve("render.version");
    }

    private boolean hasCurrentRenderVersion(Long workId) {
        Path versionPath = renderVersionPath(workId);
        try {
            return Files.isRegularFile(versionPath)
                && COVER_RENDER_VERSION.equals(Files.readString(versionPath, StandardCharsets.US_ASCII).trim());
        } catch (IOException ignored) {
            return false;
        }
    }

    private void writeRenderVersion(Long workId) {
        try {
            Path versionPath = renderVersionPath(workId);
            Files.createDirectories(versionPath.getParent());
            Files.writeString(versionPath, COVER_RENDER_VERSION, StandardCharsets.US_ASCII);
        } catch (IOException ex) {
            throw new ServiceException("自动封面版本标记写入失败");
        }
    }

    private String publicUrl(Long workId, String orientation, int revision) {
        return normalizedPublicBase() + "/" + workId + "/" + orientation + ".png?v=cover-" + Math.max(1, revision);
    }

    private boolean isGeneratedUrl(String url) {
        return StringUtils.isNotBlank(url) && url.startsWith(normalizedPublicBase() + "/");
    }

    private String normalizedPublicBase() {
        String base = properties.getPublicPath();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private Color color(int rgb) {
        return new Color(rgb);
    }

    private CoverStyle resolveStyle(ReaderWork work) {
        String mode = work.getCoverBackgroundMode();
        if (COLOR.equals(mode)) {
            return new CoverStyle(COLOR, normalizeColor(work.getCoverBackgroundColor()), null);
        }
        if (IMAGE.equals(mode) && work.getCoverBackgroundOssId() != null) {
            return new CoverStyle(IMAGE, DEFAULT_COLOR, work.getCoverBackgroundOssId());
        }
        return globalStyle();
    }

    private CoverStyle globalStyle() {
        String value = configService.selectConfigByKey(GLOBAL_CONFIG_KEY);
        if (StringUtils.isBlank(value)) {
            return new CoverStyle(COLOR, DEFAULT_COLOR, null);
        }
        try {
            return validateStyle(JSONUtil.toBean(value, ReaderCoverStyleBo.class), false);
        } catch (RuntimeException ex) {
            return new CoverStyle(COLOR, DEFAULT_COLOR, null);
        }
    }

    private CoverStyle validateStyle(ReaderCoverStyleBo bo, boolean allowGlobal) {
        String mode = bo == null || StringUtils.isBlank(bo.getMode()) ? (allowGlobal ? GLOBAL : COLOR)
            : bo.getMode().trim().toUpperCase();
        if (allowGlobal && GLOBAL.equals(mode)) {
            return new CoverStyle(GLOBAL, null, null);
        }
        if (COLOR.equals(mode)) {
            return new CoverStyle(COLOR, normalizeColor(bo == null ? null : bo.getColor()), null);
        }
        if (IMAGE.equals(mode) && bo != null && bo.getBackgroundOssId() != null) {
            loadBackgroundImage(bo.getBackgroundOssId());
            return new CoverStyle(IMAGE, DEFAULT_COLOR, bo.getBackgroundOssId());
        }
        throw new ServiceException("封面背景配置不正确");
    }

    private String normalizeColor(String value) {
        String color = StringUtils.isBlank(value) ? DEFAULT_COLOR : value.trim().toUpperCase();
        if (!color.matches("#[0-9A-F]{6}")) {
            throw new ServiceException("封面背景色必须是 #RRGGBB 格式");
        }
        return color;
    }

    private int[] palette(String colorValue) {
        Color base = Color.decode(colorValue);
        Color secondary = blend(base, Color.WHITE, 0.38);
        Color accent = blend(base, new Color(70, 77, 72), 0.58);
        Color text = blend(base, new Color(30, 38, 32), 0.78);
        return new int[]{base.getRGB() & 0xffffff, secondary.getRGB() & 0xffffff,
            accent.getRGB() & 0xffffff, text.getRGB() & 0xffffff};
    }

    private Color blend(Color first, Color second, double secondWeight) {
        double firstWeight = 1 - secondWeight;
        return new Color(
            (int) Math.round(first.getRed() * firstWeight + second.getRed() * secondWeight),
            (int) Math.round(first.getGreen() * firstWeight + second.getGreen() * secondWeight),
            (int) Math.round(first.getBlue() * firstWeight + second.getBlue() * secondWeight)
        );
    }

    private BufferedImage loadBackgroundImage(Long ossId) {
        try {
            byte[] bytes = ossService.download(ossId).getBody();
            BufferedImage image = bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ServiceException("封面背景图片无法读取");
            }
            return image;
        } catch (ServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("封面背景图片无法读取");
        }
    }

    private ReaderCoverStyleVo toStyleVo(CoverStyle style) {
        ReaderCoverStyleVo vo = new ReaderCoverStyleVo();
        vo.setMode(style.mode());
        vo.setColor(style.color());
        vo.setBackgroundOssId(style.backgroundOssId());
        vo.setBackgroundImageUrl(resolveBackgroundImageUrl(style.backgroundOssId()));
        vo.setPresets(PRESETS);
        return vo;
    }

    private record CoverStyle(String mode, String color, Long backgroundOssId) {
    }

    private record TitleLayout(Font font, List<String> lines) {
    }
}
