import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Base64;

public class Runtime {
    private Map<String, String> env = new HashMap<>();
    private Map<String, List<String>> lists = new HashMap<>();
    private StringBuilder output = new StringBuilder();
    private Map<String, Integer> labels = new HashMap<>();
    private static final int MAX_STEPS = 10_000;
    private int step = 0;
    private Command currentCommand;

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String execute(List<Command> commands) {
        return executeInternal(commands, true);
    }

    public String eval(List<Command> commands) {
        return executeInternal(commands, false);
    }

    private String executeInternal(List<Command> commands, boolean clearEnv) {
        if (clearEnv) {
            env.clear();
            lists.clear();
        }
        labels.clear();
        output.setLength(0);
        step = 0;

        boolean hasProject = false;
        for (Command cmd : commands) {
            if (cmd.getName().equals("project")) {
                hasProject = true;
                break;
            }
        }
        if (!hasProject) {
            output.append("Error: Missing required 'project' block!\n");
            return output.toString();
        }

        for (int i = 0; i < commands.size(); i++) {
            Command cmd = commands.get(i);
            if (cmd.getName().equals("label")) {
                String id = cmd.getParams().get("id");
                if (id != null) labels.put(id, i);
            }
        }

        int ip = 0;
        step = 0;
        while (ip >= 0 && ip < commands.size()) {
            if (++step > MAX_STEPS) {
                output.append("Error: Maximum execution steps (" + MAX_STEPS + ") exceeded, possible infinite loop, terminated.\n");
                break;
            }
            currentCommand = commands.get(ip);
            try {
                ip = executeInstruction(currentCommand, ip, commands);
            } catch (Exception e) {
                output.append("Runtime error: ").append(e.getMessage()).append("\n");
                output.append("  at instruction ").append(currentCommand).append("\n");
                ip++;
            }
        }
        return output.toString();
    }

