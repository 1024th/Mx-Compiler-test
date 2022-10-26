package frontend;

import ast.*;
import ast.expr.*;
import ast.stmt.*;
import utils.error.SemanticError;
import utils.scope.Scope;
import utils.scope.ClassScope;
import utils.scope.FuncScope;
import utils.scope.GlobalScope;

public class SemanticChecker implements ASTVisitor {
  public GlobalScope gScope;
  public Scope curScope;

  public SemanticChecker(GlobalScope gScope) {
    this.gScope = gScope;
    this.curScope = gScope;
    this.addBuiltinDefs();
  }

  private void addBuiltinDefs() {
    var intType = new TypeNode("int", false, null);
    var voidType = new TypeNode("void", false, null);
    var stringType = new TypeNode("string", true, null);
    addBuiltinFunc("print", voidType, stringType);
    addBuiltinFunc("println", voidType, stringType);
    addBuiltinFunc("printInt", voidType, intType);
    addBuiltinFunc("printlnInt", voidType, intType);
    addBuiltinFunc("getString", stringType);
    addBuiltinFunc("getInt", intType);
    addBuiltinFunc("toString", stringType, intType);

    ClassScope stringCls = new ClassScope("string", this.gScope, null);
    stringCls.addFunc(newBuiltinFunc("length", intType));
    stringCls.addFunc(newBuiltinFunc("substring", stringType, intType, intType));
    stringCls.addFunc(newBuiltinFunc("parseInt", intType));
    stringCls.addFunc(newBuiltinFunc("ord", intType, intType));

    // represent the 'size' function of array type
    addBuiltinFunc(".size", intType);
  }

  private void addBuiltinFunc(String funcName, TypeNode returnType, TypeNode... paramTypes) {
    this.gScope.funcs.put(funcName, newBuiltinFunc(funcName, returnType, paramTypes));
  }

  private FuncDefNode newBuiltinFunc(String funcName, TypeNode returnType, TypeNode... paramTypes) {
    var paramList = new ParamListNode(null);
    for (var i : paramTypes) {
      paramList.params.add(new ParamNode(i, "", null));
    }
    return new FuncDefNode(funcName, returnType, null, paramList, null);
  }

  @Override
  public void visit(ProgramNode node) {
    var mainFunc = this.gScope.getFunc("main");
    if (mainFunc == null) {
      throw new SemanticError("no main funciton", node.pos);
    }
    if (!mainFunc.returnType.typename.equals("int")) {
      throw new SemanticError("'main' must return 'int'", mainFunc.pos);
    }
    if (!mainFunc.params.params.isEmpty()) {
      throw new SemanticError("'main' cannot have parameter", mainFunc.pos);
    }
    for (var i : node.defs) {
      i.accept(this);
    }

  }

