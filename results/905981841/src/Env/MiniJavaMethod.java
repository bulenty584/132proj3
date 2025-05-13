package Env;
import java.util.Set;
import java.util.LinkedHashSet;

public class MiniJavaMethod {

    //parameters and local variables
    public Set<Variable> parameters;
    public Set<Variable> localVars;
    private final String name;
    private final Variable returnType;

    public MiniJavaMethod(String name, Variable returnType){
        this.parameters = new LinkedHashSet<Variable>();
        this.localVars = new LinkedHashSet<Variable>();
        this.name = name;
        this.returnType = returnType;
    }

    public String getMethodName(){
        return this.name;
    }

    public Variable getReturnType(){
        return returnType;
    }

    public boolean hasParameter(Variable param){
        for (Variable trav : this.parameters){
            if (trav.name.equals(param.name)){
                return true;
            }
        }
        return false;
    }

    public boolean hasLocalvar(Variable var){
        for (Variable trav : this.localVars){
            if (trav.name.equals(var.name)){
                return true;
            }
        }
        return false;
    }

    public void print(){
        System.out.println("    Method: " + this.name + " returns " + this.returnType.type);
        
        if (!this.parameters.isEmpty()) {
            System.out.println("      Parameters:");
            for (Variable param : this.parameters) {
                System.out.println("        " + param.name);
            }
        }

        if (!this.localVars.isEmpty()) {
            System.out.println("      Local Variables:");
            for (Variable localVar : this.localVars) {
                System.out.println("        " + localVar.name);
            }
        }
    }
}