    private int executeInstruction(Command cmd, int ip, List<Command> commands) {
        switch (cmd.getName()) {
            case "project": handleProject(cmd.getParams()); return ip + 1;
            case "print":   handlePrint(cmd.getParams());   return ip + 1;
            case "set":     handleSet(cmd.getParams());     return ip + 1;
            case "add": handleArith(cmd.getParams(), (a,b)->a+b); return ip+1;
            case "sub": handleArith(cmd.getParams(), (a,b)->a-b); return ip+1;
            case "mul": handleArith(cmd.getParams(), (a,b)->a*b); return ip+1;
            case "div": handleArith(cmd.getParams(), (a,b)->a/b); return ip+1;
            case "eq": handleEquality(cmd.getParams(), true); return ip+1;
            case "neq": handleEquality(cmd.getParams(), false); return ip+1;
            case "gt":  handleCompare(cmd.getParams(), (a,b)->Double.parseDouble(a) > Double.parseDouble(b)); return ip+1;
            case "lt":  handleCompare(cmd.getParams(), (a,b)->Double.parseDouble(a) < Double.parseDouble(b)); return ip+1;
            case "gte": handleCompare(cmd.getParams(), (a,b)->Double.parseDouble(a) >= Double.parseDouble(b)); return ip+1;
            case "lte": handleCompare(cmd.getParams(), (a,b)->Double.parseDouble(a) <= Double.parseDouble(b)); return ip+1;
            case "label": return ip + 1;
            case "jump": return getLabelIndex(cmd.getParams().get("to"));
            case "jump_if_true":
                if ("true".equals(resolveCond(cmd.getParams().get("cond"))))
                    return getLabelIndex(cmd.getParams().get("to"));
                else return ip + 1;
            case "jump_if_false":
                if ("false".equals(resolveCond(cmd.getParams().get("cond"))))
                    return getLabelIndex(cmd.getParams().get("to"));
                else return ip + 1;
            case "writefile": handleWriteFile(cmd.getParams()); return ip + 1;
            case "readfile":  handleReadFile(cmd.getParams());  return ip + 1;
            case "list_create": handleListCreate(cmd.getParams()); return ip + 1;
            case "list_add":    handleListAdd(cmd.getParams());    return ip + 1;
            case "list_get":    handleListGet(cmd.getParams());    return ip + 1;
            case "list_set":    handleListSet(cmd.getParams());    return ip + 1;
            case "list_size":   handleListSize(cmd.getParams());   return ip + 1;
            case "list_remove": handleListRemove(cmd.getParams()); return ip + 1;
            case "str_len":     handleStrLen(cmd.getParams());     return ip + 1;
            case "str_sub":     handleStrSub(cmd.getParams());     return ip + 1;
            case "str_replace": handleStrReplace(cmd.getParams()); return ip + 1;
            case "str_trim":    handleStrTrim(cmd.getParams());    return ip + 1;
            case "str_upper":   handleStrUpper(cmd.getParams());   return ip + 1;
            case "str_lower":   handleStrLower(cmd.getParams());   return ip + 1;
            case "str_concat":  handleStrConcat(cmd.getParams());  return ip + 1;
            case "str_split":   handleStrSplit(cmd.getParams());   return ip + 1;
            case "str_join":    handleStrJoin(cmd.getParams());    return ip + 1;
            case "http_get":    handleHttpGet(cmd.getParams());    return ip + 1;
            case "http_post":   handleHttpPost(cmd.getParams());   return ip + 1;
            case "time_now":    handleTimeNow(cmd.getParams());    return ip + 1;
            case "math_sqrt":   handleMathUnary(cmd.getParams(), Math::sqrt);   return ip + 1;
            case "math_abs":    handleMathUnary(cmd.getParams(), Math::abs);    return ip + 1;
            case "math_ceil":   handleMathUnary(cmd.getParams(), Math::ceil);   return ip + 1;
            case "math_floor":  handleMathUnary(cmd.getParams(), Math::floor);  return ip + 1;
            case "math_round":  handleMathUnary(cmd.getParams(), Math::round);  return ip + 1;
            case "math_sin":    handleMathUnary(cmd.getParams(), Math::sin);    return ip + 1;
            case "math_cos":    handleMathUnary(cmd.getParams(), Math::cos);    return ip + 1;
            case "math_log":    handleMathUnary(cmd.getParams(), Math::log);    return ip + 1;
            case "math_log10":  handleMathUnary(cmd.getParams(), Math::log10);  return ip + 1;
            case "math_pow":    handleMathPow(cmd.getParams());                 return ip + 1;
            case "math_random": handleMathRandom(cmd.getParams());              return ip + 1;
            case "conv_int":   handleConvInt(cmd.getParams());   return ip + 1;
            case "conv_float": handleConvFloat(cmd.getParams()); return ip + 1;
            case "conv_str":   handleConvStr(cmd.getParams());   return ip + 1;
            case "base64_encode": handleBase64Encode(cmd.getParams()); return ip + 1;
            case "base64_decode": handleBase64Decode(cmd.getParams()); return ip + 1;
            case "regex_match":   handleRegexMatch(cmd.getParams());   return ip + 1;
            case "regex_find":    handleRegexFind(cmd.getParams());    return ip + 1;
            case "regex_replace": handleRegexReplace(cmd.getParams()); return ip + 1;
            case "sleep":         handleSleep(cmd.getParams());        return ip + 1;
            default:
                output.append("Warning: Unknown command ").append(cmd.getName()).append(" (line ").append(cmd.getLine()).append(")\n");
                return ip + 1;
        }
    }

    // ========== Command handlers (all English now) ==========

    private void handleSleep(Map<String, String> params) {
        String millisStr = params.get("millis");
        if (millisStr == null) millisStr = params.get("ms");
        if (millisStr != null) {
            try {
                long millis = Long.parseLong(millisStr);
                output.append("[SLEEP] ").append(millis).append("ms\n");
                Thread.sleep(millis);
            } catch (NumberFormatException e) {
                output.append("Error: sleep argument must be a valid integer\n");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                output.append("Error: sleep interrupted\n");
            }
        }
    }

    private void handleConvInt(Map<String, String> params) {
        String val = getValue(params, "value");
        String resultVar = params.get("result");
        if (val != null && resultVar != null) {
            try {
                double d = Double.parseDouble(val);
                env.put(resultVar, String.valueOf(Math.round(d)));
                output.append("[CONV] int -> ").append(Math.round(d)).append("\n");
            } catch (NumberFormatException e) {
                output.append("Error: conv_int value must be numeric\n");
            }
        }
    }

    private void handleConvFloat(Map<String, String> params) {
        String val = getValue(params, "value");
        String resultVar = params.get("result");
        if (val != null && resultVar != null) {
            try {
                double d = Double.parseDouble(val);
                env.put(resultVar, String.valueOf(d));
                output.append("[CONV] float -> ").append(d).append("\n");
            } catch (NumberFormatException e) {
                output.append("Error: conv_float value must be numeric\n");
            }
        }
    }

