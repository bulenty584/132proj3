package Env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import minijava.syntaxtree.AllocationExpression;
import minijava.syntaxtree.AndExpression;
import minijava.syntaxtree.ArrayAllocationExpression;
import minijava.syntaxtree.ArrayAssignmentStatement;
import minijava.syntaxtree.ArrayLookup;
import minijava.syntaxtree.AssignmentStatement;
import minijava.syntaxtree.BracketExpression;
import minijava.syntaxtree.ClassDeclaration;
import minijava.syntaxtree.ClassExtendsDeclaration;
import minijava.syntaxtree.CompareExpression;
import minijava.syntaxtree.Expression;
import minijava.syntaxtree.ExpressionList;
import minijava.syntaxtree.ExpressionRest;
import minijava.syntaxtree.FalseLiteral;
import minijava.syntaxtree.Goal;
import minijava.syntaxtree.Identifier;
import minijava.syntaxtree.IfStatement;
import minijava.syntaxtree.IntegerLiteral;
import minijava.syntaxtree.MainClass;
import minijava.syntaxtree.MessageSend;
import minijava.syntaxtree.MethodDeclaration;
import minijava.syntaxtree.MinusExpression;
import minijava.syntaxtree.Node;
import minijava.syntaxtree.NotExpression;
import minijava.syntaxtree.PlusExpression;
import minijava.syntaxtree.PrimaryExpression;
import minijava.syntaxtree.PrintStatement;
import minijava.syntaxtree.ThisExpression;
import minijava.syntaxtree.TimesExpression;
import minijava.syntaxtree.TrueLiteral;
import minijava.syntaxtree.WhileStatement;
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
        if (indentLevel < 0){
            indentLevel = 0;
        }
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

    private boolean isThisAlias(String name) throws RuntimeException{
        return name.startsWith("this_$");
    }

    private void emitError(String ptr, String labelBase, int type) {
        if (this.isThisAlias(ptr)){
            ptr = "this";
        }

        if (this.isThisAlias(labelBase)){
            labelBase = "this";
        }

        String errorLabel = freshLabel(labelBase + "Error");
        String endLabel = freshLabel(labelBase + "End");
    
        this.emit("if0 " + ptr + " goto " + errorLabel);
        this.emit("goto " + endLabel);
        indentLevel = 0;
        this.emit(errorLabel + ":");
        indentLevel++;
        if (type == 0) this.emit("error(\"null pointer\")");
        else if (type == 1) this.emit("error(\"array index out of bounds\")");
        indentLevel--;
        this.emit(endLabel + ":");
        indentLevel++;
    }

    public String getTypeOfIdentifier(String var, MiniJavaClass cls, MiniJavaMethod method) {

        for (Variable v : method.localVars) {
            if (v.name.equals(var)) return v.type;
        }
        for (Variable p : method.parameters) {
            if (p.name.equals(var)) return p.type;
        }

        String _ret=null;
        while (cls != null){
            _ret = cls.getFieldType(var);
            if (_ret != null){
                return _ret;
            }
            cls = cls.getParent();
        }

        if (tempVarTypes.containsKey(var)) {
            return tempVarTypes.get(var);
        }

        return _ret;
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
        for (Node _node : n.f15.nodes){
            _ret = _node.accept(this, argu);
        }

        this.emit("return y0");

        return _ret;
    }

    @Override
    public String visit(ArrayAssignmentStatement n, Void argu) {
        String array = n.f0.accept(this, argu);
        String index = n.f2.accept(this, argu);
        String value = n.f5.accept(this, argu);

        // Load array length
        String length = freshTemp("v");
        this.emit(length + " = [" + array + " + 0]");

        // Check index >= 0
        String zero = freshTemp("v");
        this.emit(zero + " = 0");
        String one = freshTemp("v");
        this.emit(one + " = 1");
        String indexCheck = freshTemp("v");
        this.emit(indexCheck + " = " + zero + " - " + one);

        String isInBounds = freshTemp("v");
        this.emit(isInBounds + " = " + index + " < " + length);

        String isValid = freshTemp("v");
        this.emit(isValid + " = " + indexCheck + " * " + isInBounds);

        this.emitError(isValid, isValid, 1);

        String wordSize = freshTemp("v");
        this.emit(wordSize + " = 4");
        
        String byteOffset = freshTemp("v");
        this.emit(byteOffset + " = " + index + " * " + wordSize);
        
        String baseOffset = freshTemp("v");
        this.emit(baseOffset + " = " + byteOffset + " + " + wordSize);
        

        // Compute address: arr + offset
        String targetAddr = freshTemp("v");
        this.emit(targetAddr + " = " + array + " + " + baseOffset);

        // Write value
        this.emit("[" + targetAddr + " + 0] = " + value);

        return null;
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
        String _ret = null;
        String id = n.f0.f0.toString();
        String expr = n.f2.accept(this, argu);
        
        if (isThisAlias(expr)) {
            expr = "this";
        }

        // First, check if id is a local or parameter
        boolean isLocalOrParam = false;
        for (Variable v : currentMethod.localVars) {
            if (v.name.equals(id)) {
                isLocalOrParam = true;
                break;
            }
        }
        if (!isLocalOrParam) {
            for (Variable p : currentMethod.parameters) {
                if (p.name.equals(id)) {
                    isLocalOrParam = true;
                    break;
                }
            }
        }

        if (isLocalOrParam) {
            this.emit(id + " = " + expr); // ✅ local or param
        } else {
            // Must be a field — get its offset and this.emit field store
            int offset = currentClass.getFieldOffset(id);
            this.emit("[this + " + offset + "] = " + expr);
        }

        return id;
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
        String rightOne = freshTemp("v");
        this.emit(rightOne + " = 1");
        this.emit(plusOne + " = " + len + " + " + rightOne);
        this.tempVarTypes.put(plusOne, TypeConstants.INT);

        String totalSize = freshTemp("v");
        String rightFour = freshTemp("v");
        this.emit(rightFour + " = 4");
        this.emit(totalSize + " = " + plusOne + " * " + rightFour);
        this.tempVarTypes.put(totalSize, TypeConstants.INT);

        _ret = freshTemp("v");
        this.emit(_ret + " = alloc(" + totalSize + ")");
        this.tempVarTypes.put(_ret, TypeConstants.INT);

        // Add check
        this.emitError(_ret, _ret, 0);

        // Store length at [arr + 0]
        this.emit("[" + _ret + " + 0] = " + len);

        return _ret;
    }

    @Override
