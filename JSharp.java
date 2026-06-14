import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class JSharp {
    public static final String VERSION = "1.0";

    public static void main(String[] args) {
        if (args.length >= 1 && (args[0].equals("-version") || args[0].equals("--version"))) {
            printVersion();
            return;
        }

        if (args.length < 1) {
            repl();
            return;
        }
        String filePath = args[0];

        if (!filePath.toLowerCase().endsWith(".jsharp")) {
            System.err.println("Error: J# script file must end with .jsharp");
            System.exit(1);
        }

        String targetProject = args.length >= 2 ? args[1] : null;

        try {
            String script = Files.readString(Path.of(filePath));
            Parser parser = new Parser();
            Map<String, List<Command>> projects = parser.parseProjects(script);

            if (projects.isEmpty()) {
                System.err.println("Error: No valid project definitions found in script.");
                return;
            }

            if (targetProject != null) {
                List<Command> commands = projects.get(targetProject);
                if (commands == null) {
                    System.err.println("Error: project not found: " + targetProject);
                    System.out.println("Available projects: " + String.join(", ", projects.keySet()));
                    return;
                }
                executeProject(targetProject, commands);
            } else {
                ExecutorService executor = Executors.newFixedThreadPool(projects.size());
                for (Map.Entry<String, List<Command>> entry : projects.entrySet()) {
                    String name = entry.getKey();
                    List<Command> commands = entry.getValue();
                    executor.submit(() -> executeProject(name, commands));
                }
                executor.shutdown();
                executor.awaitTermination(1, TimeUnit.HOURS);
            }
        } catch (IOException e) {
            System.err.println("Error: cannot read file " + filePath);
            e.printStackTrace();
        } catch (InterruptedException e) {
            System.err.println("Error: execution interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private static void printVersion() {
        String buildDate = "2026-06-14";
        String buildNumber = "1";
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");

        System.out.println("J# version \"" + VERSION + "\" " + buildDate);
        System.out.println("J# Runtime Environment (build " + VERSION + "+" + buildNumber + ")");
        System.out.println("J# 64-Bit Server VM (build " + VERSION + "+" + buildNumber + ", mixed mode)");
        System.out.println("Platform: " + osName + " " + osArch);
        System.out.println("(powered by Java " + javaVersion + ")");
    }

    // ==================== REPL ====================
    private static void repl() {
        System.out.println("J# REPL (type 'exit' to quit, supports multiline JSON)");
        Scanner scanner = new Scanner(System.in);
        Parser parser = new Parser();
        Runtime runtime = new Runtime();

        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Goodbye.");
                break;
            }

            StringBuilder jsonBuf = new StringBuilder(line);
            while (countBraces(jsonBuf.toString()) != 0) {
                System.out.print("... ");
                String extra = scanner.nextLine().trim();
                if (extra.isEmpty()) continue;
                jsonBuf.append(" ").append(extra);
            }

            String jsonStr = jsonBuf.toString();
            try {
                Command cmd = parser.parseJsonCommand(jsonStr);
                List<Command> list = new ArrayList<>();
                list.add(new Command("project", Map.of("name", "REPL", "file", "memory")));
                list.add(cmd);
                String result = runtime.eval(list);
                System.out.print(result);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private static int countBraces(String s) {
        int count = 0;
        for (char c : s.toCharArray()) {
            if (c == '{') count++;
            else if (c == '}') count--;
        }
        return count;
    }

    private static void executeProject(String name, List<Command> commands) {
        try {
            Runtime runtime = new Runtime();
            String output = runtime.execute(commands);
            String block = "===== Execute project: " + name + " =====\n" +
                           output +
                           "===== End of project: " + name + " =====\n";
            System.out.print(block);
        } catch (Exception e) {
            System.err.println("Project " + name + " execution error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}