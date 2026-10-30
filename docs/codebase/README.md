# Codebase field guide

This is a static documentation website for contributors. It does not participate in the Android build or gameplay.

From the repository root, serve `docs/codebase` with any static server, for example:

```powershell
python -m http.server 8765 --directory docs/codebase
```

Open `http://localhost:8765/`, or open `index.html` directly. The tutorial links to `classes.html`, which groups every production Java class with its fields, constructors, and methods. Expanding a method shows its parameters, local declarations, referenced field names, and calls. The searchable index is generated from the production Java source at the commit shown on the page.

After `main` changes, install `docs/codebase/requirements.txt`, rerun `python docs/codebase/generate.py`, review the changed symbol index, and commit it with the documentation update. The generator reads source files and writes only `docs/codebase/api.json`.
