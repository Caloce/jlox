package com.craftinginterpreters.lox;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.craftinginterpreters.lox.Stmt.Expression;

class AstPrinter implements Expr.Visitor<String>, Stmt.Visitor<String> {

  public static void main(String[] args) throws IOException {
    String path = "test.lox";
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    String source = new String(bytes, Charset.defaultCharset());

    List<Stmt> statements = Lox.getStatements(source);

    System.out.println("(ns user)");
    System.out.println("");
    System.out.println(new AstPrinter().print(statements));
  }

  String print(List<Stmt> statements) {
    StringBuilder str = new StringBuilder();

    try {
      for (Stmt statement : statements) {
        String clojureStr = statement.accept(this);
        str.append(clojureStr);
      }

    } catch (RuntimeError error) {
       Lox.runtimeError(error);
    }

    return str.toString();
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    return expr.value.toString();
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    return "ASSIGN";
  }

  @Override
  public String visitCallExpr(Expr.Call expr) {
    return "CALL";
  }

  @Override
  public String visitGetExpr(Expr.Get expr) {
    return "GET";
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return "LOGICAL";
  }

  @Override
  public String visitSetExpr(Expr.Set expr) {
    return "SET";
  }

  @Override
  public String visitSuperExpr(Expr.Super expr) {
    return "SUPER";
  }

  @Override
  public String visitThisExpr(Expr.This expr) {
    return "THIS";
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return "VAR";
  }

  @Override
  public String visitBlockStmt(Stmt.Block stmt) {
    return "BLOCK";
  }

  @Override
  public String visitClassStmt(Stmt.Class stmt) {
    return "CLASS";
  }

  @Override
  public String visitExpressionStmt(Stmt.Expression stmt) {
    return "EXPRESSION";
  }

  @Override
  public String visitFunctionStmt(Stmt.Function stmt) {
    StringBuilder str = new StringBuilder();
    String name = stmt.name.lexeme;

    str.append("(defn ");
    str.append(name);
    str.append(" [");

    for (Token t : stmt.params) {
      str.append(" ");
      str.append(t.lexeme);
      str.append(" ");
    }

    str.append("] ");

    for (Stmt statement : stmt.body) {
      String s = statement.accept(this);
      str.append(s);
    }

    str.append(")");

    return str.toString();
  }

  @Override
  public String visitIfStmt(Stmt.If stmt) {
    return "IF";
  }

  @Override
  public String visitPrintStmt(Stmt.Print stmt) {
    StringBuilder str = new StringBuilder();
    Object value = evaluate(stmt.expression);
    String printTarget = stringify(value);

    str.append("(println ");
    str.append(printTarget);
    str.append(")");

    return str.toString();
  }

  @Override
  public String visitReturnStmt(Stmt.Return stmt) {
    StringBuilder str = new StringBuilder();

    str.append("(return ");
    str.append("'returned thing goes here'");
    str.append(")");

    return str.toString();
  }

  @Override
  public String visitVarStmt(Stmt.Var stmt) {
    return "VAR";
  }

  @Override
  public String visitWhileStmt(Stmt.While stmt) {
    return "WHILE";
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  private String parenthesize(Stmt... stmts) {
    StringBuilder builder = new StringBuilder();

    builder.append("(");
    for (Stmt stmt : stmts) {
      builder.append(" ");
      builder.append(stmt.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
}
