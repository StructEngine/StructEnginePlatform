package com.structengine.platform.captcha;

import com.structengine.platform.id.SnowflakeIdGenerator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码：内存存储、5 分钟 TTL、一次性。
 *
 * <p>单实例部署足够；未来多实例时替换为 Redis/DB。</p>
 */
@Service
public class CaptchaService {

    public static final int TTL_SECONDS = 300;
    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final SnowflakeIdGenerator idGenerator;

    public CaptchaService(SnowflakeIdGenerator idGenerator) {
        this.idGenerator = idGenerator;
    }

    public CaptchaChallenge generate() {
        cleanup();
        String id = idGenerator.nextIdString();
        String code = randomCode();
        entries.put(id, new Entry(code, System.currentTimeMillis() + TTL_SECONDS * 1000L));
        return new CaptchaChallenge(id, "data:image/png;base64," + render(code), TTL_SECONDS);
    }

    /**
     * 一次性校验；无论成功失败都移除，避免重放。
     */
    public boolean verify(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        Entry entry = entries.remove(captchaId);
        if (entry == null || entry.expiresAt() < System.currentTimeMillis()) {
            return false;
        }
        return entry.code().equalsIgnoreCase(code.trim());
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    private String render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(14, 20, 15));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            for (int i = 0; i < 6; i++) {
                g.setColor(randomColor(80, 150));
                g.setStroke(new BasicStroke(1.2f));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }

            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(160, 245));
                int x = 16 + i * 24;
                int y = 28 + random.nextInt(5);
                g.drawString(String.valueOf(code.charAt(i)), x, y);
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码失败", e);
        }
    }

    private Color randomColor(int min, int max) {
        int range = max - min;
        return new Color(min + random.nextInt(range), min + random.nextInt(range),
                min + random.nextInt(range));
    }

    private record Entry(String code, long expiresAt) {
    }
}
