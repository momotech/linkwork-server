package com.linkwork.agent.skill.provider.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkwork.agent.skill.core.SkillException;
import com.linkwork.agent.skill.core.SkillProvider;
import com.linkwork.agent.skill.core.SkillProviderExtendedOps;
import com.linkwork.agent.skill.core.model.CommitInfo;
import com.linkwork.agent.skill.core.model.FileNode;
import com.linkwork.agent.skill.core.model.SkillInfo;
import org.springframework.http.HttpMethod;
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

public class GitLabProviderImpl implements SkillProvider, SkillProviderExtendedOps {
    private static final String ROOT_SKILL = "root";
    private final RestClient restClient;
    private final GitLabProperties properties;

    public GitLabProviderImpl(RestClient restClient, GitLabProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        validate();
    }

    @Override
    public List<SkillInfo> listSkills() {
        return isBranchMode() ? listBranchSkills() : listTreeSkills();
    }

    @Override
    public List<FileNode> getTree(String skillName) {
        JsonNode tree;
        if (isBranchMode()) {
            String branch = ensureBranchSkillName(skillName);
            tree = listTreeByRef(branch, null, true);
        } else {
            tree = listTreeByRef(properties.getBranch(), skillPath(skillName), false);
        }

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
        String targetRef = resolveRef(skillName);
        String fullPath = resolveFilePath(skillName, filePath);
        JsonNode node = getFileMeta(fullPath, targetRef);
        return decodeContent(node);
    }

    @Override
    public CommitInfo upsertFile(String skillName, String filePath, String content, String commitMessage) {
        String targetRef = resolveRef(skillName);
        String fullPath = resolveFilePath(skillName, filePath);

        Map<String, Object> body = new HashMap<>();
        body.put("branch", targetRef);
        body.put("content", content);
        body.put("commit_message", commitMessage);
        body.put("encoding", "text");

        boolean exists = existsFile(fullPath, targetRef);
        JsonNode response;
        if (exists) {
            response = restClient.put()
                .uri(fileEndpointPath(fullPath))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        } else {
            response = restClient.post()
                .uri(fileEndpointPath(fullPath))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        }
        return mapCommit(response.path("commit"));
    }

    @Override
    public CommitInfo deleteFile(String skillName, String filePath, String commitMessage) {
        String targetRef = resolveRef(skillName);
        String fullPath = resolveFilePath(skillName, filePath);

        Map<String, Object> body = new HashMap<>();
        body.put("branch", targetRef);
        body.put("commit_message", commitMessage);

        JsonNode response = restClient.method(HttpMethod.DELETE)
            .uri(fileEndpointPath(fullPath))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        return mapCommit(response.path("commit"));
    }

    @Override
    public List<CommitInfo> listCommits(String skillName, int page, int pageSize) {
        if (isBranchMode()) {
            return listCommitsByRef(ensureBranchSkillName(skillName), null, page, pageSize);
        }
        return listCommitsByRef(properties.getBranch(), skillPath(skillName), page, pageSize);
    }

    // ==================== Extended Ops ====================

    @Override
    public String getHeadCommitId(String skillName) {
        List<CommitInfo> commits;
        if (isBranchMode()) {
            commits = listCommitsByRef(ensureBranchSkillName(skillName), null, 1, 1);
        } else {
            commits = listCommitsByRef(properties.getBranch(), skillPath(skillName), 1, 1);
        }
        return commits.isEmpty() ? null : commits.get(0).id();
    }

    @Override
    public String getFileAtCommit(String skillName, String filePath, String commitSha) {
        String fullPath = resolveFilePath(skillName, filePath);
        JsonNode node = restClient.get()
            .uri(fileEndpointWithRef(fullPath, commitSha))
            .retrieve()
            .body(JsonNode.class);
        return decodeContent(node);
    }

    @Override
    public CommitInfo createSkillBranch(String skillName, String fromRef) {
        if (isBranchMode()) {
            String targetBranch = ensureBranchSkillName(skillName);
            String sourceRef = (fromRef != null && !fromRef.isBlank()) ? fromRef : properties.getBranch();
            JsonNode response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                    .path(projectEndpoint("/repository/branches"))
                    .queryParam("branch", targetBranch)
                    .queryParam("ref", sourceRef)
                    .build())
                .retrieve()
                .body(JsonNode.class);
            return mapCommit(response.path("commit"));
        }

