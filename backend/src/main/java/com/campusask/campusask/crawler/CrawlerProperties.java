package com.campusask.campusask.crawler;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import java.util.*;

@Data
@Component
@Validated
@ConfigurationProperties(prefix="crawler")
public class CrawlerProperties {
    private boolean enabled=false;
    private List<String> allowedHosts=new ArrayList<>();
    private List<String> allowedPathPrefixes=new ArrayList<>();
    private List<String> seeds=new ArrayList<>();
    @Min(1) @Max(20) private int maxPages=10;
    @Min(1000) @Max(5000) private long delayMillis=1500;
    @Min(100) @Max(10000) private int minContentLength=100;
    private String contentSelector=".wp_articlecontent, .article-content, article, main";
}
