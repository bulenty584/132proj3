package Env;
import java.util.HashMap;
import java.util.Stack;
import java.util.HashSet;
import java.util.Set;

public class SymbolTable {
    public HashMap<String, MiniJavaClass> classMap;
    public Stack<MiniJavaClass> classes;

    public SymbolTable(){
        this.classMap = new HashMap<String, MiniJavaClass>();
        this.classes = new Stack<MiniJavaClass>();
    }

    public void add(MiniJavaClass _class){
        this.classMap.put(_class.getName(), _class);
        this.classes.push(_class);
    }

    public boolean checkAcyclicInheritance() {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();

        for (String cls : this.classMap.keySet()) {
            MiniJavaClass classToExamine = this.classMap.get(cls);
            if (!visited.contains(classToExamine.getName())) {
                if (hasCycle(classToExamine, visited, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasCycle(MiniJavaClass cls, Set<String> visited, Set<String> stack) {
        String name = cls.getName();
        visited.add(name);
        stack.add(name);
    
        MiniJavaClass parent = cls.getParent();
        if (parent != null) {
            String parentName = parent.getName();
            if (!visited.contains(parentName)) {
                if (hasCycle(parent, visited, stack)) {
                    return true;
                }
            } else if (stack.contains(parentName)) {
                return true; // Found a cycle
            }
        }
    
        stack.remove(name);
        return false;
    }

    public void print() {
        System.out.println("=== Symbol Table ===");
        for (MiniJavaClass cls : this.classMap.values()) {
            cls.print();
            System.out.println();
        }
    }

    public boolean isTypeCompatible(Variable actual, Variable expected) {

        if (actual.type.equals(expected.type)) return true;
    

        MiniJavaClass actualClass = classMap.get(actual.type);
        MiniJavaClass expectedClass = classMap.get(expected.type);
    
        if (actualClass == null || expectedClass == null) {
            return false; // at least one is not a class → incompatible
        }
    
        // Check if actual is a subclass of expected
        while (actualClass != null) {
            if (actualClass.getName().equals(expectedClass.getName())) {
                return true;
            }
            actualClass = actualClass.getParent();
        }
    
        return false;
    }
    
    

}