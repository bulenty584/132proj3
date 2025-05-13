package Env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import minijava.syntaxtree.AllocationExpression;
import minijava.syntaxtree.AndExpression;
import minijava.syntaxtree.ArrayAllocationExpression;
import minijava.syntaxtree.AssignmentStatement;
import minijava.syntaxtree.ClassDeclaration;
import minijava.syntaxtree.ClassExtendsDeclaration;
import minijava.syntaxtree.CompareExpression;
import minijava.syntaxtree.Expression;
import minijava.syntaxtree.ExpressionList;
import minijava.syntaxtree.ExpressionRest;
import minijava.syntaxtree.FalseLiteral;
import minijava.syntaxtree.Goal;
import minijava.syntaxtree.Identifier;
import minijava.syntaxtree.IntegerLiteral;
import minijava.syntaxtree.MainClass;
import minijava.syntaxtree.MessageSend;
import minijava.syntaxtree.MethodDeclaration;
import minijava.syntaxtree.MinusExpression;
import minijava.syntaxtree.Node;
import minijava.syntaxtree.PlusExpression;
import minijava.syntaxtree.PrimaryExpression;
import minijava.syntaxtree.PrintStatement;
import minijava.syntaxtree.ThisExpression;
import minijava.syntaxtree.TimesExpression;
import minijava.syntaxtree.TrueLiteral;
import minijava.visitor.GJDepthFirst;

public class TranslationVisitor extends GJDepthFirst<String, Void>{

    private final SymbolTable table;
    private final ArrayList<String> lines;
    MiniJavaClass currentClass = null;
    MiniJavaMethod currentMethod = null;
    private int tempCounter = 0;
    private int indentLevel = 0;
    private int labelCounter = 0;

    private final HashMap<String, String> tempVarTypes = new HashMap<>();



    private String indent() {
        return new String(new char[indentLevel * 2]).replace('\0', ' ');
    }

    private void emit(String line) {
        this.lines.add(indent() + line);
    }

    private String freshTemp(String letter) {
        return letter + (tempCounter++);
    }

    private String freshTemp(String letter, int index) {
        return letter + (index);
    }

    private String freshLabel(String base) {
        return base + "_" + (labelCounter++);
    }

    private void emitError(String ptr, String labelBase) {
        String errorLabel = freshLabel(labelBase + "Error");
        String endLabel = freshLabel(labelBase + "End");
    
        this.emit("if0 " + ptr + " goto " + errorLabel);
        this.emit("goto " + endLabel);
        this.emit(errorLabel + ":");
        indentLevel++;
        this.emit("error(\"null pointer\")");
        indentLevel--;
        this.emit(endLabel + ":");
        indentLevel++;
    }

    public String getTypeOfIdentifier(String var, MiniJavaClass cls, MiniJavaMethod method) {
        if (tempVarTypes.containsKey(var)) {
            return tempVarTypes.get(var);
        }

        for (Variable v : method.localVars) {
            if (v.name.equals(var)) return v.type;
        }
        for (Variable p : method.parameters) {
            if (p.name.equals(var)) return p.type;
        }

        return cls.getFieldType(cls.fields, var);
    }
    
    

    public TranslationVisitor(SymbolTable table){
        this.table = table;
        this.lines = new ArrayList<>();

        for (String clsName : this.table.classMap.keySet()){
            MiniJavaClass cls = this.table.classMap.get(clsName);
            cls.buildVtable();
            cls.buildFieldOffsets();
        }
    }

    public ArrayList<String> getLines(){
        return this.lines;
    }

    /**
    * f0 -> MainClass()
    * f1 -> ( TypeDeclaration() )*
    * f2 -> <EOF>
    */
    @Override
    public String visit(Goal n, Void argu) {
        String _ret = null;

        _ret = n.f0.accept(this, argu);

        for (Node _node : n.f1.nodes){
            _node.accept(this, argu);
        }
        return _ret;
    }

    /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> "public"
    * f4 -> "static"
    * f5 -> "void"
    * f6 -> "main"
    * f7 -> "("
    * f8 -> "String"
    * f9 -> "["
    * f10 -> "]"
    * f11 -> Identifier()
    * f12 -> ")"
    * f13 -> "{"
    * f14 -> ( VarDeclaration() )*
    * f15 -> ( Statement() )*
    * f16 -> "}"
    * f17 -> "}"
    */
    @Override
    public String visit(MainClass n, Void argu) {
        String _ret=null;
        this.emit("func Main()");
        indentLevel++;
        this.emit("y0 = 0");
        String className = n.f1.f0.toString();
        MiniJavaClass mainClass = this.table.classMap.get(className);
        mainClass.main = true;
        this.currentClass = mainClass;

        MiniJavaMethod mainMethod = mainClass.methods.get("main");
        this.currentMethod = mainMethod;

        // loop through statements

        for (Node _node : n.f14.nodes){
            _ret = _node.accept(this, argu);
        }
        for (Node _node : n.f15.nodes){
            _ret = _node.accept(this, argu);
        }

        this.emit("return y0");

        return _ret;
    }

