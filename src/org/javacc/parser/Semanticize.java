/*
 * Copyright © 2002 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * California 95054, U.S.A. All rights reserved.  Sun Microsystems, Inc. has
 * intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation,
 * these intellectual property rights may include one or more of the U.S.
 * patents listed at http://www.sun.com/patents and one or more additional
 * patents or pending patent applications in the U.S. and in other countries.
 * U.S. Government Rights - Commercial software. Government users are subject
 * to the Sun Microsystems, Inc. standard license agreement and applicable
 * provisions of the FAR and its supplements.  Use is subject to license terms.
 * Sun,  Sun Microsystems,  the Sun logo and  Java are trademarks or registered
 * trademarks of Sun Microsystems, Inc. in the U.S. and other countries.  This
 * product is covered and controlled by U.S. Export Control laws and may be
 * subject to the export or import laws in other countries.  Nuclear, missile,
 * chemical biological weapons or nuclear maritime end uses or end users,
 * whether direct or indirect, are strictly prohibited.  Export or reexport
 * to countries subject to U.S. embargo or to entities identified on U.S.
 * export exclusion lists, including, but not limited to, the denied persons
 * and specially designated nationals lists is strictly prohibited.
 */

package org.javacc.parser;

public class Semanticize extends JavaCCGlobals {

  static java.util.Vector removeList = new java.util.Vector();
  static java.util.Vector itemList = new java.util.Vector();

  static void prepareToRemove(java.util.Vector vec, Object item) {
    removeList.addElement(vec);
    itemList.addElement(item);
  }

  static void removePreparedItems() {
    for (int i = 0; i < removeList.size(); i++) {
      java.util.Vector vec = (java.util.Vector)(removeList.elementAt(i));
      vec.removeElement(itemList.elementAt(i));
    }
    removeList.removeAllElements();
    itemList.removeAllElements();
  }

  static public void start() throws MetaParseException {

    if (JavaCCErrors.get_error_count() != 0) throw new MetaParseException();

    if (Options.getLookahead() > 1 && !Options.getForceLaCheck() && Options.getSanityCheck()) {
      JavaCCErrors.warning("Lookahead adequacy checking not being performed since option LOOKAHEAD is more than 1.  Set option FORCE_LA_CHECK to true to force checking.");
    }

    /*
     * The following walks the entire parse tree to convert all LOOKAHEAD's
     * that are not at choice points (but at beginning of sequences) and converts
     * them to trivial choices.  This way, their semantic lookahead specification
     * can be evaluated during other lookahead evaluations.
     */
    for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
      ExpansionTreeWalker.postOrderWalk(((NormalProduction)enum.nextElement()).expansion, new LookaheadFixer());
    }

