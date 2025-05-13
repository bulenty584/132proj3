package Env;

import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;

class Variable {
    public final String name;
    public String type; // "int", "boolean", or class name

    public Variable(String name, String type) {
        this.name = name;
        this.type = type;
    }
}

public class MiniJavaClass {
    private final String className;
    private MiniJavaClass parent = null;

    public List<Variable> fields = new LinkedList<Variable>(); // fieldName → type
    public HashMap<String, MiniJavaMethod> methods;
    public MiniJavaMethod currentMethod = null;
    public List<String> vtable = new ArrayList<String>(); // methods
    public HashMap<String, Integer> vtableIndices = new HashMap<String, Integer>(); // methodName → offset
    public HashMap<String, Integer> fieldOffsets = new HashMap<String, Integer>();
    public boolean main = false;


    public MiniJavaClass(String name){
        this.methods = new HashMap<String, MiniJavaMethod>();
        this.className = name;
    }

    public MiniJavaClass(String name, MiniJavaClass parent){
        this.methods = new HashMap<String, MiniJavaMethod>();
        this.className = name;
        this.parent = parent;
    }

    public String getName(){
        return this.className;
    }

    public void addMethod(MiniJavaMethod method){
        methods.put(method.getMethodName(), method);
        this.currentMethod = method;
    }

    public MiniJavaClass getParent(){
        return this.parent;
    }

    public void setParent(MiniJavaClass parentClass){

        if (this == parent){
            throw new RuntimeException("Self inheritance");
        } else{
            this. parent = parentClass;
        }
    }

    public boolean addField(Variable var) {

        for (Variable existing : this.fields) {
            if (existing.name.equals(var.name)) {
                return false; // Duplicate name, don't add
            }
        }
        this.fields.add(var);
        return true; // Successfully added
    }

    public String getFieldType(List<Variable> fields, String fieldName) {
        for (Variable field : this.fields) {
            if (field.name.equals(fieldName)) {
                return field.type;
            }
        }
        return null;
    }

    public MiniJavaMethod getMethod(String methodName){
        return this.methods.get(methodName);
    }

    // Method to return a method or parent method with same method signature (Error checking)
    public MiniJavaMethod getMethodIncludingInherited(String methodName, SymbolTable table, boolean checkingMethodOverride) {
        MiniJavaClass current = this;
        while (current != null) {
            MiniJavaMethod method = current.methods.get(methodName);
    
            if (method != null) {
                if (checkingMethodOverride && current.getParent() != null) {
                    MiniJavaMethod parentMethod = current.getParent().getMethodIncludingInherited(methodName, table, false);
                    if (parentMethod != null) {
                        // Check parameter count
                        if (parentMethod.parameters.size() != method.parameters.size()) {
                            throw new RuntimeException("Cannot overload methods: different parameter count for method '" + methodName + "'");
                        }
    
                        Iterator<Variable> childIter = method.parameters.iterator();
                        Iterator<Variable> parentIter = parentMethod.parameters.iterator();

                        //check actual parameters
                        while (childIter.hasNext() && parentIter.hasNext()) {
                            Variable childParam = childIter.next();
                            Variable parentParam = parentIter.next();
                            if (!childParam.type.equals(parentParam.type)) {
                                throw new RuntimeException("Cannot overload methods: parameter type mismatch in method '" + methodName + "'");
                            }
                        }

                        // If one iterator still has elements, the number of parameters mismatch
                        if (childIter.hasNext() || parentIter.hasNext()) {
                            throw new RuntimeException("Cannot overload methods: different parameter count for method '" + methodName + "'");
                        }

    
                        // Check return type: child return type must be equal to actual type of parent return type
                        Variable parentReturn = parentMethod.getReturnType();
                        Variable childReturn = method.getReturnType();
                        if (!childReturn.type.equals(parentReturn.type)) {
                            throw new RuntimeException("Cannot override method '" + methodName + "' with incompatible return type.");
                        }
                    }
                }
                return method;
            }
            current = current.getParent();
        }
        return null;
    }

    public void buildVtable(){
        if (this.parent != null){
            this.parent.buildVtable();
            this.vtable.addAll(this.parent.vtable);
            this.vtableIndices.putAll(this.parent.vtableIndices);
        }
        // add to child's vtable
        for (String methodName : this.methods.keySet()){
            String label = "@" + this.className + "_" + methodName;

            //override parent
            if (this.vtableIndices.containsKey(methodName)){
                int offset = vtableIndices.get(methodName);
                int slot = (offset - 4) / 4;
                this.vtable.set(slot, label);

            } else{
                int slot = vtable.size();
                this.vtable.add(label);
                this.vtableIndices.put(methodName, slot * 4);
            }
        }
    }

    public void buildFieldOffsets(){
        int offset = 4;

        if (this.parent != null){
            parent.buildFieldOffsets();
            this.fieldOffsets.putAll(parent.fieldOffsets);
            offset = 4 + (parent.fieldOffsets.size() * 4);
        }

        for (Variable f : this.fields){
            if (!fieldOffsets.containsKey(f.name)){
                fieldOffsets.put(f.name, offset);
                offset += 4;
            }
        }
    }
    

    public void print() {
        System.out.println("Class: " + this.className + (this.parent != null ? " extends " + this.parent.getName() : ""));
        
        if (!fields.isEmpty()) {
            System.out.println("  Fields:");
            for (Variable field : this.fields) {
                System.out.println("    " + field.name);
            }
        }

        if (!methods.isEmpty()) {
            System.out.println("  Methods:");
            for (MiniJavaMethod method : this.methods.values()) {
                method.print();
            }
        }
    }
}