public String visit(Identifier n, Void argu) {
    String name = n.f0.toString();

    // First check if it's a local var or parameter
    for (Variable v : currentMethod.localVars) {
        if (v.name.equals(name)) {
            tempVarTypes.putIfAbsent(name, v.type);
            return name;
        }
    }
    for (Variable p : currentMethod.parameters) {
        if (p.name.equals(name)) {
            tempVarTypes.putIfAbsent(name, p.type);
            return name;
        }
    }

    String fieldType=null;
    int offset=-1;
    MiniJavaClass searchClass = currentClass;
    while (searchClass != null) {
        if (searchClass.getFieldType(name) != null) {
            fieldType = searchClass.getFieldType(name);
            offset = searchClass.getFieldOffset(name);
            break;
        }
        searchClass = searchClass.getParent();
    }

    if (offset != -1 && fieldType != null){
        String temp = freshTemp("v");
        this.emit(temp + " = [this + " + offset + "]");
        tempVarTypes.put(temp, fieldType);

        return temp;
    }

    if (this.table.classMap.get(name) != null) return name;

    return name;
}



    @Override
    public String visit(MethodDeclaration n, Void argu) {
        String _ret=null;
        this.indentLevel = 0;
        String methodName = n.f2.f0.toString();
        MiniJavaMethod expectedMethod = this.currentClass.getMethodIncludingInherited(methodName, this.table, true);
        MiniJavaMethod oldCurrentMethod = this.currentMethod;
        this.currentMethod = expectedMethod;


        String className = this.currentClass.getName();
        List<String> params = new ArrayList<>();
        params.add("this");
        for (Variable param : expectedMethod.parameters) {
            params.add(param.name);
        }

        this.emit("func " + className + "_" + methodName + "(" + String.join(" ", params) + ")");
        indentLevel++;

        for (Node stmt : n.f8.nodes) {
            stmt.accept(this, argu);
        }

        String returnExpr = n.f10.accept(this, argu);
        this.emit("return " + returnExpr);

        this.currentMethod = oldCurrentMethod;
        return _ret;
    }

    @Override
    public String visit(ArrayLookup n, Void argu) {
        String array = n.f0.accept(this, argu);
        String index = n.f2.accept(this, argu);
    
        // Load length = [array + 0]
        String length = freshTemp("v");
        this.emit(length + " = [" + array + " + 0]");
    
        // wordSize = 4
        String wordSize = freshTemp("v");
        this.emit(wordSize + " = 4");
    
        // Create constants: 0 and 1
        String one = freshTemp("v");
        String zero = freshTemp("v");
        this.emit(one + " = 1");
        this.emit(zero + " = 0");
    
        // minusOne = 0 - 1
        String minusOne = freshTemp("v");
        this.emit(minusOne + " = " + zero + " - " + one);
    
        // Bounds check: (-1 < index) && (index < length)
        String checkLower = freshTemp("v");
        this.emit(checkLower + " = " + minusOne + " < " + index);
    
        String checkUpper = freshTemp("v");
        this.emit(checkUpper + " = " + index + " < " + length);
    
        String checkBoth = freshTemp("v");
        this.emit(checkBoth + " = " + checkLower + " * " + checkUpper);
    
        this.emitError(checkBoth, checkBoth, 1);
    
        // Offset = index * wordSize
        String offset = freshTemp("v");
        this.emit(offset + " = " + index + " * " + wordSize);
    
        // Total offset = offset + wordSize (to skip length)
        String totalOffset = freshTemp("v");
        this.emit(totalOffset + " = " + offset + " + " + wordSize);
    
        // Address = array + totalOffset
        String address = freshTemp("v");
        this.emit(address + " = " + array + " + " + totalOffset);
    
        // Load result = [address + 0]
        String result = freshTemp("v");
        this.emit(result + " = [" + address + " + 0]");
    
        return result;
    }
    


    @Override
    public String visit(AllocationExpression n, Void argu) {
        String _ret=null;
        String className = n.f1.f0.toString();
        MiniJavaClass cls = this.table.classMap.get(className);

        int totalFieldCount = 0;
        MiniJavaClass cur = cls;
        while (cur != null) {
            totalFieldCount += cur.fields.size();
            cur = cur.getParent();
        }

        int sizeInBytes = 4 + totalFieldCount * 4;
        String sizeVal = freshTemp("v");
        this.emit(sizeVal + " = " + sizeInBytes);
        this.tempVarTypes.put(sizeVal, TypeConstants.INT);

        //Allocate fields table
        _ret = freshTemp("v");
        this.emit(_ret + " = alloc(" + sizeVal + ")");

        int methodCount = cls.methods.size();
        int methodsSize = methodCount * 4;
        String methodSizeVal = freshTemp("v");
        this.emit(methodSizeVal + " = " + methodsSize);
        this.tempVarTypes.put(_ret, className);

        String vmtLabel = "vmt_" + className;
        this.emit(vmtLabel + " = alloc(" + methodSizeVal + ")");

        int size = 0;
        for (String method : cls.vtable){
            String methodNum = freshTemp("v");
            this.emit(methodNum + " = " + method);
            this.emit("[" + vmtLabel + " + " + (size * 4) + "] = " + methodNum);
            size++;
        }
        this.emit("[" + _ret + " + 0] = " + vmtLabel);

        this.emitError(_ret, _ret, 0);
        return _ret;
    }

    @Override
    public String visit(MessageSend n, Void argu) {

        String _ret=null;
        String obj = n.f0.accept(this, argu);
        this.emitError(obj, obj, 0);

        String objType = this.getTypeOfIdentifier(obj, this.currentClass, this.currentMethod);
        if (this.isThisAlias(obj)){
            obj = "this";
        }

        MiniJavaClass targetClass = this.table.classMap.get(objType);
        MiniJavaClass oldClass = this.currentClass;

        String methodName = n.f2.f0.toString();
        List<String> argTemps = new ArrayList<>();
        if (n.f4 != null) {
            ExpressionList exprList = (ExpressionList) n.f4.node;
            if (exprList != null) {
                String arg1 = exprList.f0.accept(this, argu);
                    if (isThisAlias(arg1)){
                        arg1 = "this";
                    }
                argTemps.add(arg1);
                for (Node node : exprList.f1.nodes) {
                    ExpressionRest rest = (ExpressionRest) node;
                    String arg = rest.f1.accept(this, argu);
                    if (isThisAlias(arg)){
                        arg = "this";
                    }
                    argTemps.add(arg);
                }
            }
        }

        if (targetClass == null){
            throw new RuntimeException("error");
        }

        MiniJavaMethod method = targetClass.getMethodIncludingInherited(methodName, this.table, false);
        MiniJavaMethod oldMethod = this.currentMethod;
        this.currentMethod = method;

        int offset = targetClass.vtableIndices.get(methodName);
        String vtable = freshTemp("v");
        this.emit(vtable + " = [" + obj + " + 0]");
        this.tempVarTypes.put(vtable, TypeConstants.INT);

        String fnPtr = freshTemp("v");
        this.emit(fnPtr + " = [" + vtable + " + " + offset + "]");
        this.tempVarTypes.put(fnPtr, TypeConstants.INT);

        //call method
        List<String> callArgs = new ArrayList<>();
        if (this.isThisAlias(obj)){
            obj = "this";
        }
        callArgs.add(obj);
        callArgs.addAll(argTemps);

        _ret = freshTemp("v");
        this.emit(_ret + " = call " + fnPtr + "(" + String.join(" ", callArgs) + ")");
        this.tempVarTypes.put(_ret, this.currentMethod.getReturnType().type);

        this.currentClass = oldClass;
        this.currentMethod = oldMethod;

        return _ret;
    }

    @Override
    public String visit(BracketExpression n, Void argu) {
        String _ret=null;
        _ret = n.f1.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(NotExpression n, Void argu) {
        String exprVal = n.f1.accept(this, argu); // Visit inner expression

        // If exprVal is a field or alias, load its value into a temp
        if (!exprVal.startsWith("v") && !Character.isDigit(exprVal.charAt(0))) {
            String loaded = freshTemp("v");
            this.emit(loaded + " = " + exprVal);
            exprVal = loaded;
        }

        String one = freshTemp("v");
        String literalOne = freshTemp("v");
        this.emit(literalOne + " = 1");
        this.emit(one + " = " + literalOne);

        String result = freshTemp("v");
        this.emit(result + " = " + one + " - " + exprVal);
        tempVarTypes.put(result, TypeConstants.BOOLEAN);

        return result;
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
        this.emit(_ret + " = 0");
        return _ret;
    }

    @Override
    public String visit(IntegerLiteral n, Void argu) {
        String _ret=null;
        _ret = n.f0.toString();
        String temp = freshTemp("v");
        this.tempVarTypes.put(temp, TypeConstants.INT);
        this.emit(temp + " = " + _ret);
        return temp;
    }

    @Override
    public String visit(ThisExpression n, Void argu) {
        String _ret=null;
        _ret = this.currentClass.getName();
        this.tempVarTypes.put("this_$"+_ret, _ret);
        return "this_$"+_ret;
    }

    @Override 
    public String visit(IfStatement n, Void argu){
        String _ret=null;
        _ret = n.f2.accept(this, argu);
        String elseLabel = freshLabel("else");
        String endLabel = freshLabel("ifend");

        this.emit("if0 " + _ret + " goto " + elseLabel);
        n.f4.accept(this, argu);  // then branch
        this.emit("goto " + endLabel);
        this.emit(elseLabel + ":");
        n.f6.accept(this, argu);  // else branch
        this.emit(endLabel + ":");
        return _ret;
    }

    @Override
    public String visit(WhileStatement n, Void argu) {
        String _ret=null;

        String startLabel = freshLabel("whileStart");
        String bodyLabel = freshLabel("whileBody");
        String endLabel = freshLabel("whileEnd");
        this.emit(startLabel + ":");

        _ret = n.f2.accept(this, argu);
        this.emit("if0 " + _ret + " goto " + endLabel);

        // Body
        this.emit(bodyLabel + ":");
        indentLevel++;
        n.f4.accept(this, argu);

        // Loop back
        this.emit("goto " + startLabel);

        this.emit(endLabel + ":");
        indentLevel++;

        return null;
    }
}
