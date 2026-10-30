# Codebase field guide

This is a static documentation website for contributors. It does not participate in the Android build or gameplay.

From the repository root, serve `docs/codebase` with any static server, for example:

```powershell
python -m http.server 8765 --directory docs/codebase
```

Open `http://localhost:8765/`. The searchable index is generated from the production Java source at the commit shown on the page.

After `main` changes, install `docs/codebase/requirements.txt`, rerun `python docs/codebase/generate.py`, review the changed symbol index, and commit it with the documentation update. The generator reads source files and writes only `docs/codebase/api.json`.
