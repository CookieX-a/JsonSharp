import java.util.List;

public class FuncDef {
    public List<String> paramNames;  // 参数名列表
    public List<Command> body;       // 函数体命令（已展开）

    public FuncDef(List<String> paramNames, List<Command> body) {
        this.paramNames = paramNames;
        this.body = body;
    }
}