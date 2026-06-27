import java.util.List;

public class FuncDef {
    List<String> paramNames;
    List<Command> body;

    public FuncDef(List<String> paramNames, List<Command> body) {
        this.paramNames = paramNames;
        this.body = body;
    }
}