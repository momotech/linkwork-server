package com.momo.agent.mcp.core.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

public class JsonRpcRequest {

    private String jsonrpc = "2.0";
    private String id;
    private String method;
    private Map<String, Object> params = new LinkedHashMap<>();

    public static JsonRpcRequest of(String id, String method, Map<String, Object> params) {
        JsonRpcRequest request = new JsonRpcRequest();
        request.id = id;
        request.method = method;
        request.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
        return request;
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params);
    }
}
