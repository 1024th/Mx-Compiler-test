package ast;

import utils.Position;

public class TypeNode extends BaseNode {
  public final String typename;
  public final boolean isArrayType;
  public final int dimension;

  public TypeNode(String typename, Position pos) {
    super(pos);
    this.typename = typename;
    this.isArrayType = false;
    this.dimension = 0;
  }

  public TypeNode(String typename, int dimension, Position pos) {
    super(pos);
    this.typename = typename;
    this.isArrayType = true;
    this.dimension = dimension;
  }

  @Override
  public void accept(ASTVisitor visitor) {
    visitor.visit(this);
  }
}
