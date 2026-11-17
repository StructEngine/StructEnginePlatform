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

    /** 项目根目录；为空时回退到 JVM 的 user.dir。 */
    private String projectRoot = "";

    private String dataDir = "./data";

    private int maxImageMb = 10;

    private int serverPort = 8080;

    /** 等待插件 RESPONSE_Y 的超时秒数。 */
    @Min(1)
    @Max(60)
    private int gameRpcTimeoutSeconds = 5;

    /** 对外基础地址（如 https://build.example.com）；为空则按请求 Host 推导。 */
    private String publicBaseUrl = "";

    /** JWT HS256 密钥；至少 32 字节，生产必须通过环境变量覆盖。 */
    @NotBlank
    private String jwtSecret;

    /** JWT 有效期（小时）。 */
    @Min(1)
    @Max(720)
    private int jwtTtlHours = 24;

    /** 首次安装创建 admin 的初始密码；admin 已存在时忽略。 */
    private String adminInitialPassword = "";

    /** Snowflake workerId，单实例默认 0。 */
    @Min(0)
    @Max(1023)
    private int snowflakeWorkerId = 0;

    /** 一次生成允许的最大方块数。 */
    private int maxModelBlocks = 20000;

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

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getJwtTtlHours() {
        return jwtTtlHours;
    }

    public void setJwtTtlHours(int jwtTtlHours) {
        this.jwtTtlHours = jwtTtlHours;
    }

    public String getAdminInitialPassword() {
        return adminInitialPassword;
    }

    public void setAdminInitialPassword(String adminInitialPassword) {
        this.adminInitialPassword = adminInitialPassword;
    }

    public int getSnowflakeWorkerId() {
        return snowflakeWorkerId;
    }

    public void setSnowflakeWorkerId(int snowflakeWorkerId) {
        this.snowflakeWorkerId = snowflakeWorkerId;
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