    private void handleConvStr(Map<String, String> params) {
        String val = getValue(params, "value");
        String resultVar = params.get("result");
        if (val != null && resultVar != null) {
            env.put(resultVar, val);
            output.append("[CONV] str -> ").append(val).append("\n");
        }
    }

    private void handleBase64Encode(Map<String, String> params) {
        String val = getValue(params, "value");
        String resultVar = params.get("result");
        if (val != null && resultVar != null) {
            String encoded = Base64.getEncoder().encodeToString(val.getBytes());
            env.put(resultVar, encoded);
            output.append("[BASE64] encode -> ").append(encoded).append("\n");
        }
    }

    private void handleBase64Decode(Map<String, String> params) {
        String val = getValue(params, "value");
        String resultVar = params.get("result");
        if (val != null && resultVar != null) {
            try {
                byte[] decoded = Base64.getDecoder().decode(val);
                String str = new String(decoded);
                env.put(resultVar, str);
                output.append("[BASE64] decode -> ").append(str).append("\n");
            } catch (IllegalArgumentException e) {
                output.append("Error: base64_decode invalid Base64 string\n");
            }
        }
    }

    private void handleRegexMatch(Map<String, String> params) {
        String str = getValue(params, "str");
        String pattern = params.get("pattern");
        String resultVar = params.get("result");
        if (str != null && pattern != null && resultVar != null) {
            boolean matches = Pattern.matches(pattern, str);
            env.put(resultVar, matches ? "true" : "false");
            output.append("[REGEX] match -> ").append(matches).append("\n");
        }
    }

    private void handleRegexFind(Map<String, String> params) {
        String str = getValue(params, "str");
        String patternStr = params.get("pattern");
        String resultVar = params.get("result");
        if (str != null && patternStr != null && resultVar != null) {
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(str);
            if (matcher.find()) {
                env.put(resultVar, matcher.group());
                output.append("[REGEX] find -> ").append(matcher.group()).append("\n");
            } else {
                env.put(resultVar, "");
                output.append("[REGEX] find -> no match\n");
            }
        }
    }

    private void handleRegexReplace(Map<String, String> params) {
        String str = getValue(params, "str");
        String patternStr = params.get("pattern");
        String replacement = params.get("replacement");
        String resultVar = params.get("result");
        if (str != null && patternStr != null && replacement != null && resultVar != null) {
            String result = str.replaceAll(patternStr, replacement);
            env.put(resultVar, result);
            output.append("[REGEX] replace -> ").append(result).append("\n");
        }
    }

    private void handleMathUnary(Map<String, String> params, java.util.function.DoubleUnaryOperator op) {
        String value = getValue(params, "value");
        String resultVar = params.get("result");
        if (value != null && resultVar != null) {
            try {
                double x = Double.parseDouble(value);
                double res = op.applyAsDouble(x);
                env.put(resultVar, String.valueOf(res));
                output.append("[MATH] ").append(res).append("\n");
            } catch (NumberFormatException e) {
                output.append("Error: math function argument must be numeric\n");
            }
        }
    }

    private void handleMathPow(Map<String, String> params) {
        String base = getValue(params, "base");
        String exp = getValue(params, "exponent");
        String resultVar = params.get("result");
        if (base != null && exp != null && resultVar != null) {
            try {
                double b = Double.parseDouble(base);
                double e = Double.parseDouble(exp);
                double res = Math.pow(b, e);
                env.put(resultVar, String.valueOf(res));
                output.append("[MATH] pow = ").append(res).append("\n");
            } catch (NumberFormatException e) {
                output.append("Error: math_pow arguments must be numeric\n");
            }
        }
    }

    private void handleMathRandom(Map<String, String> params) {
        String resultVar = params.get("result");
        if (resultVar != null) {
            double r = Math.random();
            env.put(resultVar, String.valueOf(r));
            output.append("[MATH] random = ").append(r).append("\n");
        }
    }

    private void handleHttpGet(Map<String, String> params) {
        String url = getValue(params, "url");
        String resultVar = params.get("result");
        if (url == null || resultVar == null) {
            output.append("Error: http_get requires 'url' and 'result' parameters\n");
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            env.put(resultVar, response.body());
            output.append("[HTTP] GET ").append(url).append(" -> ").append(response.statusCode()).append("\n");
        } catch (Exception e) {
            output.append("Error: HTTP GET failed - ").append(e.getMessage()).append("\n");
        }
    }

