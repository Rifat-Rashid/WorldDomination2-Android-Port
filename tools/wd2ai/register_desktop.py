"""Test and register the running WD2 MCP server with the local Unsloth Desktop backend."""
import argparse
import asyncio
import os

from studio import studio_request


async def register(port):
    token = os.environ["WD2_MCP_TOKEN"]
    url = f"http://127.0.0.1:{port}/mcp"
    config = {"url": url, "headers": {"Authorization": "Bearer " + token}}
    probe = await studio_request("/api/mcp/servers/test", "POST", config)
    if not probe["ok"]:
        raise RuntimeError("Desktop could not discover tools: " + str(probe.get("error")))
    matches = [item for item in await studio_request("/api/mcp/servers/") if item["url"] == url]
    name = "WD2 local AI"
    if matches:
        if len(matches) != 1 or matches[0]["display_name"] != name:
            raise RuntimeError("This URL is already assigned to another registration")
        path = "/api/mcp/servers/" + matches[0]["id"]
        row = await studio_request(path, "PUT", config)
    else:
        row = await studio_request("/api/mcp/servers/", "POST", dict(config, display_name=name))
    print(f"Desktop registered {name} ({row['id']}), discovered {probe['tool_count']} tools.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8794)
    asyncio.run(register(parser.parse_args().port))
