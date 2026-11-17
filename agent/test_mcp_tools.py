import unittest

from agent.mcp_tools import campus_server, invoke_tool, tool_catalog
from agent.app import build_graph


class Bridge:
    def __init__(self):
        self.calls = []

    def post(self, path, body):
        self.calls.append((path, body))
        if path == "search":
            return {"citations": [{"documentId": "K1", "excerpt": "图书馆开放安排见公告"}],
                    "retrievalMode": "milvus"}
        if path == "documents":
            return [{"id": "D1", "title": "我的资料"}]
        if path == "source":
            return {"documentId": body["documentId"], "chunks": [{"text": "公告正文"}]}
        raise AssertionError(path)


class McpToolTest(unittest.TestCase):
    def test_adapter_discovers_read_only_catalog_and_preserves_identity(self):
        bridge = Bridge()
        server = campus_server(bridge, "USER:alice", "session-1")
        schemas = tool_catalog(server)
        self.assertEqual({"search_campus_knowledge", "list_my_documents",
                          "read_campus_source", "current_date"},
                         {item["function"]["name"] for item in schemas})
        self.assertEqual("milvus", invoke_tool(server, "search_campus_knowledge",
                                                {"query": "图书馆"})["retrievalMode"])
        self.assertEqual("D1", invoke_tool(server, "list_my_documents", {})[0]["id"])
        self.assertEqual("K1", invoke_tool(server, "read_campus_source",
                                           {"documentId": "K1"})["documentId"])
        self.assertRegex(invoke_tool(server, "current_date", {})["date"], r"^\d{4}-\d{2}-\d{2}$")
        self.assertEqual([("search", {"owner": "USER:alice", "sessionId": "session-1", "query": "图书馆"}),
                          ("documents", {"owner": "USER:alice", "sessionId": "session-1"}),
                          ("source", {"owner": "USER:alice", "sessionId": "session-1",
                                      "documentId": "K1"})], bridge.calls)

    def test_langgraph_invokes_mcp_search_and_keeps_citation_checks(self):
        class GraphBridge(Bridge):
            def post(self, path, body):
                if path == "history":
                    return []
                if path == "model":
                    self.calls.append((path, body))
                    if sum(name == "model" for name, _ in self.calls) == 1:
                        return {"role": "assistant", "content": None, "tool_calls": [{"id": "c1",
                            "function": {"name": "search_campus_knowledge", "arguments": '{"query":"图书馆"}'}}]}
                    return {"role": "assistant", "content": "开放安排见公告 [S1]。"}
                if path == "verify":
                    return {"unsupported": []}
                return super().post(path, body)

        bridge = GraphBridge()
        server = campus_server(bridge, "USER:alice", "session-1")
        state = {"messages": [{"role": "system", "content": "test"},
                              {"role": "user", "content": "图书馆开放时间"}],
                 "owner": "USER:alice", "session_id": "session-1", "turns": 0,
                 "tool_count": 0, "search_count": 0, "citations": [], "steps": [],
                 "answer": "", "mode": "lexical", "history": [], "verified": False,
                 "memory_enabled": False, "verification_attempts": 0}
        result = build_graph(bridge, server, tool_catalog(server)).invoke(state)
        self.assertTrue(result["verified"])
        self.assertEqual("milvus", result["mode"])
        self.assertEqual(1, result["search_count"])
        self.assertTrue(any(name == "search" for name, _ in bridge.calls))


if __name__ == "__main__":
    unittest.main()