    private void handleHttpPost(Map<String, String> params) {
        String url = getValue(params, "url");
        String body = getValue(params, "body");
        String resultVar = params.get("result");
        if (url == null || resultVar == null) {
            output.append("Error: http_post requires 'url' and 'result' parameters\n");
            return;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "text/plain; charset=UTF-8");
            if (body != null && !body.isEmpty()) {
                builder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
            HttpRequest request = builder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            env.put(resultVar, response.body());
            output.append("[HTTP] POST ").append(url).append(" -> ").append(response.statusCode()).append("\n");
        } catch (Exception e) {
            output.append("Error: HTTP POST failed - ").append(e.getMessage()).append("\n");
        }
    }

    private void handleTimeNow(Map<String, String> params) {
        String resultVar = params.get("result");
        if (resultVar != null) {
            String now = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            env.put(resultVar, now);
            output.append("[TIME] now = ").append(now).append("\n");
        }
    }

    private void handleStrLen(Map<String, String> params) {
        String str = getValue(params, "str");
        String resultVar = params.get("result");
        if (str != null && resultVar != null) {
            env.put(resultVar, String.valueOf(str.length()));
            output.append("[STR] len = ").append(str.length()).append("\n");
        }
    }

    private void handleStrSub(Map<String, String> params) {
        String str = getValue(params, "str");
        String startStr = getValue(params, "start");
        String lenStr = params.get("length");
        String resultVar = params.get("result");
        if (str != null && startStr != null && resultVar != null) {
            try {
                int start = Integer.parseInt(startStr);
                if (lenStr != null) {
                    int length = Integer.parseInt(lenStr);
                    String sub = str.substring(Math.min(start, str.length()), Math.min(start + length, str.length()));
                    env.put(resultVar, sub);
                    output.append("[STR] sub = ").append(sub).append("\n");
                } else {
                    String sub = str.substring(Math.min(start, str.length()));
                    env.put(resultVar, sub);
                    output.append("[STR] sub = ").append(sub).append("\n");
                }
            } catch (NumberFormatException e) {
                output.append("Error: str_sub indices must be integers\n");
            }
        }
    }

    private void handleStrReplace(Map<String, String> params) {
        String str = getValue(params, "str");
        String oldStr = params.get("old");
        String newStr = params.get("new");
        String resultVar = params.get("result");
        if (str != null && oldStr != null && newStr != null && resultVar != null) {
            String res = str.replace(oldStr, newStr);
            env.put(resultVar, res);
            output.append("[STR] replace -> ").append(res).append("\n");
        }
    }

    private void handleStrTrim(Map<String, String> params) {
        String str = getValue(params, "str");
        String resultVar = params.get("result");
        if (str != null && resultVar != null) {
            String res = str.trim();
            env.put(resultVar, res);
            output.append("[STR] trim -> ").append(res).append("\n");
        }
    }

    private void handleStrUpper(Map<String, String> params) {
        String str = getValue(params, "str");
        String resultVar = params.get("result");
        if (str != null && resultVar != null) {
            env.put(resultVar, str.toUpperCase());
            output.append("[STR] upper\n");
        }
    }

    private void handleStrLower(Map<String, String> params) {
        String str = getValue(params, "str");
        String resultVar = params.get("result");
        if (str != null && resultVar != null) {
            env.put(resultVar, str.toLowerCase());
            output.append("[STR] lower\n");
        }
    }

    private void handleStrConcat(Map<String, String> params) {
        String a = getValue(params, "a");
        String b = getValue(params, "b");
        String resultVar = params.get("result");
        if (a != null && b != null && resultVar != null) {
            env.put(resultVar, a + b);
            output.append("[STR] concat -> ").append(a + b).append("\n");
        }
    }

    private void handleStrSplit(Map<String, String> params) {
        String str = getValue(params, "str");
        String delimiter = params.get("delimiter");
        String listName = params.get("list");
        if (str != null && delimiter != null && listName != null) {
            String[] parts = str.split(delimiter);
            List<String> list = new ArrayList<>(Arrays.asList(parts));
            lists.put(listName, list);
            output.append("[STR] split -> list ").append(listName).append(" (").append(parts.length).append(" items)\n");
        }
    }

