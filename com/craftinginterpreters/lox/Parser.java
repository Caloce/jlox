package com.craftinginterpreters.lox;

import java.util.List;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
  // If we found an error, this is how we return to parsing instead of stopping altogether.
  private static class ParseError extends RuntimeException {}

  // applies List function to inputted tokens.
  private final List<Token> tokens;
  // keeps track of which token we're on.
  private int current = 0;

  // applies parser function to list of inputted tokens.
  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  // How we start parsing.
  Expr parse() {
    try {
      return expression();
      // Errors are converted into nulls as far as calculation is concerned.
    } catch (ParseError error) {
      return null;
    }
  }

  private Expr expression() {
    // Says for expressions, do what you do for equality.
    return equality();
  }

  private Expr equality() {
    // the token is turned into one of our exact comparators.
    Expr expr = comparison();

    //recognizes the tokens BANG_EQUAL and EQUAL_EQUAL specifically.
    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      // step back one token to find out the operator
      Token operator = previous();
      // step forward one token to find what you're comparing to.
      Expr right = comparison();
      // make a new 3 item expression: operator, then comparator, then the right compared.
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
    return primary();
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