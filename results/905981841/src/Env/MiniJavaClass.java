package Env;

import java.util.HashMap;
import java.util.List;
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
    private boolean vTableBuilt = false;

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
        name = sanitize(name);
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

    public String getFieldType(String fieldName) {
        MiniJavaClass current = this;
        while (current != null) {
            for (Variable field : current.fields) {
                if (field.name.equals(fieldName)) {
                    return field.type;
                }
            }
            current = current.getParent();
        }
        return null;
    }
    
    public int getFieldOffset(String fieldName) {
        MiniJavaClass current = this;
        while (current != null) {
            String mangled = current.getName() + "_" + fieldName;
            Integer offset = current.fieldOffsets.get(mangled);
            if (offset != null) {
                return offset;
            }
            current = current.getParent();
        }
        return -1;
    }    

    public MiniJavaMethod getMethod(String methodName){
        return this.methods.get(methodName);
    }

    public MiniJavaMethod getMethodIncludingInherited(String methodName, SymbolTable table, boolean checkingMethodOverride) {
        MiniJavaClass current = this;
        while (current != null) {
            MiniJavaMethod method = current.methods.get(methodName);
    
            if (method != null) {
                return method;
            }
            current = current.getParent();
        }
        return null;
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }
    
    

    public void buildVtable(){
        if (this.vTableBuilt) return;
        if (this.parent != null){
            this.parent.buildVtable();
            this.vtable.addAll(this.parent.vtable);
            this.vtableIndices.putAll(this.parent.vtableIndices);
        }
        // add to child's vtable
        for (String methodName : this.methods.keySet()){
            String _className = sanitize(className);
            String label = "@" + _className + "_" + methodName;

            //override parent
            if (this.vtableIndices.containsKey(methodName)){
                int offset = vtableIndices.get(methodName);
                int slot = offset / 4;
                this.vtable.set(slot, label);

            } else{
                int slot = vtable.size();
                this.vtable.add(label);
                this.vtableIndices.put(methodName, slot * 4);
            }
        }

        this.vTableBuilt = true;
    }

    public void buildFieldOffsets() {
        int maxOffset = 0;
        if (this.parent != null) {
            parent.buildFieldOffsets();

            for (String name : parent.fieldOffsets.keySet()) {
                this.fieldOffsets.put(name, parent.fieldOffsets.get(name));

                int parentOffset = parent.fieldOffsets.get(name);
                if (maxOffset <= parentOffset) {
                    maxOffset = parentOffset;
                }
            }
        }

        int offset = maxOffset + 4;
    
        for (Variable f : this.fields) {

            this.fieldOffsets.put(this.getName() + "_"+f.name, offset);
            offset += 4;
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
