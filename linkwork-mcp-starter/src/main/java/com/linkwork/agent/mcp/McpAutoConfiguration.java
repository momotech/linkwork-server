package com.linkwork.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkwork.agent.mcp.client.DirectMcpClient;
import com.linkwork.agent.mcp.client.GatewayMcpClient;
import com.linkwork.agent.mcp.client.NoopMcpClient;
import com.linkwork.agent.mcp.core.McpClient;
import com.linkwork.agent.mcp.transport.McpTransport;
import com.linkwork.agent.mcp.transport.sse.SseMcpTransport;
import com.linkwork.agent.mcp.transport.stdio.StdioMcpTransport;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@ConditionalOnClass(RestTemplate.class)
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {

    @Bean(name = "mcpObjectMapper")
    @ConditionalOnMissingBean(name = "mcpObjectMapper")
    public ObjectMapper mcpObjectMapper(ObjectMapper objectMapper) {
        // 复用应用侧 Jackson 配置（含 JavaTimeModule），避免 starter 覆盖导致 LocalDateTime 序列化失败
        return objectMapper.copy();
    }

    @Bean
    @ConditionalOnMissingBean(name = "mcpRestTemplate")
    public RestTemplate mcpRestTemplate(McpProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getClient().getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getClient().getReadTimeoutMs());
        return new RestTemplate(requestFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public SseMcpTransport sseMcpTransport(RestTemplate mcpRestTemplate,
                                           @Qualifier("mcpObjectMapper") ObjectMapper mcpObjectMapper) {
        return new SseMcpTransport(mcpRestTemplate, mcpObjectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public StdioMcpTransport stdioMcpTransport(@Qualifier("mcpObjectMapper") ObjectMapper mcpObjectMapper) {
        return new StdioMcpTransport(mcpObjectMapper);
    }

    @Bean
    @ConditionalOnExpression("'${linkwork.agent.mcp.enabled:true}' == 'true' and '${linkwork.agent.mcp.mode:direct}' == 'direct'")
    @ConditionalOnMissingBean(McpClient.class)
    public McpClient directMcpClient(List<McpTransport> transports) {
        return new DirectMcpClient(transports);
    }

    @Bean
    @ConditionalOnExpression("'${linkwork.agent.mcp.enabled:true}' == 'true' and '${linkwork.agent.mcp.mode:direct}' == 'gateway'")
    @ConditionalOnMissingBean(McpClient.class)
    public McpClient gatewayMcpClient(RestTemplate mcpRestTemplate, McpProperties properties) {
        return new GatewayMcpClient(mcpRestTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(McpClient.class)
    public McpClient noopMcpClient() {
        return new NoopMcpClient();
    }
}