  @Override
  public void visit(ClassDefNode node) {
    this.curScope = this.gScope.getClass(node.className);
    // TODO Auto-generated method stub
    for (var i : node.defs) {
      // TODO: check
      i.accept(this);
    }
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(ClassCtorDefNode node) {
    var funcScope = new FuncScope(new TypeNode("void", false, null), false, this.curScope);
    this.curScope = funcScope;
    for (var i : node.body.stmts) {
      i.accept(this);
    }
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(FuncDefNode node) {
    node.returnType.accept(this);
    var funcDefNode = this.gScope.getFunc(node.funcName);
    var funcScope = new FuncScope(funcDefNode.returnType, false, this.curScope);
    this.curScope = funcScope;
    node.params.accept(this);
    for (var i : node.body.stmts) {
      i.accept(this);
    }
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(VarDefNode node) {
    for (var i : node.vars) {
      i.accept(this);
    }
  }

  @Override
  public void visit(SingleVarDefNode node) {
    node.type.accept(this);
    if (node.initExpr != null) {
      node.initExpr.accept(this);
    }
    if (!node.initExpr.type.match(node.type)) {
      throw new SemanticError("initialization expression has wrong type", node.initExpr.pos);
    }
    // TODO: check
    if (!(this.curScope instanceof ClassScope)) {
      this.curScope.addVar(node);
    }
  }

  @Override
  public void visit(ForStmtNode node) {
    var scope = new Scope(curScope, true);
    this.curScope = scope;
    if (node.initVar != null) {
      node.initVar.accept(this);
    }
    if (node.initExpr != null) {
      node.initExpr.accept(this);
    }
    node.condition.accept(this);
    if (!node.condition.type.isBool()) {
      throw new SemanticError("the expression type should be bool", node.condition.pos);
    }
    if (node.increase != null) {
      node.increase.accept(this);
    }
    node.body.accept(this);
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(IfStmtNode node) {
    node.condition.accept(this);
    if (!node.condition.type.isBool()) {
      throw new SemanticError("the expression type should be bool", node.condition.pos);
    }
    // statement inside if is in a new scope, even if it's not wrapped in {}
    var scope = new Scope(this.curScope, false);
    this.curScope = scope;
    node.thenStmt.accept(this);
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(WhileStmtNode node) {
    var scope = new Scope(curScope, true);
    this.curScope = scope;
    node.condition.accept(this);
    if (!node.condition.type.isBool()) {
      throw new SemanticError("the expression type should be bool", node.condition.pos);
    }
    node.body.accept(this);
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(BreakStmtNode node) {
    if (!this.curScope.insideLoop()) {
      throw new SemanticError("'break' statement not in loop statement", node.pos);
    }
  }

  @Override
  public void visit(ContinueStmtNode node) {
    if (!this.curScope.insideLoop()) {
      throw new SemanticError("'continue' statement not in loop statement", node.pos);
    }
  }

  @Override
  public void visit(ReturnStmtNode node) {
    // grammar guarantees that return statements are inside a function
    node.expr.accept(this);
    var funcScope = this.curScope.getFuncScope();
    if (funcScope.isLambda) {
      if (funcScope.hasReturn) {
        if (!node.expr.type.match(funcScope.returnType)) {
          throw new SemanticError("inconsistent return type inside lambda function", node.pos);
        }
      } else {
        funcScope.hasReturn = true;
        funcScope.returnType = new TypeNode(node.expr.type);
      }
    }
    var returnType = this.curScope.getReturnType();
    if (!node.expr.type.match(returnType)) {
      throw new SemanticError(
          "wrong return type '" + node.expr.type.typename + "'", node.expr.pos);
    }
    this.curScope.getFuncScope().hasReturn = true;
  }

  @Override
  public void visit(ExprStmtNode node) {
    node.expr.accept(this);
  }

  @Override
  public void visit(SuiteNode node) {
    // loop scopes are create in their own visit function,
    // so only non-loop scopes are created here.
    var scope = new Scope(curScope, false);
    this.curScope = scope;
    for (var i : node.stmts) {
      i.accept(this);
    }
    this.curScope = this.curScope.parent;
  }

  @Override
  public void visit(TypeNode node) {
    // check if a class type is defined
    if (node.isClass) {
      var cls = this.gScope.getClass(node.typename);
      if (cls == null) {
        throw new SemanticError("unknown type name '" + node.typename + "'", node.pos);
      }
    }
  }

  @Override
  public void visit(AssignExprNode node) {
    node.lhs.accept(this);
    node.rhs.accept(this);
    if (!node.rhs.type.match(node.lhs.type)) {
      throw new SemanticError("type not match in assignment expression", node.pos);
    }
    node.type = new TypeNode(node.lhs.type);
  }

  @Override
  public void visit(AtomExprNode node) {
    // TODO Auto-generated method stub
    if (node.type.typename.equals("this")) {
      var cls = this.curScope.getClassScope();
      if (cls == null) {
        throw new SemanticError("invalid use of 'this' in non-member function", node.pos);
      }
      node.type.typename = cls.className;
    } else if (node.type.typename == null) {
      // this atom expression is an identifier,
      // it can be either variable or function
      // (because class identifiers are considered as part of TypeNode)
      // Note: variable and function can have the same name!
      // Note2: member variables and member functions are not atomExprs
      if (node.isFunc) {
        var funcDef = this.gScope.getFunc(node.text);
        if (funcDef == null) {
          throw new SemanticError("use of undeclared function '" + node.text + "'", node.pos);
        }
        node.funcDef = funcDef;
      } else {
        var varDef = this.curScope.getVar(node.text, true);
        if (varDef == null) {
          throw new SemanticError("use of undeclared variable '" + node.text + "'", node.pos);
        }
        node.varDef = varDef;
        node.type = varDef.type;
      }
    }
  }

  @Override
  public void visit(BinaryExprNode node) {
    node.lhs.accept(this);
    node.rhs.accept(this);
    var op = node.op;
    var ltype = node.lhs.type;
    var rtype = node.lhs.type;
    if (ltype.isNull() || rtype.isNull()) {
      if (!op.equals("==") && !op.equals("!=")) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
      if (!ltype.isClass && !ltype.isArrayType &&
          !rtype.isClass && !rtype.isArrayType &&
          !(ltype.isNull() && rtype.isNull())) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
    } else {
      if (!ltype.match(node.rhs.type)) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
    }
    if (op.equals("==") || op.equals("!=")) {
      node.type = new TypeNode("bool", false, node.pos);
    } else if (op.equals("&&") || op.equals("||")) {
      if (!ltype.isBool()) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
      node.type = new TypeNode("bool", false, node.pos);
    } else if (op.equals("<") || op.equals("<=") ||
        op.equals(">") || op.equals(">=")) {
      if (!ltype.isString() || !ltype.isInt()) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
      node.type = new TypeNode("bool", false, node.pos);
    } else { // arithmetic
      if (op.equals("+") && ltype.isString()) {
        node.type = new TypeNode("string", true, node.pos);
        return;
      }
      if (!ltype.isInt()) {
        throw new SemanticError("invalid operand types to binary expression", node.pos);
      }
      node.type = new TypeNode("int", false, node.pos);
    }
  }

  @Override
  public void visit(FuncCallExprNode node) {
    node.function.accept(this);
    var params = node.function.funcDef.params.params;
    if (params.size() != node.args.size()) {
      throw new SemanticError("argument number does not match", node.pos);
    }
    for (int i = 0; i < node.args.size(); ++i) {
      var arg = node.args.get(i);
      arg.accept(this);
      var param = params.get(i);
      if (!arg.type.match(param.type)) {
        throw new SemanticError("argument type does not match function definition", arg.pos);
      }
    }
    node.type = new TypeNode(node.function.funcDef.returnType);
  }

  @Override
  public void visit(ParamListNode node) {
    for (var i : node.params) {
      i.accept(this);
    }
  }

  @Override
  public void visit(ParamNode node) {
    this.curScope.addVar(new SingleVarDefNode(node.type, node.name, null, node.pos));
  }

  @Override
  public void visit(IndexExprNode node) {
    node.array.accept(this);
    if (!node.array.type.isArrayType) {
      throw new SemanticError("subscript operator on non-array type", node.array.pos);
    }
    node.index.accept(this);
    node.type = new TypeNode(node.array.type);
    node.type.dimension--;
    if (node.type.dimension == 0) {
      node.type.isArrayType = false;
    }
  }

  @Override
  public void visit(MemberExprNode node) {
    node.instance.accept(this);
    if (node.instance.type.isArrayType) {
      // for array.size() function
      if (!node.isFunc) {
        throw new SemanticError("array type has no member variable", node.pos);
      }
      if (!node.member.equals("size")) {
        throw new SemanticError("array type has no member function named '" + node.member + "'", node.pos);
      }
      node.funcDef = this.gScope.getFunc(".size");
      node.type = new TypeNode(node.funcDef.returnType);
      return;
    }
    if (!node.instance.type.isClass) {
      throw new SemanticError(
          "cannot request for member '" + node.member + "' in non-class type '" + node.type.typename + "'", node.pos);
    }
    var clsName = node.instance.type.typename;
    var cls = this.gScope.getClass(clsName);
    if (node.isFunc) {
      var funcDef = cls.getFunc(node.member);
      if (funcDef == null) {
        throw new SemanticError("class '" + clsName + "' has no member function named '" + node.member + "'", node.pos);
      }
      node.funcDef = funcDef;
      node.type = new TypeNode(node.funcDef.returnType);
    } else {
      var varDef = cls.getVar(node.member, false);
      if (varDef == null) {
        throw new SemanticError("class '" + clsName + "' has no member variable named '" + node.member + "'", node.pos);
      }
      node.varDef = varDef;
      node.type = new TypeNode(node.varDef.type);
    }
  }

  @Override
  public void visit(NewExprNode node) {
    for (var i : node.sizeExprs) {
      i.accept(this);
      if (!i.type.isInt()) {
        throw new SemanticError("array size expression must have integral type", i.pos);
      }
    }
    node.type.accept(this);
  }

  @Override
  public void visit(PostfixExprNode node) {
    node.expr.accept(this);
    if (!node.expr.type.isInt()) {
      throw new SemanticError("increment and decrement only accepts int type", node.expr.pos);
    }
    node.type = new TypeNode(node.expr.type);
  }

  @Override
  public void visit(PrefixExprNode node) {
    node.expr.accept(this);
    if (!node.expr.type.isInt()) {
      throw new SemanticError("increment and decrement only accepts int type", node.expr.pos);
    }
    node.type = new TypeNode(node.expr.type);
  }

  @Override
  public void visit(UnaryExprNode node) {
    node.expr.accept(this);
    var op = node.op;
    if (op.equals("+") || op.equals("-") || op.equals("~")) {
      if (!node.expr.type.isInt()) {
        throw new SemanticError("argument type of 'operator" + op + "' should be int", node.pos);
      }
    } else { // op is "!"
      if (!node.expr.type.isBool()) {
        throw new SemanticError("argument type of 'operator" + op + "' should be bool", node.pos);
      }
    }
    node.type = new TypeNode(node.expr.type);
  }

  @Override
  public void visit(LambdaExprNode node) {
    var tmp = this.curScope;
    var funcScope = new FuncScope(null, true, node.capture ? this.curScope : this.gScope);
    this.curScope = funcScope;
    node.params.accept(this);
    for (var i : node.body.stmts) {
      i.accept(this);
    }
    if (funcScope.hasReturn) {
      node.type = new TypeNode("void", false, null);
    } else {
      node.type = new TypeNode(funcScope.returnType);
    }
    this.curScope = tmp;

    if (node.params.params.size() != node.args.size()) {
      throw new SemanticError("argument number does not match", node.pos);
    }
    for (int i = 0; i < node.args.size(); ++i) {
      var arg = node.args.get(i);
      arg.accept(this);
      var param = node.params.params.get(i);
      if (!arg.type.match(param.type)) {
        throw new SemanticError("argument type does not match function definition", arg.pos);
      }
    }
  }

}
