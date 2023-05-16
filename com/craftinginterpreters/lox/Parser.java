package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  // If we found an error, this is how we return to parsing instead of stopping altogether.
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  // keeps track of which token we're on.
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }


  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }


  private Expr expression() {
    return assignment();
  }

  private Stmt declaration() {
    try {
      if (match(CLASS)) return classDeclaration();
      if (match(FUN)) return function("function");
      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(IDENTIFIER, "Expect class name.");

    Expr.Variable superclass = null;
    if (match(LESS)) {
      consume(IDENTIFIER, "expect superclass name.");
      superclass = new Expr.Variable(previous());
    }
    consume(LEFT_BRACE, "Expect '{' before class body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(function("method"));
    }

    consume(RIGHT_BRACE, "expect '}' after class body.");

    return new Stmt.Class(name, superclass, methods);
  }

  private Stmt statement() {
/*     if (match(BREAK)) return breakStatement(); */
    if (match(FOR)) return forStatement();
    if (match(IF)) return ifStatement();
    if (match(PRINT)) return printStatement();
    if (match(RETURN)) return returnStatement();
    if (match(WHILE)) return whileStatement();
    if (match(LEFT_BRACE)) return new Stmt.Block(block());

    return expressionStatement();
  }

/*   private Stmt breakStatement() {
    consume(SEMICOLON, "expect ';' after break.");
    return new Stmt.Break();
  } */

  private Stmt forStatement() {
    consume(LEFT_PAREN, "expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "expect ')' after for clauses.");
    Stmt body = statement();

    if (increment != null) {
      body = new Stmt.Block(
        Arrays.asList(
            body,
            new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.)");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "expect ')' after condition.");
    Stmt body = statement();

    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt.Function function(String kind) {
    Token name = consume(IDENTIFIER, "Expect" + kind + " name.");
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          error(peek(), "Can't have more than 255 parameters.");
        }

        parameters.add(
            consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
    List<Stmt> body = block();
    return new Stmt.Function(name, parameters, body);
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();
    
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume (RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get) {
        Expr.Get get = (Expr.Get)expr;
        return new Expr.Set(get.object, get.name, value);
      }

      error(equals, "Invalid assignment target.");
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    // ship it back.
    return expr;
  }

  private Expr comparison() {
  // Check if term function cares about our token.
    Expr expr = term();

    // Handles the specific tokens > >= < and <=.
    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      // Composes a new binary expression using previous expression, current operator, and right termed.
      expr = new Expr.Binary(expr, operator, right);
    }

    // Ships back parsed expression.
    return expr;
  }

  private Expr term() {
    //check if factor function cares about our token.
    Expr expr = factor();

    // This function cares about only the - and + tokens.
    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      // Figures out the parsed expression using the expression, operator, and factored right.
      expr = new Expr.Binary(expr, operator, right);
    }
  
    // Ships back parsed expression.
    return expr;
  }

  private Expr factor() {
    // Check if unary function cares about our token.
    Expr expr = unary();

    //This function cares only about the / and * tokens.
    while (match(SLASH, STAR)) {
      Token operator = previous();
      // for / and * tokens, the next token should be unary.
      Expr right = unary();
      // Creates a parsed expression using previous expression, / or *, and calculated right unary.
      expr = new Expr.Binary(expr, operator, right);
    }

    // Ships back parsed expression.
    return expr;
  }

  private Expr unary() {
    // This function cares only about ! and - tokens.
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      // Creates a parsed expression using operator and calculated right unary.
      return new Expr.Unary(operator, right);
    }

    // send result to primary function.
    return call();
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(DOT)) {
        Token name = consume(IDENTIFIER,
        "Expect property name after '.'.");
        expr = new Expr.Get(expr, name);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          error(peek(), "Can't have more than 255 arguments.");
        }
        arguments.add(expression());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr primary() {
    // Parses false, true, and nil tokens into their literal forms.
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    // For numbers and strings, keep using the number or string literal until the number or string is over.
    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER, "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }

    if (match(THIS)) return new Expr.This(previous());

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
    Expr expr = expression();
    // If we found a ( but didn't find a ), then that's an error.
    consume(RIGHT_PAREN, "Expect ')' after expression.");
    return new Expr.Grouping(expr);
    }

    // If no cases match token, token can't start expression. This is that error.
    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    // If the Token type is recognized, consume it and say true.
    for (TokenType type: types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    // If the token type does not exist, say false.
    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    //The token eater checks if it's eating the right type of thing. If not, error time.
    throw error(peek(), message);
  }

  // If next token is of the type we're calculating right now, say true.
  private boolean check(TokenType type) {
    // Or if it's the last token, say false.
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  // Consume and return next token, unless it's the end token.
  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  // If we found the isAtEnd token, return exactly "EOF" which means end of file.
  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  // Returns to us the next thing to consume/parse.
  private Token peek() {
    return tokens.get(current);
  }

  // How to get the previous token. Step back in the list by 1.
  private Token previous() {
    return tokens.get(current - 1);
  }

  // When we have an unexpected token, return the right error message for that token type.
  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  // This is how the parser skips to the next statement if it got tripped by an error.
  private void synchronize() {
    advance();

    while(!isAtEnd()) {
      // if we found a ";", we're done with the previous statement and can look for next.
      if (previous().type == SEMICOLON) return;

      // All of these tokens are valid clues that we're onto a new statement.
      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}