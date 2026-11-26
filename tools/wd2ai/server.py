"""World Domination 2 development MCP. Run with the tools/wd2ai environment."""
import argparse
import hmac
import os

import uvicorn
from mcp.server import MCPServer
from mcp.types import ToolAnnotations
from starlette.responses import PlainTextResponse

from studio import export_status, studio_health, training_hardware, training_status
from game_tools import game_start, game_observe, game_choose, game_finish, game_replay
from training_tools import preview_reviewed_training, start_reviewed_training, wd2_training_status

server = MCPServer("wd2-unsloth", version="1.0", instructions=(
    "Inspect Unsloth Studio on this computer. Use studio_health for connection checks. "
    "Read training_status before scheduling WD2 work."))
for tool in (studio_health, training_status, training_hardware, export_status, wd2_training_status):
    server.tool(annotations=ToolAnnotations(read_only_hint=True, open_world_hint=False))(tool)
for tool in (game_start, game_observe, game_choose, game_finish, game_replay):
    server.tool(annotations=ToolAnnotations(read_only_hint=tool in (game_observe, game_replay),
                                           destructive_hint=False, open_world_hint=False))(tool)
for tool in (preview_reviewed_training, start_reviewed_training):
    server.tool(annotations=ToolAnnotations(read_only_hint=False, open_world_hint=False))(tool)


class LocalBearer:
    def __init__(self, app, token):
        self.app = app
        self.expected = ("Bearer " + token).encode("ascii")

    async def __call__(self, scope, receive, send):
        if scope["type"] == "http":
            headers = dict(scope["headers"])
            if not hmac.compare_digest(headers.get(b"authorization", b""), self.expected):
                await PlainTextResponse("Authorization required", status_code=401,
                                        headers={"WWW-Authenticate": "Bearer"})(scope, receive, send)
                return
        await self.app(scope, receive, send)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--http", action="store_true", help="Serve authenticated loopback HTTP")
    parser.add_argument("--port", type=int, default=8794)
    args = parser.parse_args()
    if not args.http:
        server.run()
        return
    token = os.environ.get("WD2_MCP_TOKEN", "")
    if len(token) < 32 or not token.isascii():
        parser.error("Set WD2_MCP_TOKEN to a random ASCII token of at least 32 characters")
    app = server.streamable_http_app(json_response=True, stateless_http=True)
    uvicorn.run(LocalBearer(app, token), host="127.0.0.1", port=args.port, access_log=False)


if __name__ == "__main__":
    main()
