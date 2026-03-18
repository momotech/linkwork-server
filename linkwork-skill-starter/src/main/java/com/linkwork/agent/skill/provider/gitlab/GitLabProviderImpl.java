package com.linkwork.agent.skill.provider.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.linkwork.agent.skill.core.SkillException;
import com.linkwork.agent.skill.core.SkillProvider;
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

public class GitLabProviderImpl implements SkillProvider {
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
        List<SkillInfo> result = new ArrayList<>();
        JsonNode tree = listTree(rawRootPath());
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

    @Override
    public List<FileNode> getTree(String skillName) {
        JsonNode tree = listTree(skillPath(skillName));
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
        String fullPath = skillPath(skillName, filePath);
        JsonNode node = getFileMeta(fullPath);
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
        String fullPath = skillPath(skillName, filePath);
        Map<String, Object> body = new HashMap<>();
        body.put("branch", properties.getBranch());
        body.put("content", content);
        body.put("commit_message", commitMessage);
        body.put("encoding", "text");

        boolean exists = existsFile(fullPath);
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
        String fullPath = skillPath(skillName, filePath);
        Map<String, Object> body = new HashMap<>();
        body.put("branch", properties.getBranch());
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
        return listCommitsByPath(skillPath(skillName), page, pageSize);
    }

    private List<CommitInfo> listCommitsByPath(String path, int page, int pageSize) {
        JsonNode json = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/commits"))
                        .queryParam("ref_name", properties.getBranch())
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

    private JsonNode listTree(String path) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(projectEndpoint("/repository/tree"))
                        .queryParam("ref", properties.getBranch())
                        .queryParam("path", path)
                        .queryParam("per_page", 200)
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private boolean existsFile(String fullPath) {
        try {
            getFileMeta(fullPath);
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private JsonNode getFileMeta(String fullPath) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(fileEndpoint(fullPath))
                        .queryParam("ref", properties.getBranch())
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    private CommitInfo mapCommit(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return new CommitInfo(null, null, null, null, null, null);
        }
        Instant authoredAt = null;
        String authoredDate = node.path("authored_date").asText(null);
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
    }
}
