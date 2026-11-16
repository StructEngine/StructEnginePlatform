package com.structengine.platform.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * 强类型配置绑定：{@code structengine.*} 前缀，环境变量 {@code STRUCTENGINE_*} 通过 relaxed binding 注入。
 */
@ConfigurationProperties(prefix = "structengine")
@Validated
public class AppProperties {

    @NotBlank
    private String aiApiUrl;

    @NotBlank
    private String aiApiKey;

    @NotBlank
    private String aiModel;

    /** 项目根目录；为空时回退到 JVM 的 user.dir。 */
    private String projectRoot = "";

    private String dataDir = "./data";

    private int maxImageMb = 10;

    private int serverPort = 8080;

    /** 游戏服 WebSocket 鉴权令牌，空字符串表示不校验（仅限本地开发）。 */
    private String gameToken = "";

    /** 等待插件 RESPONSE_Y 的超时秒数。 */
    @Min(1)
    @Max(60)
    private int gameRpcTimeoutSeconds = 5;

    /** 对外基础地址（如 https://build.example.com）；为空则按请求 Host 推导。 */
    private String publicBaseUrl = "";

    /** 单管理员登录密码；生产环境必须覆盖。 */
    private String adminPassword = "change-me";

    /** 一次生成允许的最大方块数。 */
    private int maxModelBlocks = 20000;

    public String getAiApiUrl() {
        return aiApiUrl;
    }

    public void setAiApiUrl(String aiApiUrl) {
        this.aiApiUrl = aiApiUrl;
    }

    public String getAiApiKey() {
        return aiApiKey;
    }

    public void setAiApiKey(String aiApiKey) {
        this.aiApiKey = aiApiKey;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public String getProjectRoot() {
        return projectRoot;
    }

    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getMaxImageMb() {
        return maxImageMb;
    }

    public void setMaxImageMb(int maxImageMb) {
        this.maxImageMb = maxImageMb;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public String getGameToken() {
        return gameToken;
    }

    public void setGameToken(String gameToken) {
        this.gameToken = gameToken;
    }

    public int getGameRpcTimeoutSeconds() {
        return gameRpcTimeoutSeconds;
    }

    public void setGameRpcTimeoutSeconds(int gameRpcTimeoutSeconds) {
        this.gameRpcTimeoutSeconds = gameRpcTimeoutSeconds;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public int getMaxModelBlocks() {
        return maxModelBlocks;
    }

    public void setMaxModelBlocks(int maxModelBlocks) {
        this.maxModelBlocks = maxModelBlocks;
    }

    /**
     * 项目根目录的绝对路径；未显式配置时使用 JVM 工作目录。
     */
    public Path projectPath() {
        String root = (projectRoot == null || projectRoot.isBlank())
                ? System.getProperty("user.dir")
                : projectRoot;
        return Path.of(root).toAbsolutePath().normalize();
    }

    /**
     * 数据根目录的绝对路径：相对路径一律基于项目根目录解析，避免启动目录漂移。
     */
    public Path dataPath() {
        Path path = Path.of(dataDir);
        return path.isAbsolute() ? path.normalize() : projectPath().resolve(path).normalize();
    }

    /**
     * 单图片大小上限（字节）：maxImageMb * 1024 * 1024。
     */
    public long maxImageBytes() {
        return (long) maxImageMb * 1024 * 1024;
    }
}