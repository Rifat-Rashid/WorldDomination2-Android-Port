"""Documented local Studio REST operations shared by the MCP transports."""
import ipaddress
import os
from urllib.parse import urlsplit

import httpx


def studio_url():
    url = os.environ.get("WD2_STUDIO_URL", "http://127.0.0.1:8888")
    parts = urlsplit(url)
    if (parts.scheme != "http" or parts.username is not None or parts.password is not None
            or parts.query or parts.fragment or parts.path not in ("", "/")
            or not ipaddress.ip_address(parts.hostname or "").is_loopback):
        raise ValueError("WD2_STUDIO_URL must be an HTTP loopback IP and optional port")
    return url.rstrip("/")


async def studio_request(path, method="GET", payload=None):
    token = os.environ.get("WD2_STUDIO_TOKEN")
    headers = {"Authorization": "Bearer " + token} if token else {}
    async with httpx.AsyncClient(base_url=studio_url(), trust_env=False, timeout=15,
                                 follow_redirects=False, headers=headers) as client:
        response = await client.request(method, path, json=payload)
    if response.status_code in (401, 403):
        raise ValueError("Studio requires an authorized local session or WD2_STUDIO_TOKEN")
    if not response.is_success:
        raise ValueError(f"Studio returned HTTP {response.status_code} for {path}")
    return response.json()


async def studio_health():
    """Check the running Desktop backend version, device type and training availability."""
    data = await studio_request("/api/health")
    return {key: data.get(key) for key in
            ("status", "version", "studio_version", "device_type", "chat_only", "chat_only_reason")}


async def training_status():
    """Read Studio's active training job, phase and diagnostic status."""
    data = await studio_request("/api/train/status")
    return {key: data.get(key) for key in
            ("job_id", "phase", "is_training_running", "message", "error", "warnings")}


async def export_status():
    """Read the state of the local Studio model export job."""
    data = await studio_request("/api/export/status")
    return {key: data.get(key) for key in
            ("is_export_active", "active_op_kind", "last_op_kind", "last_op_status", "last_op_error")}


async def training_hardware():
    """Inspect the GPU and precision capabilities reported by Studio."""
    return await studio_request("/api/train/hardware")

