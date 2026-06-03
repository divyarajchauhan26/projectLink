# projectLink
This project models a college social network using graph data structures. Users are represented as nodes, friendships as edges, and BFS/DFS are used for shortest and exhaustive path discovery.

## Setup & Running

### Compile:
```bash
cd PorjectLink
javac -d out -cp "lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar" src/CampusConnect/domain/UserNode.java src/CampusConnect/service/NetworkService.java src/CampusConnect/ui/NetworkCanvas.java src/CampusConnect/ui/MainFrame.java src/CampusConnect/main/Main.java
```

### Run:
```bash
java -cp "out;lib/flatlaf-3.5.jar;lib/flatlaf-intellij-themes-3.5.jar" CampusConnect.main.Main
```

### Or in IntelliJ IDEA:
1. Open the project
2. Right-click on `Main.java` → Run 'Main.main()'
