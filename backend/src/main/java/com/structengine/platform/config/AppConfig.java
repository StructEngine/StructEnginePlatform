package com.structengine.platform.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bean 装配：注册 AppProperties 与 SQLite DataSource。
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    public DataSource dataSource(AppProperties props) throws IOException {
        Path dir = props.dataPath();
        Files.createDirectories(dir);
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + dir.resolve("structengine.db"));
        return dataSource;
    }
}