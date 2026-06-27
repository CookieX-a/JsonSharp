import java.util.Map;

public class Command {
    private String name;
    private Map<String, String> params;
    private int line;

    public Command(String name, Map<String, String> params) {
        this(name, params, -1);
    }

    public Command(String name, Map<String, String> params, int line) {
        this.name = name;
        this.params = params;
        this.line = line;
    }

    public String getName() { return name; }
    public Map<String, String> getParams() { return params; }
    public int getLine() { return line; }
    public void setLine(int line) { this.line = line; }

    @Override
    public String toString() {
        return "{\"" + name + "\": " + params + "}" + (line > 0 ? " (line " + line + ")" : "");
    }
}