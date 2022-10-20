package ast.expr;

import ast.ASTVisitor;
import utils.Position;

public class UnaryExprNode extends ExprNode {
  public ExprNode expr;
  public String op;

  public UnaryExprNode(ExprNode expr, String op, Position pos) {
    super(expr.type, false, pos);
    this.expr = expr;
    this.op = op;
  }

  @Override
  public void accept(ASTVisitor visitor) {
    visitor.visit(this);
  }
}