        String path = skillPath(skillName);
        String readmePath = path + "/README.md";
        Map<String, Object> body = new HashMap<>();
        body.put("branch", properties.getBranch());
        body.put("content", "# " + skillName + "\n");
        body.put("commit_message", "init skill " + skillName);
        body.put("encoding", "text");
        JsonNode response = restClient.post()
            .uri(fileEndpointPath(readmePath))
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        return mapCommit(response.path("commit"));
    }

    @Override
    public void deleteSkillBranch(String skillName) {
        if (isBranchMode()) {
            restClient.method(HttpMethod.DELETE)
                .uri(projectEndpoint("/repository/branches/" + encodePathSegment(ensureBranchSkillName(skillName))))
                .retrieve()
                .toBodilessEntity();
            return;
        }

        List<FileNode> files = getTree(skillName);
        for (FileNode file : files) {
            if (file.type() == FileNode.NodeType.FILE) {
                String fullPath = skillPath(skillName, file.name());
                String pathFromNode = file.path();
                if (pathFromNode != null && !pathFromNode.isBlank()) {
                    fullPath = pathFromNode;
                }
                Map<String, Object> body = new HashMap<>();
                body.put("branch", properties.getBranch());
                body.put("commit_message", "delete " + file.name());
                restClient.method(HttpMethod.DELETE)
                    .uri(fileEndpointPath(fullPath))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            }
        }
    }

    private List<SkillInfo> listTreeSkills() {
        List<SkillInfo> result = new ArrayList<>();
        JsonNode tree = listTreeByRef(properties.getBranch(), rawRootPath(), false);
        boolean hasDirectory = false;
        for (JsonNode node : tree) {
            if (!"tree".equalsIgnoreCase(node.path("type").asText())) {
                continue;
            }
            hasDirectory = true;
            String name = node.path("name").asText();
            String path = node.path("path").asText();
            List<CommitInfo> commits = listCommitsByRef(properties.getBranch(), skillPath(name), 1, 1);
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
            List<CommitInfo> commits = listCommitsByRef(properties.getBranch(), rawRootPath(), 1, 1);
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

    private List<SkillInfo> listBranchSkills() {
        JsonNode branches = restClient.get()
            .uri(uriBuilder -> uriBuilder
                .path(projectEndpoint("/repository/branches"))
                .queryParam("per_page", 200)
                .build())
            .retrieve()
            .body(JsonNode.class);

        List<SkillInfo> result = new ArrayList<>();
        if (branches == null || !branches.isArray()) {
            return result;
        }

        for (JsonNode node : branches) {
            String name = node.path("name").asText();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (isIgnoredBranch(name)) {
                continue;
            }

            JsonNode commit = node.path("commit");
            String commitId = commit.path("id").asText(null);
            Instant authoredAt = parseInstant(
                firstNonBlank(
                    commit.path("committed_date").asText(null),
                    commit.path("authored_date").asText(null),
                    commit.path("created_at").asText(null)
                )
            );
            result.add(new SkillInfo(name, "/", name, commitId, authoredAt));
        }
        return result;
    }

    private List<CommitInfo> listCommitsByRef(String refName, String path, int page, int pageSize) {
        JsonNode json = restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder
                    .path(projectEndpoint("/repository/commits"))
                    .queryParam("ref_name", refName)
                    .queryParam("page", Math.max(1, page))
                    .queryParam("per_page", Math.max(1, pageSize));
                if (path != null && !path.isBlank()) {
                    builder.queryParam("path", path);
                }
                return builder.build();
            })
            .retrieve()
            .body(JsonNode.class);

        List<CommitInfo> commits = new ArrayList<>();
        if (json == null || !json.isArray()) {
            return commits;
        }

        for (JsonNode node : json) {
            commits.add(mapCommit(node));
        }
        return commits;
    }

    private JsonNode listTreeByRef(String ref, String path, boolean recursive) {
        return restClient.get()
            .uri(uriBuilder -> {
                var builder = uriBuilder
                    .path(projectEndpoint("/repository/tree"))
                    .queryParam("ref", ref)
                    .queryParam("per_page", 200);
                if (path != null && !path.isBlank()) {
                    builder.queryParam("path", path);
                }
                if (recursive) {
                    builder.queryParam("recursive", true);
                }
                return builder.build();
            })
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
            .uri(fileEndpointWithRef(fullPath, ref))
            .retrieve()
            .body(JsonNode.class);
    }

    private CommitInfo mapCommit(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new CommitInfo(null, null, null, null, null, null);
        }
        Instant authoredAt = parseInstant(
            firstNonBlank(
                node.path("authored_date").asText(null),
                node.path("committed_date").asText(null),
                node.path("created_at").asText(null)
            )
        );
        return new CommitInfo(
            node.path("id").asText(null),
            node.path("title").asText(null),
            node.path("message").asText(null),
            node.path("author_name").asText(null),
            authoredAt,
            node.path("web_url").asText(null)
        );
    }

    private String decodeContent(JsonNode node) {
        String content = node.path("content").asText();
        String encoding = node.path("encoding").asText();
        if ("base64".equalsIgnoreCase(encoding)) {
            byte[] decoded = Base64.getDecoder().decode(content.replace("\n", ""));
            return new String(decoded, StandardCharsets.UTF_8);
        }
        return content;
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

    private String resolveRef(String skillName) {
        return isBranchMode() ? ensureBranchSkillName(skillName) : properties.getBranch();
    }

    private String resolveFilePath(String skillName, String filePath) {
        if (isBranchMode()) {
            if (filePath == null || filePath.isBlank()) {
                throw new SkillException("filePath cannot be blank");
            }
            return trimSlashes(filePath);
        }
        return skillPath(skillName, filePath);
    }

    private String ensureBranchSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new SkillException("skillName cannot be blank");
        }
        return skillName.trim();
    }

    private boolean isIgnoredBranch(String branchName) {
        String normalized = branchName.trim();
        if ("main".equalsIgnoreCase(normalized) || "master".equalsIgnoreCase(normalized)) {
            return true;
        }
        String baseBranch = properties.getBranch();
        return baseBranch != null && !baseBranch.isBlank() && normalized.equalsIgnoreCase(baseBranch.trim());
    }

    private boolean isBranchMode() {
        return properties.isBranchPerSkillMode();
    }

    /**
     * Build GitLab file endpoint path.
     *
     * NOTE:
     * We return an already encoded string path (e.g. scripts%2Fmake.sh) and pass it via
     * RestClient#uri(String) to avoid UriBuilder double-encoding (%2F -> %252F).
     */
    private String fileEndpointPath(String fullPath) {
        String encoded = encodePathSegment(fullPath);
        return projectEndpoint("/repository/files/" + encoded);
    }

    private String fileEndpointWithRef(String fullPath, String ref) {
        String encodedRef = UriUtils.encodeQueryParam(ref, StandardCharsets.UTF_8);
        return fileEndpointPath(fullPath) + "?ref=" + encodedRef;
    }

    private String projectEndpoint(String suffix) {
        String encodedProjectId = encodePathSegment(properties.getProjectId());
        return "/api/v4/projects/" + encodedProjectId + suffix;
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private Instant parseInstant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private void validate() {
        if (properties.effectiveUrl() == null || properties.effectiveUrl().isBlank()) {
            throw new SkillException("agent.skill.gitlab.url or agent.skill.gitlab.repo-url is required");
        }
        if (properties.effectiveToken() == null || properties.effectiveToken().isBlank()) {
            throw new SkillException("agent.skill.gitlab.token or agent.skill.gitlab.deploy-token is required");
        }
        if (properties.getProjectId() == null || properties.getProjectId().isBlank()) {
            throw new SkillException("agent.skill.gitlab.project-id is required");
        }
        if (isBranchMode() && (properties.getBranch() == null || properties.getBranch().isBlank())) {
            throw new SkillException("agent.skill.gitlab.branch is required in branch-per-skill mode");
        }
    }
}