    /**
    * f0 -> "class"
    * f1 -> Identifier()
    * f2 -> "{"
    * f3 -> ( VarDeclaration() )*
    * f4 -> ( MethodDeclaration() )*
    * f5 -> "}"
    */
    @Override
    public String visit(ClassDeclaration n, Void argu) {
        String _ret=null;
        _ret = n.f1.accept(this, argu);
        // Find class to add to table
        String classId = n.f1.accept(this, argu);
        MiniJavaClass classToAdd = this.table.classMap.get(classId);
        this.currentClass = classToAdd;

        for (Node _node : n.f3.nodes){
            _ret = _node.accept(this, argu);
        }

        // run through MethodDeclarations
        for (Node f4 : n.f4.nodes){
            _ret = f4.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, Void argu) {
        String _ret=null;

        // extract parent and child
        String childClassType = n.f1.accept(this, argu);
        String parentClassType = n.f3.accept(this, argu);

        // Get child and parent from table
        MiniJavaClass childClass = this.table.classMap.get(childClassType);
        MiniJavaClass parentClass = this.table.classMap.get(parentClassType);

        // set parent for child and set currentClass for child
        childClass.setParent(parentClass);
        this.currentClass = childClass;

        // MethodDeclarations
        for (Node n6 : n.f6.nodes){
            _ret = n6.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(AssignmentStatement n, Void argu) {
        String _ret=null;
        String id = n.f0.f0.toString();
        String expr = n.f2.accept(this, argu);
        this.emit(id + " = " + expr);
        return _ret;
    }

    @Override
    public String visit(PrintStatement n, Void argu) {
        String _ret=null;
        _ret = n.f2.accept(this, argu);
        this.emit("print(" + _ret + ")");
        return _ret;
    }


    @Override
    public String visit(PrimaryExpression n, Void argu) {
        String _ret = null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(Expression n, Void argu) {
        String _ret=null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(AndExpression n, Void argu) {
        String _ret=null;
        // Evaluate left and right expressions
        String left = n.f0.accept(this, argu);
        String right = n.f2.accept(this, argu);

        // Multiply them to simulate '&&'
        _ret = this.freshTemp("v");
        this.emit(_ret + " = " + left + " * " + right);
        this.tempVarTypes.put(_ret, TypeConstants.BOOLEAN);

        return _ret;
    }

    @Override
    public String visit(CompareExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        _ret = this.freshTemp("v");
        this.emit(_ret + " = " + comp1 + " < " + comp2);
        this.tempVarTypes.put(_ret, TypeConstants.BOOLEAN);
        return _ret;
    }

    @Override
    public String visit(PlusExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        _ret = this.freshTemp("v");
        this.emit(_ret + " = " + comp1 + " + " + comp2);
        this.tempVarTypes.put(_ret, TypeConstants.INT);
        return _ret;
    }

    @Override
    public String visit(MinusExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        _ret = this.freshTemp("v");
        this.emit(_ret + " = " + comp1 + " - " + comp2);
        this.tempVarTypes.put(_ret, TypeConstants.INT);
        return _ret;
    }

    @Override
    public String visit(TimesExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        _ret = this.freshTemp("v");
        this.emit(_ret + " = " + comp1 + " * " + comp2);
        this.tempVarTypes.put(_ret, TypeConstants.INT);
        return _ret;
    }

    /**
    * f0 -> "new"
    * f1 -> "int"
    * f2 -> "["
    * f3 -> Expression()
    * f4 -> "]"
    */
    @Override
    public String visit(ArrayAllocationExpression n, Void argu) {
        String _ret=null;
        // Evaluate length expression
        String len = n.f3.accept(this, argu);
        String plusOne = freshTemp("v");
        this.emit(plusOne + " = " + len + " + 1");
        this.tempVarTypes.put(plusOne, TypeConstants.INT);

        String totalSize = freshTemp("v");
        this.emit(totalSize + " = " + plusOne + " * 4");
        this.tempVarTypes.put(totalSize, TypeConstants.INT);

        _ret = freshTemp("v");
        this.emit(_ret + " = alloc(" + totalSize + ")");
        this.tempVarTypes.put(_ret, TypeConstants.INT);

        // Add check
        emitError(_ret, _ret);

        // Store length at [arr + 0]
        this.emit("[" + _ret + " + 0] = " + len);

        return _ret;
    }

    @Override
    public String visit(Identifier n, Void argu) {
        return n.f0.toString();
    }


    @Override
    public String visit(MethodDeclaration n, Void argu) {
        String _ret=null;
        String methodName = n.f2.f0.toString();
        MiniJavaMethod expectedMethod = this.currentClass.getMethodIncludingInherited(methodName, this.table, true);
        MiniJavaMethod oldCurrentMethod = this.currentClass.currentMethod;
        this.currentClass.currentMethod = expectedMethod;

        String className = this.currentClass.getName();
        List<String> params = new ArrayList<>();
        params.add("this");
        for (Variable param : expectedMethod.parameters) {
            params.add(param.name);
        }

        emit("func " + className + "_" + methodName + "(" + String.join(" ", params) + ")");
        indentLevel++;

        for (Node stmt : n.f8.nodes) {
            stmt.accept(this, argu);
        }

        String returnExpr = n.f10.accept(this, argu);
        emit("return " + returnExpr);

        indentLevel--;

        this.currentClass.currentMethod = oldCurrentMethod;
        return _ret;
    }

    @Override
    public String visit(AllocationExpression n, Void argu) {
        String _ret=null;
        String className = n.f1.f0.toString();
        MiniJavaClass cls = this.table.classMap.get(className);

        int fieldCount = cls.fields.size();
        int sizeInBytes = 4 + fieldCount * 4;
        String sizeVal = freshTemp("v");
        emit(sizeVal + " = " + sizeInBytes);
        this.tempVarTypes.put(sizeVal, TypeConstants.INT);

        //Allocate fields table
        _ret = freshTemp("v");
        emit(_ret + " = alloc(" + sizeVal + ")");
        this.tempVarTypes.put(_ret, className);

        int methodCount = cls.methods.size();
        int methodsSize = methodCount * 4;
        String methodSizeVal = freshTemp("v");
        emit(methodSizeVal + " = " + methodsSize);
        this.tempVarTypes.put(_ret, className);

        String vmtLabel = "vmt_" + className;
        emit(vmtLabel + " = alloc(" + methodSizeVal + ")");

        for (String method : cls.methods.keySet()){
            int size = 0;
            String methodNum = freshTemp("v");
            String methodName = "@" + cls.getName() + "_" + method;
            emit(methodNum + " = " + methodName);
            emit("[" + vmtLabel + " + " + size * 4 + "] = " + methodNum);
        }

        emit("[" + _ret + " + 0] = " + vmtLabel);

        return _ret;
    }

    @Override
    public String visit(MessageSend n, Void argu) {

        String _ret=null;
        String obj = n.f0.accept(this, argu);
        emitError(obj, obj);

        String objType = this.getTypeOfIdentifier(obj, this.currentClass, this.currentMethod);

        MiniJavaClass targetClass = this.table.classMap.get(objType);
        MiniJavaClass oldClass = this.currentClass;
        this.currentClass = targetClass;

        String methodName = n.f2.f0.toString();
        MiniJavaMethod method = targetClass.getMethodIncludingInherited(methodName, this.table, false);

        MiniJavaMethod oldMethod = targetClass.currentMethod;
        targetClass.currentMethod = method;

        List<String> argTemps = new ArrayList<>();
        if (n.f4 != null) {
            ExpressionList exprList = (ExpressionList) n.f4.node;
            if (exprList != null) {
                argTemps.add(exprList.f0.accept(this, argu));
                for (Node node : exprList.f1.nodes) {
                    ExpressionRest rest = (ExpressionRest) node;
                    argTemps.add(rest.f1.accept(this, argu));
                }
            }
        }

        int offset = targetClass.vtableIndices.get(methodName);  // assumes buildVtable() was run
        String vtable = freshTemp("v");
        emit(vtable + " = [" + obj + " + 0]");
        this.tempVarTypes.put(vtable, TypeConstants.INT);

        String fnPtr = freshTemp("v");
        emit(fnPtr + " = [" + vtable + " + " + offset + "]");
        this.tempVarTypes.put(fnPtr, TypeConstants.INT);

        //call method
        List<String> callArgs = new ArrayList<>();
        callArgs.add(obj);
        callArgs.addAll(argTemps);

        _ret = freshTemp("v");
        emit(_ret + " = call " + fnPtr + "(" + String.join(" ", callArgs) + ")");
        this.tempVarTypes.put(_ret, TypeConstants.INT);

        //cleanup
        targetClass.currentMethod = oldMethod;
        this.currentClass = oldClass;

        return _ret;
    }







    @Override
    public String visit(TrueLiteral n, Void argu) {
        String _ret=null;
        _ret = freshTemp("v");
        this.tempVarTypes.put(_ret, TypeConstants.BOOLEAN);
        this.emit(_ret + " = 1");
        return _ret;
    }


    @Override
    public String visit(FalseLiteral n, Void argu) {
        String _ret=null;
        _ret = freshTemp("v");
        this.tempVarTypes.put(_ret, TypeConstants.BOOLEAN);
        this.emit(_ret + " = 1");
        return _ret;
    }

    @Override
    public String visit(IntegerLiteral n, Void argu) {
        String _ret=null;
        _ret = n.f0.toString();
        String temp = freshTemp("v");
        this.tempVarTypes.put(_ret, TypeConstants.INT);
        this.emit(temp + " = " + _ret);
        return temp;
    }

    @Override
    public String visit(ThisExpression n, Void argu) {
        String _ret=null;
        _ret = this.currentClass.getName();
        this.tempVarTypes.put("this_"+_ret, _ret);
        return "this_"+_ret;
    }




    
}
