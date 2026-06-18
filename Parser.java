import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {
    private Map<String, FuncDef> functions = new HashMap<>();
    private Map<String, List<Command>> rawProjects = new LinkedHashMap<>();
    private int condCounter = 0;
    private Map<String, ConditionExpansion> whileCondMap = new HashMap<>();

    private static class ConditionExpansion {
        List<Command> setupCommands = new ArrayList<>();
        String resultVar;
    }

    public Map<String, List<Command>> parseProjects(String script) {
        Map<String, List<Command>> projects = new LinkedHashMap<>();
        functions.clear();
        rawProjects.clear();
        condCounter = 0;
        whileCondMap.clear();

        if (script.startsWith("\uFEFF")) script = script.substring(1);
        script = script.replace("\r\n", "\n").replace("\r", "\n");

        String cleanedScript = precollectAndRemoveFunctions(script);

        Pattern projectBlock = Pattern.compile(
            "\\{\"project\"\\s*:\\s*\\{([^}]*)\\}\\s*\\}(.*?)(?=\\{\"project\"\\s*:|$)",
            Pattern.DOTALL);
        Matcher matcher = projectBlock.matcher(cleanedScript);

        while (matcher.find()) {
            String paramsStr = matcher.group(1);
            String body = matcher.group(2);
            Map<String, String> projectParams = parseJsonParams("{" + paramsStr + "}");
            String name = projectParams.get("name");
            if (name == null) {
                System.err.println("Warning: project without name found, skipped.");
                continue;
            }
            List<Command> rawCommands = parseCommands(body);
            rawProjects.put(name, rawCommands);
        }

        for (Map.Entry<String, List<Command>> entry : rawProjects.entrySet()) {
            String name = entry.getKey();
            List<Command> preprocessed = preprocessExpressions(entry.getValue());
            List<Command> expanded = desugar(preprocessed);
            Command projCmd = new Command("project",
                Map.of("name", name, "file", "./" + name + ".txt"));
            expanded.add(0, projCmd);
            projects.put(name, expanded);
        }
        return projects;
    }

    // ========== Public method for REPL ==========
    public Command parseJsonCommand(String json) {
        return parseJsonCommandInternal(json);
    }

    private Command parseJsonCommandInternal(String json) {
        String content = json.trim();
        if (!content.startsWith("{") || !content.endsWith("}"))
            throw new IllegalArgumentException("Not a valid JSON object");

        // --- 强制对象内部必须换行 ---
        String inner = content.substring(1, content.length() - 1).trim();
        if (!inner.contains("\n")) {
            throw new IllegalArgumentException("JSON object must span multiple lines (each key-value pair on a new line)");
        }

        // Extract command name and parameters
        Pattern keyPattern = Pattern.compile("\"([^\"]*)\"\\s*:\\s*(.*)");
        Matcher m = keyPattern.matcher(content);
        if (!m.matches()) throw new IllegalArgumentException("Cannot parse command name");
        String commandName = m.group(1);
        String paramsJson = m.group(2).trim();
        Map<String, String> params = parseJsonParams(paramsJson);
        return new Command(commandName, params);
    }

    // ========== Strict JSON parsing (objects must be separated by newline) ==========
    private List<Command> parseCommands(String script) {
        List<Command> commands = new ArrayList<>();
        int len = script.length();
        int i = 0;
        boolean first = true;
        while (i < len) {
            // Skip spaces/tabs
            while (i < len && (script.charAt(i) == ' ' || script.charAt(i) == '\t')) i++;
            if (i < len && script.charAt(i) == '\n') {
                i++;
                while (i < len && Character.isWhitespace(script.charAt(i))) i++;
            } else if (!first && i < len && script.charAt(i) == '{') {
                int line = 1;
                for (int k = 0; k < i; k++) if (script.charAt(k) == '\n') line++;
                throw new IllegalArgumentException("Error at line " + line + ": objects must be separated by newline");
            }
            first = false;

            if (i >= len) break;

            // Must start with {
            if (script.charAt(i) != '{') {
                int line = 1;
                for (int k = 0; k < i; k++) if (script.charAt(k) == '\n') line++;
                throw new IllegalArgumentException("Error at line " + line + ": non-JSON content found, expected '{'");
            }

            int start = i;
            int braceCount = 0;
            while (i < len) {
                char c = script.charAt(i);
                if (c == '"') {
                    i++;
                    while (i < len) {
                        if (script.charAt(i) == '\\') i += 2;
                        else if (script.charAt(i) == '"') { i++; break; }
                        else i++;
                    }
                } else {
                    if (c == '{') braceCount++;
                    else if (c == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            i++;
                            break;
                        }
                    }
                    i++;
                }
            }
            if (braceCount != 0) {
                int line = 1;
                for (int k = 0; k < start; k++) if (script.charAt(k) == '\n') line++;
                throw new IllegalArgumentException("Error at line " + line + ": JSON brace mismatch");
            }

            String jsonStr = script.substring(start, i).trim();
            try {
                Command cmd = parseJsonCommandInternal(jsonStr);
                int line = 1;
                for (int k = 0; k < start; k++) if (script.charAt(k) == '\n') line++;
                cmd.setLine(line);
                commands.add(cmd);
            } catch (Exception e) {
                int line = 1;
                for (int k = 0; k < start; k++) if (script.charAt(k) == '\n') line++;
                throw new IllegalArgumentException("Error at line " + line + ": JSON parsing failed - " + e.getMessage());
            }
        }
        return commands;
    }

    // Parse parameter JSON object
    private Map<String, String> parseJsonParams(String paramsJson) {
        Map<String, String> params = new LinkedHashMap<>();
        if (paramsJson == null || !paramsJson.startsWith("{") || !paramsJson.endsWith("}"))
            return params;
        String content = paramsJson.substring(1, paramsJson.length() - 1).trim();
        if (content.isEmpty()) return params;
        Pattern pairPattern = Pattern.compile(
            "\"([^\"]*)\"\\s*:\\s*(\"[^\"]*\"|-?\\d+(\\.\\d+)?(?:[eE][+-]?\\d+)?|true|false|null)");
        Matcher m = pairPattern.matcher(content);
        while (m.find()) {
            String key = m.group(1);
            String valueStr = m.group(2);
            String value;
            if (valueStr.startsWith("\"")) {
                value = valueStr.substring(1, valueStr.length() - 1)
                              .replace("\\\"", "\"")
                              .replace("\\n", "\n")
                              .replace("\\t", "\t")
                              .replace("\\\\", "\\");
            } else if (valueStr.equals("true") || valueStr.equals("false")) {
                value = valueStr;
            } else if (valueStr.equals("null")) {
                value = "";
            } else {
                value = valueStr;
            }
            params.put(key, value);
        }
        return params;
    }

    // Pre-collect function definitions and remove them from script
    private String precollectAndRemoveFunctions(String script) {
        Pattern funcPattern = Pattern.compile(
            "\\{\"function\"\\s*:\\s*\\{([^}]*)\\}\\s*\\}(.*?)\\{\"endfunction\"\\s*:\\s*\\{[^}]*\\}\\s*\\}",
            Pattern.DOTALL);
        Matcher m = funcPattern.matcher(script);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String paramsStr = m.group(1);
            String body = m.group(2);
            Map<String, String> funcParams = parseJsonParams("{" + paramsStr + "}");
            String funcName = funcParams.get("name");
            String paramList = funcParams.get("params");
            if (funcName != null) {
                List<String> paramNames = new ArrayList<>();
                if (paramList != null && !paramList.isEmpty()) {
                    for (String p : paramList.split(",")) paramNames.add(p.trim());
                }
                List<Command> funcBody = parseCommands(body);
                funcBody = desugar(funcBody);
                functions.put(funcName, new FuncDef(paramNames, funcBody));
            }
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Expression preprocessing
    private List<Command> preprocessExpressions(List<Command> commands) {
        List<Command> result = new ArrayList<>();
        for (Command cmd : commands) {
            String name = cmd.getName();
            if (name.equals("if") || name.equals("while") || name.equals("elseif")) {
                String cond = cmd.getParams().get("cond");
                if (cond != null && isExpression(cond)) {
                    ConditionExpansion expansion = expandComplexCondition(cond);
                    if (expansion != null) {
                        result.addAll(expansion.setupCommands);
                        Map<String, String> newParams = new HashMap<>(cmd.getParams());
                        newParams.put("cond", expansion.resultVar);
                        result.add(new Command(cmd.getName(), newParams, cmd.getLine()));
                        if (name.equals("while")) {
                            whileCondMap.put(expansion.resultVar, expansion);
                        }
                        continue;
                    }
                }
            }
            result.add(cmd);
        }
        return result;
    }

    private boolean isExpression(String s) {
        return s.matches(".*([<>]=?|==|!=).*");
    }

    private ConditionExpansion expandComplexCondition(String expr) {
        String[] orParts = expr.split("\\|\\|");
        if (orParts.length > 1) {
            List<Command> cmds = new ArrayList<>();
            String finalVar = null;
            String endLabel = "__or_end_" + (condCounter++);
            for (int i = 0; i < orParts.length; i++) {
                String part = orParts[i].trim();
                ConditionExpansion sub = expandAndCondition(part);
                if (sub == null) return null;
                String tempVar = sub.resultVar;
                cmds.addAll(sub.setupCommands);
                cmds.add(new Command("jump_if_true", Map.of("cond", tempVar, "to", endLabel)));
                finalVar = tempVar;
            }
            cmds.add(new Command("label", Map.of("id", endLabel)));
            ConditionExpansion ce = new ConditionExpansion();
            ce.setupCommands = cmds;
            ce.resultVar = finalVar;
            return ce;
        }
        return expandAndCondition(expr);
    }

    private ConditionExpansion expandAndCondition(String expr) {
        String[] andParts = expr.split("&&");
        List<Command> cmds = new ArrayList<>();
        String finalVar = null;
        String endLabel = "__and_end_" + (condCounter++);
        for (int i = 0; i < andParts.length; i++) {
            String part = andParts[i].trim();
            ConditionExpansion sub = expandSimpleCondition(part);
            if (sub == null) return null;
            String tempVar = sub.resultVar;
            cmds.addAll(sub.setupCommands);
            if (i < andParts.length - 1) {
                cmds.add(new Command("jump_if_false", Map.of("cond", tempVar, "to", endLabel)));
            }
            finalVar = tempVar;
        }
        cmds.add(new Command("label", Map.of("id", endLabel)));
        ConditionExpansion ce = new ConditionExpansion();
        ce.setupCommands = cmds;
        ce.resultVar = finalVar;
        return ce;
    }

    private ConditionExpansion expandSimpleCondition(String expr) {
        expr = expr.trim();
        Comparison comp = parseComparison(expr);
        if (comp != null) {
            String tempVar = "__cond_" + (condCounter++);
            Command cmpCmd = new Command(comp.op, Map.of(
                "a", comp.left, "b", comp.right, "result", tempVar));
            ConditionExpansion ce = new ConditionExpansion();
            ce.setupCommands.add(cmpCmd);
            ce.resultVar = tempVar;
            return ce;
        }
        if (expr.startsWith("$")) {
            String var = expr.substring(1);
            ConditionExpansion ce = new ConditionExpansion();
            ce.resultVar = var;
            return ce;
        }
        return null;
    }

    private Comparison parseComparison(String expr) {
        Pattern pattern = Pattern.compile(
            "^\\s*(\\$?[a-zA-Z_]\\w*|\\d+(\\.\\d+)?)\\s*(<=|>=|==|!=|<|>)\\s*(\\$?[a-zA-Z_]\\w*|\\d+(\\.\\d+)?)\\s*$");
        Matcher m = pattern.matcher(expr);
        if (m.find()) {
            String left = m.group(1);
            String op = m.group(3);
            String right = m.group(4);
            String cmdOp = switch (op) {
                case "<" -> "lt";
                case ">" -> "gt";
                case "<=" -> "lte";
                case ">=" -> "gte";
                case "==" -> "eq";
                case "!=" -> "neq";
                default -> null;
            };
            if (cmdOp != null) {
                return new Comparison(cmdOp, left, right);
            }
        }
        return null;
    }

    private static class Comparison {
        String op, left, right;
        Comparison(String op, String left, String right) {
            this.op = op; this.left = left; this.right = right;
        }
    }

    // Syntactic sugar expansion
    private List<Command> desugar(List<Command> commands) {
        List<Command> result = new ArrayList<>();
        int i = 0;
        while (i < commands.size()) {
            Command cmd = commands.get(i);
            String name = cmd.getName();
            switch (name) {
                case "call": {
                    String funcName = cmd.getParams().get("func");
                    String projectName = cmd.getParams().get("project");
                    String argsStr = cmd.getParams().get("args");
                    String resultVar = cmd.getParams().get("result");
                    if (projectName != null) {
                        List<Command> target = rawProjects.get(projectName);
                        if (target == null) {
                            System.err.println("Warning: project not found " + projectName);
                            i++; break;
                        }
                        List<Command> projCommands = new ArrayList<>(target);
                        projCommands = desugar(projCommands);
                        result.addAll(projCommands);
                        i++; break;
                    }
                    FuncDef func = functions.get(funcName);
                    if (func == null) {
                        System.err.println("Warning: function not found " + funcName);
                        i++; break;
                    }
                    String[] argValues = (argsStr != null) ? argsStr.split(",") : new String[0];
                    List<Command> inlined = new ArrayList<>();
                    for (Command oc : func.body) {
                        Map<String, String> newParams = new HashMap<>(oc.getParams());
                        for (String key : newParams.keySet()) {
                            String val = newParams.get(key);
                            for (int p = 0; p < func.paramNames.size(); p++) {
                                String pname = func.paramNames.get(p);
                                String replaceWith = (p < argValues.length) ? argValues[p].trim() : "";
                                val = val.replaceAll("\\$" + Pattern.quote(pname) + "(?![a-zA-Z0-9_])",
                                        Matcher.quoteReplacement(replaceWith));
                            }
                            if (resultVar != null) {
                                val = val.replaceAll("\\$return", Matcher.quoteReplacement(resultVar));
                            }
                            newParams.put(key, val);
                        }
                        inlined.add(new Command(oc.getName(), newParams, oc.getLine()));
                    }
                    result.addAll(inlined);
                    i++;
                    break;
                }
                case "if": {
                    String id = cmd.getParams().get("id");
                    if (id == null) { i++; break; }
                    int end = findBlockEnd(commands, i, "if", id);
                    if (end == -1) { i++; break; }
                    List<Command> block = new ArrayList<>(commands.subList(i, end + 1));
                    result.addAll(expandIfBlock(block, id));
                    i = end + 1;
                    break;
                }
                case "while": {
                    String id = cmd.getParams().get("id");
                    if (id == null) { i++; break; }
                    int end = findBlockEnd(commands, i, "while", id);
                    if (end == -1) { i++; break; }
                    List<Command> block = new ArrayList<>(commands.subList(i, end + 1));
                    result.addAll(expandWhileBlock(block, id));
                    i = end + 1;
                    break;
                }
                default:
                    result.add(cmd);
                    i++;
            }
        }
        return result;
    }

    private int findBlockEnd(List<Command> commands, int start, String type, String id) {
        String endTag = type.equals("if") ? "endif" : "endwhile";
        for (int j = start + 1; j < commands.size(); j++) {
            Command c = commands.get(j);
            if (c.getName().equals(endTag) && id.equals(c.getParams().get("id"))) {
                return j;
            }
        }
        return -1;
    }

    private List<Command> expandIfBlock(List<Command> block, String id) {
        List<List<Command>> bodies = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        List<Command> current = new ArrayList<>();
        boolean inBranch = false;
        for (Command c : block) {
            switch (c.getName()) {
                case "if":
                    conds.add(c.getParams().get("cond"));
                    inBranch = true;
                    current = new ArrayList<>();
                    break;
                case "elseif":
                    bodies.add(current);
                    conds.add(c.getParams().get("cond"));
                    current = new ArrayList<>();
                    break;
                case "else":
                    bodies.add(current);
                    conds.add(null);
                    current = new ArrayList<>();
                    break;
                case "endif":
                    bodies.add(current);
                    break;
                default:
                    if (inBranch) current.add(c);
            }
        }
        List<Command> result = new ArrayList<>();
        String endLabel = id + "_end";
        for (int idx = 0; idx < bodies.size(); idx++) {
            String cond = conds.get(idx);
            List<Command> body = desugar(bodies.get(idx));
            if (cond != null) {
                String nextLabel = (idx < bodies.size() - 1) ? id + "_else" + (idx + 1) : endLabel;
                result.add(new Command("jump_if_false", Map.of("cond", cond, "to", nextLabel)));
                result.addAll(body);
                if (idx < bodies.size() - 1) result.add(new Command("jump", Map.of("to", endLabel)));
                if (idx < bodies.size() - 1) result.add(new Command("label", Map.of("id", nextLabel)));
            } else {
                result.addAll(body);
            }
        }
        result.add(new Command("label", Map.of("id", endLabel)));
        return result;
    }

    private List<Command> expandWhileBlock(List<Command> block, String id) {
        String cond = null;
        List<Command> body = new ArrayList<>();
        for (Command c : block) {
            if (c.getName().equals("while")) cond = c.getParams().get("cond");
            else if (!c.getName().equals("endwhile")) body.add(c);
        }
        body = desugar(body);

        ConditionExpansion expansion = whileCondMap.get(cond);
        if (expansion != null) {
            List<Command> updateCmds = new ArrayList<>();
            for (Command c : expansion.setupCommands) {
                updateCmds.add(new Command(c.getName(), new HashMap<>(c.getParams()), c.getLine()));
            }
            body = new ArrayList<>(body);
            body.addAll(updateCmds);
        }

        String startLabel = id + "_start";
        String breakLabel = id + "_end";
        List<Command> result = new ArrayList<>();
        result.add(new Command("label", Map.of("id", startLabel)));
        result.add(new Command("jump_if_false", Map.of("cond", cond, "to", breakLabel)));
        result.addAll(body);
        result.add(new Command("jump", Map.of("to", startLabel)));
        result.add(new Command("label", Map.of("id", breakLabel)));
        return result;
    }
}