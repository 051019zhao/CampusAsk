"""In-process read-only MCP tools backed by the authorized Java bridge."""
from __future__ import annotations

import asyncio
import json
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from fastmcp import FastMCP
from langchain.mcp import MCPAdapter


def campus_server(bridge: Any, owner: str, session_id: str) -> FastMCP:
    server = FastMCP("CampusAsk read-only tools")

    @server.tool
    def search_campus_knowledge(query: str) -> dict[str, Any]:
        """检索校园知识库和当前会话资料；回答校园事实前必须调用。"""
        return bridge.post("search", {"owner": owner, "sessionId": session_id, "query": query})

    @server.tool
    def list_my_documents() -> list[dict[str, Any]]:
        """列出当前登录用户在本会话上传的资料。"""
        return bridge.post("documents", {"owner": owner, "sessionId": session_id})

    @server.tool
    def read_campus_source(documentId: str) -> dict[str, Any]:
        """读取已检索来源在本地知识库中的正文，不会打开原网页。"""
        return bridge.post("source", {"owner": owner, "sessionId": session_id,
                                      "documentId": documentId})

    @server.tool
    def current_date() -> dict[str, str]:
        """获取当前北京时间日期，供校园公告时效判断。"""
        return {"date": datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()}

    return server


async def _catalog(server: FastMCP) -> list[dict[str, Any]]:
    async with MCPAdapter(server) as adapter:
        tools = await adapter.list_tools()
        return [{"type": "function", "function": {"name": tool.name,
                 "description": tool.description,
                 "parameters": tool.args_schema if isinstance(tool.args_schema, dict)
                 else tool.args_schema.model_json_schema()}} for tool in tools]


def tool_catalog(server: FastMCP) -> list[dict[str, Any]]:
    try:
        catalog = asyncio.run(_catalog(server))
    except Exception as exc:
        raise ValueError("MCP tool discovery failed") from exc
    required = {"search_campus_knowledge", "list_my_documents", "read_campus_source", "current_date"}
    if {item["function"]["name"] for item in catalog} != required:
        raise ValueError("MCP tool catalog does not match the read-only allowlist")
    return catalog


async def _invoke(server: FastMCP, name: str, args: dict[str, Any]) -> Any:
    async with MCPAdapter(server) as adapter:
        tools = {tool.name: tool for tool in await adapter.list_tools()}
        if name not in tools:
            raise ValueError("Unknown MCP tool")
        response = await tools[name].ainvoke({"type": "tool_call", "name": name,
                                               "args": args, "id": "campusask-mcp"})
        if getattr(response, "status", "success") == "error":
            raise ValueError("MCP tool failed")
        artifact = getattr(response, "artifact", None)
        if isinstance(artifact, dict) and "structured_content" in artifact:
            content = artifact["structured_content"]
            return content["result"] if isinstance(content, dict) and set(content) == {"result"} else content
        blocks = getattr(response, "content", [])
        if isinstance(blocks, list) and len(blocks) == 1 and blocks[0].get("type") == "text":
            return json.loads(blocks[0]["text"])
        raise ValueError("MCP tool returned no structured result")


def invoke_tool(server: FastMCP, name: str, args: dict[str, Any]) -> Any:
    try:
        return asyncio.run(_invoke(server, name, args))
    except Exception as exc:
        raise ValueError("MCP tool invocation failed") from exc
