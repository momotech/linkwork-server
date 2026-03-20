package com.linkwork.agent.skill.provider.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkwork.agent.skill.core.SkillException;
import com.linkwork.agent.skill.core.SkillProvider;
import com.linkwork.agent.skill.core.SkillProviderExtendedOps;
import com.linkwork.agent.skill.core.model.CommitInfo;
import com.linkwork.agent.skill.core.model.FileNode;
import com.linkwork.agent.skill.core.model.SkillInfo;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GitLabProviderImpl implements SkillProvider, SkillProviderExtendedOps {
    private static final String ROOT_SKILL = "root";
    private final RestClient restClient;
    private final GitLabProperties properties;
    private volatile String cachedDefaultBranch;
    private final Map<String, Boolean> branchSkillCache = new ConcurrentHashMap<>();

    public GitLabProviderImpl(RestClient restClient, GitLabProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        validate();
    }

    @Override
    public List<SkillInfo> listSkills() {
        // 优先与 general_agent 对齐：分支即 Skill（排除 main/master）。
        List<SkillInfo> byBranches = listSkillsFromBranches();
        if (!byBranches.isEmpty()) {
            return byBranches;
        }
        // 兼容回退：目录即 Skill（旧 starter 目录模式）。
        return listSkillsFromDirectories();
    }

    @Override
    public List<FileNode> getTree(String skillName) {
        String ref = resolveSkillRef(skillName);
        String path = isBranchSkill(skillName) ? "" : skillPath(skillName);
        JsonNode tree = listTree(ref, path);
        List<FileNode> nodes = new ArrayList<>();
        for (JsonNode node : tree) {
            FileNode.NodeType type = "tree".equalsIgnoreCase(node.path("type").asText())
                    ? FileNode.NodeType.DIRECTORY
                    : FileNode.NodeType.FILE;
            Long size = node.path("size").isMissingNode() ? null : node.path("size").asLong();
            nodes.add(new FileNode(
                    node.path("name").asText(),
                    node.path("path").asText(),
                    type,
                    node.path("id").asText(),
                    size
            ));
        }
        return nodes;
    }

    @Override
    public String getFile(String skillName, String filePath) {
        String fullPath = resolveSkillFilePath(skillName, filePath);
        String ref = resolveSkillRef(skillName);
        JsonNode node = getFileMeta(fullPath, ref);
        String content = node.path("content").asText();
        String encoding = node.path("encoding").asText();
        if ("base64".equalsIgnoreCase(encoding)) {
            byte[] decoded = Base64.getDecoder().decode(content.replace("\n", ""));
            return new String(decoded, StandardCharsets.UTF_8);
        }
        return content;
    }

    @Override
    public CommitInfo upsertFile(String skillName, String filePath, String content, String commitMessage) {
        String fullPath = resolveSkillFilePath(skillName, filePath);
        String targetBranch = resolveSkillRef(skillName);
        Map<String, Object> body = new HashMap<>();
        body.put("branch", targetBranch);
        body.put("content", encodeUtf8Base64(content));
        body.put("commit_message", commitMessage);
        body.put("encoding", "base64");

        boolean exists = existsFile(fullPath, targetBranch);
        JsonNode response;
        if (exists) {
            response = restClient.put()
                    .uri(fileEndpoint(fullPath))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } else {
            response = restClient.post()
                    .uri(fileEndpoint(fullPath))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        }
        return mapCommit(response.path("commit"));
    }

    @Override
    public CommitInfo deleteFile(String skillName, String filePath, String commitMessage) {
        String fullPath = resolveSkillFilePath(skillName, filePath);
        String targetBranch = resolveSkillRef(skillName);
        Map<String, Object> body = new HashMap<>();
        body.put("branch", targetBranch);
        body.put("commit_message", commitMessage);

        JsonNode response = restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri(fileEndpoint(fullPath))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return mapCommit(response.path("commit"));
    }

    @Override
    public List<CommitInfo> listCommits(String skillName, int page, int pageSize) {
        if (isBranchSkill(skillName)) {
            return listCommitsByRef(skillName, page, pageSize);
        }
        return listCommitsByPath(skillPath(skillName), page, pageSize);
    }

    // ==================== Extended Ops ====================

    @Override
    public String getHeadCommitId(String skillName) {
        if (isBranchSkill(skillName)) {
            JsonNode branch = getBranchInfo(skillName);
            return branch.path("commit").path("id").asText(null);
        }
        List<CommitInfo> commits = listCommitsByPath(skillPath(skillName), 1, 1);
        return commits.isEmpty() ? null : commits.get(0).id();
    }

    @Override
    public String getFileAtCommit(String skillName, String filePath, String commitSha) {
        String fullPath = resolveSkillFilePath(skillName, filePath);
        JsonNode node = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(fileEndpoint(fullPath))
                        .queryParam("ref", commitSha)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        String content = node.path("content").asText();
        String encoding = node.path("encoding").asText();
        if ("base64".equalsIgnoreCase(encoding)) {
            byte[] decoded = Base64.getDecoder().decode(content.replace("\n", ""));
            return new String(decoded, StandardCharsets.UTF_8);
        }
        return content;
    }

    @Override
    public CommitInfo createSkillBranch(String skillName, String fromRef) {
        String sourceBranch = resolveDefaultBranch(fromRef);
        try {
            JsonNode response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(projectEndpoint("/repository/branches"))
                            .queryParam("branch", skillName)
                            .queryParam("ref", sourceBranch)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            branchSkillCache.put(skillName, true);
            return mapCommit(response.path("commit"));
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 400
                    && responseBody != null
                    && responseBody.toLowerCase().contains("already exists")) {
                branchSkillCache.put(skillName, true);
                return mapCommit(getBranchInfo(skillName).path("commit"));
            }
            throw ex;
        }
    }

    private String encodeUtf8Base64(String content) {
        String safeContent = content == null ? "" : content;
        return Base64.getEncoder().encodeToString(safeContent.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void deleteSkillBranch(String skillName) {
        if (isBranchSkill(skillName)) {
            try {
                restClient.delete()
                        .uri(branchEndpoint(skillName))
                        .retrieve()
                        .toBodilessEntity();
            } catch (RestClientResponseException ex) {
                if (ex.getStatusCode().value() != 404) {
                    throw ex;
                }
            }
            branchSkillCache.remove(skillName);
            return;
        }

        // 目录模式回退：按路径深度逆序删除文件，避免子路径残留。
        List<FileNode> files = getTree(skillName);
        files.stream()
                .filter(file -> file.type() == FileNode.NodeType.FILE)
                .sorted((a, b) -> Integer.compare(pathDepth(b.path()), pathDepth(a.path())))
                .forEach(file -> {
                    String fullPath = file.path();
                    if (fullPath == null || fullPath.isBlank()) {
                        fullPath = skillPath(skillName, file.name());
                    }
                    Map<String, Object> body = new HashMap<>();
                    body.put("branch", resolveDefaultBranch(properties.getBranch()));
                    body.put("commit_message", "delete " + file.name());
                    restClient.method(org.springframework.http.HttpMethod.DELETE)
                            .uri(fileEndpoint(fullPath))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(JsonNode.class);
                });
    }

    private int pathDepth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        int depth = 0;
        for (char ch : path.toCharArray()) {
            if (ch == '/') {
                depth++;
            }
        }
        return depth;
    }

    private List<CommitInfo> listCommitsByPath(String path, int page, int pageSize) {
        String targetBranch = resolveDefaultBranch(properties.getBranch());
        JsonNode json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/commits"))
                        .queryParam("ref_name", targetBranch)
                        .queryParam("path", path)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("per_page", Math.max(1, pageSize))
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<CommitInfo> commits = new ArrayList<>();
        for (JsonNode node : json) {
            commits.add(mapCommit(node));
        }
        return commits;
    }

    private List<CommitInfo> listCommitsByRef(String ref, int page, int pageSize) {
        JsonNode json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/commits"))
                        .queryParam("ref_name", ref)
                        .queryParam("page", Math.max(1, page))
                        .queryParam("per_page", Math.max(1, pageSize))
                        .build())
                .retrieve()
                .body(JsonNode.class);

        List<CommitInfo> commits = new ArrayList<>();
        for (JsonNode node : json) {
            commits.add(mapCommit(node));
        }
        return commits;
    }

    private List<SkillInfo> listSkillsFromBranches() {
        JsonNode branches = listBranches();
        List<SkillInfo> skills = new ArrayList<>();
        for (JsonNode node : branches) {
            String name = node.path("name").asText();
            if (name == null || name.isBlank()) {
                continue;
            }
            if ("main".equals(name) || "master".equals(name)) {
                continue;
            }
            branchSkillCache.put(name, true);
            CommitInfo latest = mapCommit(node.path("commit"));
            skills.add(new SkillInfo(
                    name,
                    name,
                    name,
                    latest.id(),
                    latest.authoredAt()
            ));
        }
        return skills;
    }

    private List<SkillInfo> listSkillsFromDirectories() {
        List<SkillInfo> result = new ArrayList<>();
        JsonNode tree = listTree(resolveDefaultBranch(properties.getBranch()), rawRootPath());
        boolean hasDirectory = false;
        for (JsonNode node : tree) {
            if (!"tree".equalsIgnoreCase(node.path("type").asText())) {
                continue;
            }
            hasDirectory = true;
            String name = node.path("name").asText();
            String path = node.path("path").asText();
            List<CommitInfo> commits = listCommitsByPath(skillPath(name), 1, 1);
            CommitInfo latest = commits.isEmpty() ? null : commits.get(0);
            result.add(new SkillInfo(
                    name,
                    path,
                    properties.getBranch(),
                    latest == null ? null : latest.id(),
                    latest == null ? null : latest.authoredAt()
            ));
        }
        if (!hasDirectory && tree != null && tree.size() > 0) {
            List<CommitInfo> commits = listCommitsByPath(rawRootPath(), 1, 1);
            CommitInfo latest = commits.isEmpty() ? null : commits.get(0);
            result.add(new SkillInfo(
                    ROOT_SKILL,
                    rawRootPath().isEmpty() ? "/" : rawRootPath(),
                    properties.getBranch(),
                    latest == null ? null : latest.id(),
                    latest == null ? null : latest.authoredAt()
            ));
        }
        return result;
    }

    private JsonNode listTree(String ref, String path) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/tree"))
                        .queryParam("ref", ref)
                        .queryParam("path", path)
                        .queryParam("recursive", true)
                        .queryParam("per_page", 200)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode listBranches() {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/branches"))
                        .queryParam("per_page", 200)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private boolean existsFile(String fullPath, String ref) {
        try {
            getFileMeta(fullPath, ref);
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private JsonNode getFileMeta(String fullPath, String ref) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(fileEndpoint(fullPath))
                        .queryParam("ref", ref)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private JsonNode getBranchInfo(String branchName) {
        return restClient.get()
                .uri(branchEndpoint(branchName))
                .retrieve()
                .body(JsonNode.class);
    }

    private String resolveSkillRef(String skillName) {
        if (isBranchSkill(skillName)) {
            return skillName;
        }
        return resolveDefaultBranch(properties.getBranch());
    }

    private String resolveSkillFilePath(String skillName, String filePath) {
        String cleanFilePath = normalizeFilePath(filePath);
        if (isBranchSkill(skillName)) {
            return cleanFilePath;
        }
        return skillPath(skillName, cleanFilePath);
    }

    private boolean isBranchSkill(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            return false;
        }
        return branchSkillCache.computeIfAbsent(skillName.trim(), this::branchExists);
    }

    private CommitInfo mapCommit(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new CommitInfo(null, null, null, null, null, null);
        }
        Instant authoredAt = null;
        String authoredDate = node.path("authored_date").asText(null);
        if (authoredDate == null || authoredDate.isBlank()) {
            authoredDate = node.path("committed_date").asText(null);
        }
        if (authoredDate == null || authoredDate.isBlank()) {
            authoredDate = node.path("created_at").asText(null);
        }
        if (authoredDate != null && !authoredDate.isBlank()) {
            authoredAt = Instant.parse(authoredDate);
        }
        return new CommitInfo(
                node.path("id").asText(null),
                node.path("title").asText(null),
                node.path("message").asText(null),
                node.path("author_name").asText(null),
                authoredAt,
                node.path("web_url").asText(null)
        );
    }

    private String rawRootPath() {
        return trimSlashes(properties.getRootPath());
    }

    private String skillPath(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new SkillException("skillName cannot be blank");
        }
        String root = rawRootPath();
        if (ROOT_SKILL.equalsIgnoreCase(trimSlashes(skillName))) {
            return root;
        }
        String skill = trimSlashes(skillName);
        return root.isEmpty() ? skill : root + "/" + skill;
    }

    private String skillPath(String skillName, String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new SkillException("filePath cannot be blank");
        }
        String cleanFilePath = trimSlashes(filePath);
        String base = skillPath(skillName);
        return base.isEmpty() ? cleanFilePath : base + "/" + cleanFilePath;
    }

    private String fileEndpoint(String fullPath) {
        String encoded = UriUtils.encodePathSegment(fullPath, StandardCharsets.UTF_8);
        return projectEndpoint("/repository/files/" + encoded);
    }

    private String branchEndpoint(String branchName) {
        String encoded = UriUtils.encodePathSegment(branchName, StandardCharsets.UTF_8);
        return projectEndpoint("/repository/branches/" + encoded);
    }

    private String resolveDefaultBranch(String preferredBranch) {
        String cached = cachedDefaultBranch;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, preferredBranch);
        addCandidate(candidates, properties.getBranch());
        addCandidate(candidates, "main");
        addCandidate(candidates, "master");

        for (String candidate : candidates) {
            if (branchExists(candidate)) {
                cachedDefaultBranch = candidate;
                return candidate;
            }
        }

        String fallback = !candidates.isEmpty() ? candidates.get(0) : "main";
        cachedDefaultBranch = fallback;
        return fallback;
    }

    private boolean branchExists(String branchName) {
        try {
            restClient.get()
                    .uri(branchEndpoint(branchName))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void addCandidate(List<String> candidates, String branchName) {
        if (branchName == null || branchName.isBlank()) {
            return;
        }
        String normalized = branchName.trim();
        if (!candidates.contains(normalized)) {
            candidates.add(normalized);
        }
    }

    private String projectEndpoint(String suffix) {
        String encodedProjectId = UriUtils.encodePathSegment(properties.getProjectId(), StandardCharsets.UTF_8);
        return "/api/v4/projects/" + encodedProjectId + suffix;
    }

    private String trimSlashes(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "";
        }
        String out = text;
        while (out.startsWith("/")) {
            out = out.substring(1);
        }
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private String normalizeFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new SkillException("filePath cannot be blank");
        }
        String normalized = filePath.trim().replace('\\', '/');
        for (int i = 0; i < 3; i++) {
            try {
                String decoded = UriUtils.decode(normalized, StandardCharsets.UTF_8);
                if (decoded.equals(normalized)) {
                    break;
                }
                normalized = decoded;
            } catch (IllegalArgumentException ignored) {
                break;
            }
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.isBlank()) {
            throw new SkillException("filePath cannot be blank");
        }
        return normalized;
    }

    private void validate() {
        if (properties.effectiveUrl() == null || properties.effectiveUrl().isBlank()) {
            throw new SkillException("linkwork.agent.skill.gitlab.url or linkwork.agent.skill.gitlab.repo-url is required");
        }
        if (properties.effectiveToken() == null || properties.effectiveToken().isBlank()) {
            throw new SkillException("linkwork.agent.skill.gitlab.token or linkwork.agent.skill.gitlab.deploy-token is required");
        }
        if (properties.getProjectId() == null || properties.getProjectId().isBlank()) {
            throw new SkillException("linkwork.agent.skill.gitlab.project-id is required");
        }
    }
}
