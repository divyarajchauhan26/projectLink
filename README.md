# projectLink
This project models a college social network using graph data structures. Users are represented as nodes, friendships as edges, and BFS/DFS are used for shortest and exhaustive path discovery.

## Running it

The quickest way — builds if needed, then launches:

```powershell
.un.ps1
```

Other modes:

```powershell
.un.ps1 -Test     # run the nine verification harnesses (234 checks)
.un.ps1 -Clean    # force a full rebuild first
```

In VS Code, F5 also works once the Java extension has picked up the `lib/` jars.

---

## Setup & Running (manual)

### Compile (PowerShell):
```powershell
cd PorjectLink
javac -d out -cp "lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar" (Get-ChildItem -Recurse -Filter *.java src | ForEach-Object FullName)
```

### Compile (bash / Git Bash):
```bash
cd PorjectLink
javac -d out -cp "lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar;lib/gson-2.11.0.jar" $(find src -name "*.java")
```

### Run:
```bash
java -cp "out;lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar" CampusConnect.main.Main
```

### Dev harnesses (headless, no window):
```bash
java -cp out CampusConnect.dev.InterestCatalogHarness
java -cp "out;lib/gson-2.11.0.jar" CampusConnect.dev.SeedHarness
```

### Adding campus-specific interests
Drop an `interests-custom.json` next to where you run the app — it is merged into the
built-in vocabulary at startup, no recompile needed:
```json
{ "tags": [
  { "id": "rangoli", "label": "Rangoli", "category": "ARTS", "aliases": ["kolam"] }
] }
```
Valid categories: `SPORTS MUSIC GAMING ACADEMICS TECH ARTS FOOD FILM_TV FITNESS
VOLUNTEERING OUTDOORS OTHER`. An id or alias that collides with an existing tag is
rejected loudly rather than silently hijacking it.

### Or in IntelliJ IDEA:
1. Open the project
2. Right-click on `Main.java` → Run 'Main.main()'

### VS Code Note:
If you see red error squiggles in VS Code (like "package com.formdev.flatlaf does not exist"), don't worry! This is just the language server needing time to recognize the external JAR libraries. The code compiles and runs perfectly. Just give it a moment to load, and simply hit Run - it will work fine.
