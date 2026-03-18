package com.linkwork.agent.skill.core;

import com.linkwork.agent.skill.core.model.CommitInfo;
import com.linkwork.agent.skill.core.model.FileNode;
import com.linkwork.agent.skill.core.model.SkillInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SkillClient {
    private final SkillProvider provider;
    private final int maxRetries;
    private final Duration retryBackoff;
    private final Duration cacheTtl;
    private final Map<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    public SkillClient(SkillProvider provider,
                       int maxRetries,
                       Duration retryBackoff,
                       Duration cacheTtl) {
        this.provider = provider;
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoff = retryBackoff == null ? Duration.ofMillis(200) : retryBackoff;
        this.cacheTtl = cacheTtl == null ? Duration.ofSeconds(10) : cacheTtl;
    }

    public List<SkillInfo> listSkills() {
        return executeWithRetry(provider::listSkills);
    }

    public List<FileNode> getTree(String skillName) {
        String key = "tree:" + skillName;
        @SuppressWarnings("unchecked")
        List<FileNode> cached = (List<FileNode>) getCached(key);
        if (cached != null) {
            return cached;
        }
        List<FileNode> data = executeWithRetry(() -> provider.getTree(skillName));
        cache(key, data);
        return data;
    }

    public String getFile(String skillName, String filePath) {
        String key = "file:" + skillName + ":" + filePath;
        String cached = (String) getCached(key);
        if (cached != null) {
            return cached;
        }
        String data = executeWithRetry(() -> provider.getFile(skillName, filePath));
        cache(key, data);
        return data;
    }

    public CommitInfo upsertFile(String skillName, String filePath, String content, String commitMessage) {
        CommitInfo info = executeWithRetry(() -> provider.upsertFile(skillName, filePath, content, commitMessage));
        clearCache(skillName);
        return info;
    }

    public CommitInfo deleteFile(String skillName, String filePath, String commitMessage) {
        CommitInfo info = executeWithRetry(() -> provider.deleteFile(skillName, filePath, commitMessage));
        clearCache(skillName);
        return info;
    }

    public List<CommitInfo> listCommits(String skillName, int page, int pageSize) {
        return executeWithRetry(() -> provider.listCommits(skillName, page, pageSize));
    }

    public void clearCache(String skillName) {
        String prefix1 = "tree:" + skillName;
        String prefix2 = "file:" + skillName + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix1) || key.startsWith(prefix2));
    }

    private Object getCached(String key) {
        CacheEntry<?> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAt().isBefore(Instant.now())) {
            cache.remove(key);
            return null;
        }
        return entry.value();
    }

    private void cache(String key, Object value) {
        cache.put(key, new CacheEntry<>(value, Instant.now().plus(cacheTtl)));
    }

    private <T> T executeWithRetry(Supplier<T> supplier) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return supplier.get();
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt == maxRetries) {
                    break;
                }
                sleep(retryBackoff.toMillis());
            }
        }
        String reason = last == null ? "" : ": " + last.getMessage();
        throw new SkillException("Skill operation failed after retries" + reason, last);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SkillException("Interrupted while retrying skill operation", ex);
        }
    }

    private record CacheEntry<T>(T value, Instant expireAt) {
    }
}