    /*
     * The following loop populates "production_table"
     */
    for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
      NormalProduction p = (NormalProduction)enum.nextElement();
      if (production_table.put(p.lhs, p) != null) {
        JavaCCErrors.semantic_error(p, p.lhs + " occurs on the left hand side of more than one production.");
      }
    }

    /*
     * The following walks the entire parse tree to make sure that all
     * non-terminals on RHS's are defined on the LHS.
     */
    for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
      ExpansionTreeWalker.preOrderWalk(((NormalProduction)enum.nextElement()).expansion, new ProductionDefinedChecker());
    }

    /*
     * The following loop ensures that all target lexical states are
     * defined.  Also piggybacking on this loop is the detection of
     * <EOF> and <name> in token productions.  After reporting an
     * error, these entries are removed.  Also checked are definitions
     * on inline private regular expressions.
     * This loop works slightly differently when USER_TOKEN_MANAGER
     * is set to true.  In this case, <name> occurrences are OK, while
     * regular expression specs generate a warning.
     */
    for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
      TokenProduction tp = (TokenProduction)(enum.nextElement());
      java.util.Vector respecs = tp.respecs;
      for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
        RegExprSpec res = (RegExprSpec)(enum1.nextElement());
        if (res.nextState != null) {
          if (lexstate_S2I.get(res.nextState) == null) {
            JavaCCErrors.semantic_error(res.nsTok, "Lexical state \"" + res.nextState + "\" has not been defined.");
          }
        }
        if (res.rexp instanceof REndOfFile) {
          //JavaCCErrors.semantic_error(res.rexp, "Badly placed <EOF>.");
          if (tp.lexStates != null)
            JavaCCErrors.semantic_error(res.rexp, "EOF action/state change must be specified for all states, i.e., <*>TOKEN:.");
          if (tp.kind != TokenProduction.TOKEN)
            JavaCCErrors.semantic_error(res.rexp, "EOF action/state change can be specified only in a TOKEN specification.");
          if (nextStateForEof != null || actForEof != null)
            JavaCCErrors.semantic_error(res.rexp, "Duplicate action/state change specification for <EOF>.");
          actForEof = res.act;
          nextStateForEof = res.nextState;
          prepareToRemove(respecs, res);
        } else if (tp.isExplicit && Options.getUserTokenManager()) {
          JavaCCErrors.warning(res.rexp, "Ignoring regular expression specification since option USER_TOKEN_MANAGER has been set to true.");
        } else if (tp.isExplicit && !Options.getUserTokenManager() && res.rexp instanceof RJustName) {
            JavaCCErrors.warning(res.rexp, "Ignoring free-standing regular expression reference.  If you really want this, you must give it a different label as <NEWLABEL:<" + res.rexp.label + ">>.");
            prepareToRemove(respecs, res);
        } else if (!tp.isExplicit && res.rexp.private_rexp) {
          JavaCCErrors.semantic_error(res.rexp, "Private (#) regular expression cannot be defined within grammar productions.");
        }
      }
    }

    removePreparedItems();

    /*
     * The following loop inserts all names of regular expressions into
     * "named_tokens_table" and "ordered_named_tokens".
     * Duplications are flagged as errors.
     */
    for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
      TokenProduction tp = (TokenProduction)(enum.nextElement());
      java.util.Vector respecs = tp.respecs;
      for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
        RegExprSpec res = (RegExprSpec)(enum1.nextElement());
        if (!(res.rexp instanceof RJustName) && !res.rexp.label.equals("")) {
          String s = res.rexp.label;
          Object obj = named_tokens_table.put(s, res.rexp);
          if (obj != null) {
            JavaCCErrors.semantic_error(res.rexp, "Multiply defined lexical token name \"" + s + "\".");
          } else {
            ordered_named_tokens.addElement(res.rexp);
          }
          if (lexstate_S2I.get(s) != null) {
            JavaCCErrors.semantic_error(res.rexp, "Lexical token name \"" + s + "\" is the same as that of a lexical state.");
          }
        }
      }
    }

    /*
     * The following code merges multiple uses of the same string in the same
     * lexical state and produces error messages when there are multiple
     * explicit occurrences (outside the BNF) of the string in the same
     * lexical state, or when within BNF occurrences of a string are duplicates
     * of those that occur as non-TOKEN's (SKIP, MORE, SPECIAL_TOKEN) or private
     * regular expressions.  While doing this, this code also numbers all
     * regular expressions (by setting their ordinal values), and populates the
     * table "names_of_tokens".
     */

    tokenCount = 1;
    for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
      TokenProduction tp = (TokenProduction)(enum.nextElement());
      java.util.Vector respecs = tp.respecs;
      if (tp.lexStates == null) {
        tp.lexStates = new String[lexstate_I2S.size()];
        int i = 0;
        for (java.util.Enumeration enum1 = lexstate_I2S.elements(); enum1.hasMoreElements();) {
          tp.lexStates[i++] = (String)(enum1.nextElement());
        }
      }
      java.util.Hashtable table[] = new java.util.Hashtable[tp.lexStates.length];
      for (int i = 0; i < tp.lexStates.length; i++) {
        table[i] = (java.util.Hashtable)simple_tokens_table.get(tp.lexStates[i]);
      }
      for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
        RegExprSpec res = (RegExprSpec)(enum1.nextElement());
        if (res.rexp instanceof RStringLiteral) {
          RStringLiteral sl = (RStringLiteral)res.rexp;
          // This loop performs the checks and actions with respect to each lexical state.
          for (int i = 0; i < table.length; i++) {
            // Get table of all case variants of "sl.image" into table2.
            java.util.Hashtable table2 = (java.util.Hashtable)(table[i].get(sl.image.toUpperCase()));
            if (table2 == null) {
              // There are no case variants of "sl.image" earlier than the current one.
              // So go ahead and insert this item.
              if (sl.ordinal == 0) {
                sl.ordinal = tokenCount++;
              }
              table2 = new java.util.Hashtable();
              table2.put(sl.image, sl);
              table[i].put(sl.image.toUpperCase(), table2);
            } else if (hasIgnoreCase(table2, sl.image)) { // hasIgnoreCase sets "other" if it is found.
              // Since IGNORE_CASE version exists, current one is useless and bad.
              if (!sl.tpContext.isExplicit) {
                // inline BNF string is used earlier with an IGNORE_CASE.
                JavaCCErrors.semantic_error(sl, "String \"" + sl.image + "\" can never be matched due to presence of more general (IGNORE_CASE) regular expression at line " + other.line + ", column " + other.column + ".");
              } else {
                // give the standard error message.
                JavaCCErrors.semantic_error(sl, "Duplicate definition of string token \"" + sl.image + "\" can never be matched.");
              }
            } else if (sl.tpContext.ignoreCase) {
              // This has to be explicit.  A warning needs to be given with respect
              // to all previous strings.
              String pos = ""; int count = 0;
              for (java.util.Enumeration enum2 = table2.elements(); enum2.hasMoreElements();) {
                RegularExpression rexp = (RegularExpression)(enum2.nextElement());
                if (count != 0) pos += ",";
                pos += " line " + rexp.line;
                count++;
              }
              if (count == 1) {
                JavaCCErrors.warning(sl, "String with IGNORE_CASE is partially superceded by string at" + pos + ".");
              } else {
                JavaCCErrors.warning(sl, "String with IGNORE_CASE is partially superceded by strings at" + pos + ".");
              }
              // This entry is legitimate.  So insert it.
              if (sl.ordinal == 0) {
                sl.ordinal = tokenCount++;
              }
              table2.put(sl.image, sl);
              // The above "put" may override an existing entry (that is not IGNORE_CASE) and that's
              // the desired behavior.
            } else {
              // The rest of the cases do not involve IGNORE_CASE.
              RegularExpression re = (RegularExpression)table2.get(sl.image);
              if (re == null) {
                if (sl.ordinal == 0) {
                  sl.ordinal = tokenCount++;
                }
                table2.put(sl.image, sl);
              } else if (tp.isExplicit) {
                // This is an error even if the first occurrence was implicit.
                if (tp.lexStates[i].equals("DEFAULT")) {
                  JavaCCErrors.semantic_error(sl, "Duplicate definition of string token \"" + sl.image + "\".");
                } else {
                  JavaCCErrors.semantic_error(sl, "Duplicate definition of string token \"" + sl.image + "\" in lexical state \"" + tp.lexStates[i] + "\".");
                }
              } else if (re.tpContext.kind != TokenProduction.TOKEN) {
                JavaCCErrors.semantic_error(sl, "String token \"" + sl.image + "\" has been defined as a \"" + TokenProduction.kindImage[re.tpContext.kind] + "\" token.");
              } else if (re.private_rexp) {
                JavaCCErrors.semantic_error(sl, "String token \"" + sl.image + "\" has been defined as a private regular expression.");
              } else {
                // This is now a legitimate reference to an existing RStringLiteral.
                // So we assign it a number and take it out of "rexprlist".
                // Therefore, if all is OK (no errors), then there will be only unequal
                // string literals in each lexical state.  Note that the only way
                // this can be legal is if this is a string declared inline within the
                // BNF.  Hence, it belongs to only one lexical state - namely "DEFAULT".
                sl.ordinal = re.ordinal;
                prepareToRemove(respecs, res);
              }
            }
          }
        } else if (!(res.rexp instanceof RJustName)) {
          res.rexp.ordinal = tokenCount++;
        }
        if (!(res.rexp instanceof RJustName) && !res.rexp.label.equals("")) {
          names_of_tokens.put(new Integer(res.rexp.ordinal), res.rexp.label);
        }
        if (!(res.rexp instanceof RJustName)) {
          rexps_of_tokens.put(new Integer(res.rexp.ordinal), res.rexp);
        }
      }
    }

    removePreparedItems();

    /*
     * The following code performs a tree walk on all regular expressions
     * attaching links to "RJustName"s.  Error messages are given if
     * undeclared names are used, or if "RJustNames" refer to private
     * regular expressions or to regular expressions of any kind other
     * than TOKEN.  In addition, this loop also removes top level
     * "RJustName"s from "rexprlist".
     * This code is not executed if Options.getUserTokenManager() is set to
     * true.  Instead the following block of code is executed.
     */

    if (!Options.getUserTokenManager()) {
      FixRJustNames frjn = new FixRJustNames();
      for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
        TokenProduction tp = (TokenProduction)(enum.nextElement());
        java.util.Vector respecs = tp.respecs;
        for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
          RegExprSpec res = (RegExprSpec)(enum1.nextElement());
          frjn.root = res.rexp;
          ExpansionTreeWalker.preOrderWalk(res.rexp, frjn);
          if (res.rexp instanceof RJustName) {
            prepareToRemove(respecs, res);
          }
        }
      }
    }

    removePreparedItems();

    /*
     * The following code is executed only if Options.getUserTokenManager() is
     * set to true.  This code visits all top-level "RJustName"s (ignores
     * "RJustName"s nested within regular expressions).  Since regular expressions
     * are optional in this case, "RJustName"s without corresponding regular
     * expressions are given ordinal values here.  If "RJustName"s refer to
     * a named regular expression, their ordinal values are set to reflect this.
     * All but one "RJustName" node is removed from the lists by the end of
     * execution of this code.
     */

    if (Options.getUserTokenManager()) {
      for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
        TokenProduction tp = (TokenProduction)(enum.nextElement());
        java.util.Vector respecs = tp.respecs;
        for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
          RegExprSpec res = (RegExprSpec)(enum1.nextElement());
          if (res.rexp instanceof RJustName) {

            RJustName jn = (RJustName)res.rexp;
            RegularExpression rexp = (RegularExpression)named_tokens_table.get(jn.label);
            if (rexp == null) {
              jn.ordinal = tokenCount++;
              named_tokens_table.put(jn.label, jn);
              ordered_named_tokens.addElement(jn);
              names_of_tokens.put(new Integer(jn.ordinal), jn.label);
            } else {
              jn.ordinal = rexp.ordinal;
              prepareToRemove(respecs, res);
            }
          }
        }
      }
    }

    removePreparedItems();

    /*
     * The following code is executed only if Options.getUserTokenManager() is
     * set to true.  This loop labels any unlabeled regular expression and
     * prints a warning that it is doing so.  These labels are added to
     * "ordered_named_tokens" so that they may be generated into the ...Constants
     * file.
     */
    if (Options.getUserTokenManager()) {
      for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
        TokenProduction tp = (TokenProduction)(enum.nextElement());
        java.util.Vector respecs = tp.respecs;
        for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
          RegExprSpec res = (RegExprSpec)(enum1.nextElement());
          Integer ii = new Integer(res.rexp.ordinal);
          if (names_of_tokens.get(ii) == null) {
            JavaCCErrors.warning(res.rexp, "Unlabeled regular expression cannot be referred to by user generated token manager.");
          }
        }
      }
    }

    if (JavaCCErrors.get_error_count() != 0) throw new MetaParseException();

    // The following code sets the value of the "emptyPossible" field of NormalProduction
    // nodes.  This field is initialized to false, and then the entire list of
    // productions is processed.  This is repeated as long as at least one item
    // got updated from false to true in the pass.
    boolean emptyUpdate = true;
    while (emptyUpdate) {
      emptyUpdate = false;
      for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
        NormalProduction prod = (NormalProduction)enum.nextElement();
        if (emptyExpansionExists(prod.expansion)) {
          if (!prod.emptyPossible) {
            emptyUpdate = prod.emptyPossible = true;
          }
        }
      }
    }

    if (Options.getSanityCheck() && JavaCCErrors.get_error_count() == 0) {

      // The following code checks that all ZeroOrMore, ZeroOrOne, and OneOrMore nodes
      // do not contain expansions that can expand to the empty token list.
      for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
        ExpansionTreeWalker.preOrderWalk(((NormalProduction)enum.nextElement()).expansion, new EmptyChecker());
      }

      // The following code goes through the productions and adds pointers to other
      // productions that it can expand to without consuming any tokens.  Once this is
      // done, a left-recursion check can be performed.
      for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
        NormalProduction prod = (NormalProduction)enum.nextElement();
        addLeftMost(prod, prod.expansion);
      }

      // Now the following loop calls a recursive walk routine that searches for
      // actual left recursions.  The way the algorithm is coded, once a node has
      // been determined to participate in a left recursive loop, it is not tried
      // in any other loop.
      for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
        NormalProduction prod = (NormalProduction)enum.nextElement();
        if (prod.walkStatus == 0) {
          prodWalk(prod);
        }
      }

      // Now we do a similar, but much simpler walk for the regular expression part of
      // the grammar.  Here we are looking for any kind of loop, not just left recursions,
      // so we only need to do the equivalent of the above walk.
      // This is not done if option USER_TOKEN_MANAGER is set to true.
      if (!Options.getUserTokenManager()) {
        for (java.util.Enumeration enum = rexprlist.elements(); enum.hasMoreElements();) {
          TokenProduction tp = (TokenProduction)(enum.nextElement());
          java.util.Vector respecs = tp.respecs;
          for (java.util.Enumeration enum1 = respecs.elements(); enum1.hasMoreElements();) {
            RegExprSpec res = (RegExprSpec)(enum1.nextElement());
            RegularExpression rexp = res.rexp;
            if (rexp.walkStatus == 0) {
              rexp.walkStatus = -1;
              if (rexpWalk(rexp)) {
                loopString = "..." + rexp.label + "... --> " + loopString;
                JavaCCErrors.semantic_error(rexp, "Loop in regular expression detected: \"" + loopString + "\"");
              }
              rexp.walkStatus = 1;
            }
          }
        }
      }

      /*
       * The following code performs the lookahead ambiguity checking.
       */
      if (JavaCCErrors.get_error_count() == 0) {
        for (java.util.Enumeration enum = bnfproductions.elements(); enum.hasMoreElements();) {
          ExpansionTreeWalker.preOrderWalk(((NormalProduction)enum.nextElement()).expansion, new LookaheadChecker());
        }
      }

    } // matches "if (Options.getSanityCheck()) {"

    if (JavaCCErrors.get_error_count() != 0) throw new MetaParseException();

  }

  public static RegularExpression other;

  // Checks to see if the "str" is superceded by another equal (except case) string
  // in table.
  public static boolean hasIgnoreCase(java.util.Hashtable table, String str) {
    RegularExpression rexp;
    rexp = (RegularExpression)(table.get(str));
    if (rexp != null && !rexp.tpContext.ignoreCase) {
      return false;
    }
    for (java.util.Enumeration enum = table.elements(); enum.hasMoreElements();) {
      rexp = (RegularExpression)(enum.nextElement());
      if (rexp.tpContext.ignoreCase) {
        other = rexp;
        return true;
      }
    }
    return false;
  }

  // returns true if "exp" can expand to the empty string, returns false otherwise.
  public static boolean emptyExpansionExists(Expansion exp) {
    if (exp instanceof NonTerminal) {
      return ((NonTerminal)exp).prod.emptyPossible;
    } else if (exp instanceof Action) {
      return true;
    } else if (exp instanceof RegularExpression) {
      return false;
    } else if (exp instanceof OneOrMore) {
      return emptyExpansionExists(((OneOrMore)exp).expansion);
    } else if (exp instanceof ZeroOrMore || exp instanceof ZeroOrOne) {
      return true;
    } else if (exp instanceof Lookahead) {
      return true;
    } else if (exp instanceof Choice) {
      for (java.util.Enumeration enum = ((Choice)exp).choices.elements(); enum.hasMoreElements();) {
        if (emptyExpansionExists((Expansion)enum.nextElement())) {
          return true;
        }
      }
      return false;
    } else if (exp instanceof Sequence) {
      for (java.util.Enumeration enum = ((Sequence)exp).units.elements(); enum.hasMoreElements();) {
        if (!emptyExpansionExists((Expansion)enum.nextElement())) {
          return false;
        }
      }
      return true;
    } else if (exp instanceof TryBlock) {
      return emptyExpansionExists(((TryBlock)exp).exp);
    } else {
      return false; // This should be dead code.
    }
  }

  // Updates prod.leftExpansions based on a walk of exp.
  static private void addLeftMost(NormalProduction prod, Expansion exp) {
    if (exp instanceof NonTerminal) {
      for (int i=0; i<prod.leIndex; i++) {
        if (prod.leftExpansions[i] == ((NonTerminal)exp).prod) {
          return;
        }
      }
      if (prod.leIndex == prod.leftExpansions.length) {
	NormalProduction[] newle = new NormalProduction[prod.leIndex*2];
	System.arraycopy(prod.leftExpansions, 0, newle, 0, prod.leIndex);
	prod.leftExpansions = newle;
      }
      prod.leftExpansions[prod.leIndex++] = ((NonTerminal)exp).prod;
    } else if (exp instanceof OneOrMore) {
      addLeftMost(prod, ((OneOrMore)exp).expansion);
    } else if (exp instanceof ZeroOrMore) {
      addLeftMost(prod, ((ZeroOrMore)exp).expansion);
    } else if (exp instanceof ZeroOrOne) {
      addLeftMost(prod, ((ZeroOrOne)exp).expansion);
    } else if (exp instanceof Choice) {
      for (java.util.Enumeration enum = ((Choice)exp).choices.elements(); enum.hasMoreElements();) {
        addLeftMost(prod, (Expansion)enum.nextElement());
      }
    } else if (exp instanceof Sequence) {
      for (java.util.Enumeration enum = ((Sequence)exp).units.elements(); enum.hasMoreElements();) {
        Expansion e = (Expansion)enum.nextElement();
        addLeftMost(prod, e);
        if (!emptyExpansionExists(e)) {
          break;
        }
      }
    } else if (exp instanceof TryBlock) {
      addLeftMost(prod, ((TryBlock)exp).exp);
    }
  }

  // The string in which the following methods store information.
  static private String loopString;

  // Returns true to indicate an unraveling of a detected left recursion loop,
  // and returns false otherwise.
  static private boolean prodWalk(NormalProduction prod) {
    prod.walkStatus = -1;
    for (int i = 0; i < prod.leIndex; i++) {
      if (prod.leftExpansions[i].walkStatus == -1) {
        prod.leftExpansions[i].walkStatus = -2;
        loopString = prod.lhs + "... --> " + prod.leftExpansions[i].lhs + "...";
        if (prod.walkStatus == -2) {
          prod.walkStatus = 1;
          JavaCCErrors.semantic_error(prod, "Left recursion detected: \"" + loopString + "\"");
          return false;
        } else {
          prod.walkStatus = 1;
          return true;
        }
      } else if (prod.leftExpansions[i].walkStatus == 0) {
        if (prodWalk(prod.leftExpansions[i])) {
          loopString = prod.lhs + "... --> " + loopString;
          if (prod.walkStatus == -2) {
            prod.walkStatus = 1;
            JavaCCErrors.semantic_error(prod, "Left recursion detected: \"" + loopString + "\"");
            return false;
          } else {
            prod.walkStatus = 1;
            return true;
          }
        }
      }
    }
    prod.walkStatus = 1;
    return false;
  }

  // Returns true to indicate an unraveling of a detected loop,
  // and returns false otherwise.
  static private boolean rexpWalk(RegularExpression rexp) {
    if (rexp instanceof RJustName) {
      RJustName jn = (RJustName)rexp;
      if (jn.regexpr.walkStatus == -1) {
        jn.regexpr.walkStatus = -2;
        loopString = "..." + jn.regexpr.label + "...";
        // Note: Only the regexpr's of RJustName nodes and the top leve
        // regexpr's can have labels.  Hence it is only in these cases that
        // the labels are checked for to be added to the loopString.
        return true;
      } else if (jn.regexpr.walkStatus == 0) {
        jn.regexpr.walkStatus = -1;
        if (rexpWalk(jn.regexpr)) {
          loopString = "..." + jn.regexpr.label + "... --> " + loopString;
          if (jn.regexpr.walkStatus == -2) {
            jn.regexpr.walkStatus = 1;
            JavaCCErrors.semantic_error(jn.regexpr, "Loop in regular expression detected: \"" + loopString + "\"");
            return false;
          } else {
            jn.regexpr.walkStatus = 1;
            return true;
          }
        } else {
          jn.regexpr.walkStatus = 1;
          return false;
        }
      }
    } else if (rexp instanceof RChoice) {
      for (java.util.Enumeration enum = ((RChoice)rexp).choices.elements(); enum.hasMoreElements();) {
        if (rexpWalk((RegularExpression)enum.nextElement())) {
          return true;
        }
      }
      return false;
    } else if (rexp instanceof RSequence) {
      for (java.util.Enumeration enum = ((RSequence)rexp).units.elements(); enum.hasMoreElements();) {
        if (rexpWalk((RegularExpression)enum.nextElement())) {
          return true;
        }
      }
      return false;
    } else if (rexp instanceof ROneOrMore) {
      return rexpWalk(((ROneOrMore)rexp).regexpr);
    } else if (rexp instanceof RZeroOrMore) {
      return rexpWalk(((RZeroOrMore)rexp).regexpr);
    } else if (rexp instanceof RZeroOrOne) {
      return rexpWalk(((RZeroOrOne)rexp).regexpr);
    } else if (rexp instanceof RRepetitionRange) {
      return rexpWalk(((RRepetitionRange)rexp).regexpr);
    }
    return false;
  }

  /**
   * Objects of this class are created from class Semanticize to work on
   * references to regular expressions from RJustName's.
   */
  static class FixRJustNames extends JavaCCGlobals implements TreeWalkerOp {

    public RegularExpression root;

    public boolean goDeeper(Expansion e) {
      return true;
    }

    public void action(Expansion e) {
      if (e instanceof RJustName) {
	RJustName jn = (RJustName)e;
	RegularExpression rexp = (RegularExpression)named_tokens_table.get(jn.label);
	if (rexp == null) {
	  JavaCCErrors.semantic_error(e, "Undefined lexical token name \"" + jn.label + "\".");
	} else if (jn == root && !jn.tpContext.isExplicit && rexp.private_rexp) {
	  JavaCCErrors.semantic_error(e, "Token name \"" + jn.label + "\" refers to a private (with a #) regular expression.");
	} else if (jn == root && !jn.tpContext.isExplicit && rexp.tpContext.kind != TokenProduction.TOKEN) {
	  JavaCCErrors.semantic_error(e, "Token name \"" + jn.label + "\" refers to a non-token (SKIP, MORE, IGNORE_IN_BNF) regular expression.");
	} else {
	  jn.ordinal = rexp.ordinal;
	  jn.regexpr = rexp;
	}
      }
    }

  }

  static class LookaheadFixer extends JavaCCGlobals implements TreeWalkerOp {

    public boolean goDeeper(Expansion e) {
      if (e instanceof RegularExpression) {
	return false;
      } else {
	return true;
      }
    }

    public void action(Expansion e) {
      if (e instanceof Sequence) {
	if (e.parent instanceof Choice || e.parent instanceof ZeroOrMore ||
	    e.parent instanceof OneOrMore || e.parent instanceof ZeroOrOne) {
	  return;
	}
	Sequence seq = (Sequence)e;
	Lookahead la = (Lookahead)(seq.units.elementAt(0));
	if (!la.isExplicit) {
	  return;
	}
	// Create a singleton choice with an empty action.
	Choice ch = new Choice();
	ch.line = la.line; ch.column = la.column;
	ch.parent = seq;
	Sequence seq1 = new Sequence();
	seq1.line = la.line; seq1.column = la.column;
	seq1.parent = ch;
	seq1.units.addElement(la);
	la.parent = seq1;
	Action act = new Action();
	act.line = la.line; act.column = la.column;
	act.parent = seq1;
	seq1.units.addElement(act);
	ch.choices.addElement(seq1);
	if (la.amount != 0) {
	  if (la.action_tokens.size() != 0) {
	    JavaCCErrors.warning(la, "Encountered LOOKAHEAD(...) at a non-choice location.  Only semantic lookahead will be considered here.");
	  } else {
	    JavaCCErrors.warning(la, "Encountered LOOKAHEAD(...) at a non-choice location.  This will be ignored.");
	  }
	}
	// Now we have moved the lookahead into the singleton choice.  Now create
	// a new dummy lookahead node to replace this one at its original location.
	Lookahead la1 = new Lookahead();
	la1.isExplicit = false;
	la1.line = la.line; la1.column = la.column;
	la1.parent = seq;
	// Now set the la_expansion field of la and la1 with a dummy expansion (we use EOF).
	la.la_expansion = new REndOfFile();
	la1.la_expansion = new REndOfFile();
	seq.units.setElementAt(la1, 0);
	seq.units.insertElementAt(ch, 1);
      }
    }

  }

  static class ProductionDefinedChecker extends JavaCCGlobals implements TreeWalkerOp {

    public boolean goDeeper(Expansion e) {
      if (e instanceof RegularExpression) {
	return false;
      } else {
	return true;
      }
    }

    public void action(Expansion e) {
      if (e instanceof NonTerminal) {
	NonTerminal nt = (NonTerminal)e;
	if ((nt.prod = (NormalProduction)production_table.get(nt.name)) == null) {
	  JavaCCErrors.semantic_error(e, "Non-terminal " + nt.name + " has not been defined.");
	} else {
	  nt.prod.parents.addElement(nt);
	}
      }
    }

  }

  static class EmptyChecker extends JavaCCGlobals implements TreeWalkerOp {

    public boolean goDeeper(Expansion e) {
      if (e instanceof RegularExpression) {
	return false;
      } else {
	return true;
      }
    }

    public void action(Expansion e) {
      if (e instanceof OneOrMore) {
	if (Semanticize.emptyExpansionExists(((OneOrMore)e).expansion)) {
	  JavaCCErrors.semantic_error(e, "Expansion within \"(...)+\" can be matched by empty string.");
	}
      } else if (e instanceof ZeroOrMore) {
	if (Semanticize.emptyExpansionExists(((ZeroOrMore)e).expansion)) {
	  JavaCCErrors.semantic_error(e, "Expansion within \"(...)*\" can be matched by empty string.");
	}
      } else if (e instanceof ZeroOrOne) {
	if (Semanticize.emptyExpansionExists(((ZeroOrOne)e).expansion)) {
	  JavaCCErrors.semantic_error(e, "Expansion within \"(...)?\" can be matched by empty string.");
	}
      }
    }

  }

  static class LookaheadChecker extends JavaCCGlobals implements TreeWalkerOp {

    public boolean goDeeper(Expansion e) {
      if (e instanceof RegularExpression) {
	return false;
      } else if (e instanceof Lookahead) {
	return false;
      } else {
	return true;
      }
    }

    public void action(Expansion e) {
      if (e instanceof Choice) {
	if (Options.getLookahead() == 1 || Options.getForceLaCheck()) {
	  LookaheadCalc.choiceCalc((Choice)e);
	}
      } else if (e instanceof OneOrMore) {
	OneOrMore exp = (OneOrMore)e;
	if (Options.getForceLaCheck() || (implicitLA(exp.expansion) && Options.getLookahead() == 1)) {
	  LookaheadCalc.ebnfCalc(exp, exp.expansion);
	}
      } else if (e instanceof ZeroOrMore) {
	ZeroOrMore exp = (ZeroOrMore)e;
	if (Options.getForceLaCheck() || (implicitLA(exp.expansion) && Options.getLookahead() == 1)) {
	  LookaheadCalc.ebnfCalc(exp, exp.expansion);
	}
      } else if (e instanceof ZeroOrOne) {
	ZeroOrOne exp = (ZeroOrOne)e;
	if (Options.getForceLaCheck() || (implicitLA(exp.expansion) && Options.getLookahead() == 1)) {
	  LookaheadCalc.ebnfCalc(exp, exp.expansion);
	}
      }
    }

    static boolean implicitLA(Expansion exp) {
      if (!(exp instanceof Sequence)) {
	return true;
      }
      Sequence seq = (Sequence)exp;
      Object obj = seq.units.elementAt(0);
      if (!(obj instanceof Lookahead)) {
	return true;
      }
      Lookahead la = (Lookahead)obj;
      return !la.isExplicit;
    }

  }

   public static void reInit()
   {
      removeList = new java.util.Vector();
      itemList = new java.util.Vector();
      other = null;
      loopString = null;
   }

}