    private void handleStrJoin(Map<String, String> params) {
        String listName = params.get("list");
        String delimiter = params.get("delimiter");
        String resultVar = params.get("result");
        List<String> list = lists.get(listName);
        if (list != null && delimiter != null && resultVar != null) {
            String joined = String.join(delimiter, list);
            env.put(resultVar, joined);
            output.append("[STR] join -> ").append(joined).append("\n");
        } else {
            output.append("Error: str_join list not found\n");
        }
    }

    // --- Expression evaluator ---
    private String evaluateExpression(String expr) {
        if (expr == null) return null;
        String resolved = resolveVariables(expr);
        try {
            double result = evalArith(resolved);
            if (result == Math.floor(result) && !Double.isInfinite(result)) {
                return String.valueOf((long) result);
            } else {
                return String.valueOf(result);
            }
        } catch (Exception e) {
            return resolved;
        }
    }

    private double evalArith(String inputExpr) {
        final String expr = inputExpr.trim();
        return new Object() {
            int pos = -1, ch;
            void nextChar() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) { nextChar(); return true; }
                return false;
            }
            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("Unexpected character");
                return x;
            }
            double parseExpression() {
                double x = parseTerm();
                while (true) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }
            double parseTerm() {
                double x = parseFactor();
                while (true) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }
            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();
                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    if (!eat(')')) throw new RuntimeException("Missing closing parenthesis");
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected character");
                }
                return x;
            }
        }.parse();
    }

    private void handleSet(Map<String, String> params) {
        String name = params.get("name");
        String raw = params.get("value");
        if (name != null && raw != null) {
            String resolved = resolveVariables(raw);
            String finalValue = evaluateExpression(resolved);
            env.put(name, finalValue);
            output.append("[SET] ").append(name).append(" = ").append(finalValue).append("\n");
        }
    }

    private void handleEquality(Map<String, String> params, boolean isEq) {
        String a = getValue(params, "a"); String b = getValue(params, "b"); String resultVar = params.get("result");
        if (a != null && b != null && resultVar != null) {
            boolean result;
            try {
                double va = Double.parseDouble(a);
                double vb = Double.parseDouble(b);
                result = isEq ? (va == vb) : (va != vb);
            } catch (NumberFormatException e) {
                result = isEq ? a.equals(b) : !a.equals(b);
            }
            env.put(resultVar, result ? "true" : "false");
            output.append("[CMP] ").append(a).append(" vs ").append(b).append(" => ").append(env.get(resultVar)).append("\n");
        }
    }

    private String resolveCond(String rawCond) {
        if (rawCond == null) return "false";
        String varName = rawCond.startsWith("$") ? rawCond.substring(1) : rawCond;
        return env.getOrDefault(varName, "false");
    }

    private String getValue(Map<String, String> params, String key) {
        String raw = params.get(key);
        if (raw == null) return null;
        if (raw.startsWith("$")) {
            String rest = raw.substring(1);
            if (rest.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                return env.getOrDefault(rest, "");
            }
        }
        return raw;
    }

    private void handleProject(Map<String, String> params) {
        String name = params.get("name"); String file = params.get("file");
        if (name != null) env.put("projectName", name);
        if (file != null) env.put("projectFile", file);
        output.append("[Project] ").append(name).append(" -> ").append(file).append("\n");
    }

    private void handlePrint(Map<String, String> params) {
        String type = params.get("type");
        if ("text".equals(type)) { String text = params.get("text"); output.append(resolveVariables(text)).append("\n"); }
        else output.append(params.toString()).append("\n");
    }

    private String resolveVariables(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == '$' && i + 1 < s.length() && Character.isLetter(s.charAt(i + 1))) {
                int j = i + 1;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_')) j++;
                String varName = s.substring(i + 1, j);
                String value = env.getOrDefault(varName, "");
                sb.append(value);
                i = j;
            } else { sb.append(s.charAt(i)); i++; }
        }
        return sb.toString();
    }

    private void handleArith(Map<String, String> params, ArithFunc func) {
        String a = getValue(params, "a"); String b = getValue(params, "b"); String resultVar = params.get("result");
        if (a != null && b != null && resultVar != null) {
            try {
                double va = Double.parseDouble(a), vb = Double.parseDouble(b), res = func.apply(va, vb);
                env.put(resultVar, String.valueOf(res));
                output.append("[ARITH] ").append(a).append(" op ").append(b).append(" = ").append(res).append("\n");
            } catch (NumberFormatException e) { output.append("Error: arithmetic operands must be numbers\n"); }
        }
    }

    private void handleCompare(Map<String, String> params, CompareFunc func) {
        String a = getValue(params, "a"); String b = getValue(params, "b"); String resultVar = params.get("result");
        if (a != null && b != null && resultVar != null) {
            boolean result = func.compare(a, b);
            env.put(resultVar, result ? "true" : "false");
            output.append("[CMP] ").append(a).append(" vs ").append(b).append(" => ").append(env.get(resultVar)).append("\n");
        }
    }

    private void handleWriteFile(Map<String, String> params) {
        String file = params.get("file"); String content = params.get("content");
        if (file != null && content != null) {
            try { Files.writeString(Path.of(file), resolveVariables(content)); output.append("[WRITE] wrote to ").append(file).append("\n"); }
            catch (IOException e) { output.append("Error: write file failed - ").append(e.getMessage()).append("\n"); }
        }
    }

    private void handleReadFile(Map<String, String> params) {
        String file = params.get("file"); String resultVar = params.get("result");
        if (file != null && resultVar != null) {
            try { String content = Files.readString(Path.of(file)); env.put(resultVar, content); output.append("[READ] ").append(file).append(" -> ").append(resultVar).append("\n"); }
            catch (IOException e) { output.append("Error: read file failed - ").append(e.getMessage()).append("\n"); }
        }
    }

    private int getLabelIndex(String id) {
        Integer idx = labels.get(id);
        if (idx == null) { output.append("Error: label not found ").append(id).append("\n"); return -1; }
        return idx;
    }

    private void handleListCreate(Map<String, String> params) {
        String name = params.get("name");
        if (name != null) { lists.put(name, new ArrayList<>()); output.append("[LIST] created ").append(name).append("\n"); }
    }

    private void handleListAdd(Map<String, String> params) {
        String name = params.get("name"); String value = getValue(params, "value");
        List<String> list = lists.get(name);
        if (list != null && value != null) { list.add(value); output.append("[LIST] ").append(name).append(".add(").append(value).append(")\n"); }
    }

    private void handleListGet(Map<String, String> params) {
        String name = params.get("name"); String indexStr = getValue(params, "index"); String resultVar = params.get("result");
        List<String> list = lists.get(name);
        if (list != null && indexStr != null && resultVar != null) {
            try {
                int idx = Integer.parseInt(indexStr);
                if (idx >= 0 && idx < list.size()) { env.put(resultVar, list.get(idx)); output.append("[LIST] ").append(name).append("[").append(idx).append("] = ").append(list.get(idx)).append("\n"); }
                else output.append("Error: index out of bounds\n");
            } catch (NumberFormatException e) { output.append("Error: index must be integer\n"); }
        }
    }

    private void handleListSet(Map<String, String> params) {
        String name = params.get("name"); String indexStr = getValue(params, "index"); String value = getValue(params, "value");
        List<String> list = lists.get(name);
        if (list != null && indexStr != null && value != null) {
            try {
                int idx = Integer.parseInt(indexStr);
                if (idx >= 0 && idx < list.size()) { list.set(idx, value); output.append("[LIST] ").append(name).append("[").append(idx).append("] = ").append(value).append("\n"); }
                else output.append("Error: index out of bounds\n");
            } catch (NumberFormatException e) { output.append("Error: index must be integer\n"); }
        }
    }

    private void handleListSize(Map<String, String> params) {
        String name = params.get("name"); String resultVar = params.get("result");
        List<String> list = lists.get(name);
        if (list != null && resultVar != null) { env.put(resultVar, String.valueOf(list.size())); output.append("[LIST] size of ").append(name).append(" = ").append(list.size()).append("\n"); }
    }

    private void handleListRemove(Map<String, String> params) {
        String name = params.get("name"); String indexStr = getValue(params, "index");
        List<String> list = lists.get(name);
        if (list != null && indexStr != null) {
            try {
                int idx = Integer.parseInt(indexStr);
                if (idx >= 0 && idx < list.size()) { String removed = list.remove(idx); output.append("[LIST] ").append(name).append(".remove(").append(idx).append(") = ").append(removed).append("\n"); }
                else output.append("Error: index out of bounds\n");
            } catch (NumberFormatException e) { output.append("Error: index must be integer\n"); }
        }
    }

    @FunctionalInterface private interface ArithFunc { double apply(double a, double b); }
    @FunctionalInterface private interface CompareFunc { boolean compare(String a, String b); }
}