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

package org.javacc.jjtree;

import java.util.Enumeration;
import java.util.Hashtable;

public class NodeScope
{
  private ASTProduction production;
  private ASTNodeDescriptor node_descriptor;

  private String closedVar;
  private String exceptionVar;
  private String nodeVar;
  private int scopeNumber;

  NodeScope(ASTProduction p, ASTNodeDescriptor n)
  {
    production = p;

    if (n == null) {
      String nm = production.name;
      if (JJTreeOptions.getNodeDefaultVoid()) {
	nm = "void";
      }
      node_descriptor = ASTNodeDescriptor.indefinite(nm);
    } else {
      node_descriptor = n;
    }

    scopeNumber = production.getNodeScopeNumber(this);
    nodeVar = constructVariable("n");
    closedVar = constructVariable("c");
    exceptionVar = constructVariable("e");
  }


  boolean isVoid()
  {
    return node_descriptor.isVoid();
  }


  ASTNodeDescriptor getNodeDescriptor()
  {
    return node_descriptor;
  }


  String getNodeDescriptorText()
  {
    return node_descriptor.getDescriptor();
  }


  String getNodeVariable()
  {
    return nodeVar;
  }


  private String constructVariable(String id)
  {
    String s = "000" + scopeNumber;
    return "jjt" + id + s.substring(s.length() - 3, s.length());
  }


  boolean usesCloseNodeVar()
  {
    return true;
  }


  void insertOpenNodeDeclaration(IO io, String indent)
  {
    insertOpenNodeCode(io, indent);
  }


  void insertOpenNodeCode(IO io, String indent)
  {
    String type = node_descriptor.getNodeType();

    /* Ensure that there is a template definition file for the node
       type. */
    NodeFiles.ensure(io, type);

    io.print(indent + type + " " + nodeVar + " = ");
    if (JJTreeOptions.getNodeFactory()) {
      if (JJTreeOptions.getNodeUsesParser()) {
	String p = JJTreeOptions.getStatic() ? "null" : "this";
	io.println("(" + type + ")" + type + ".jjtCreate(" + p + ", " +
		   node_descriptor.getNodeId() +");");
      } else {
	io.println("(" + type + ")" + type + ".jjtCreate(" + 
		   node_descriptor.getNodeId() +");");
      }
    } else {
      if (JJTreeOptions.getNodeUsesParser()) {
	String p = JJTreeOptions.getStatic() ? "null" : "this";
	io.println("new " + type + "(" + p + ", " +
		   node_descriptor.getNodeId() + ");");
      } else {
	io.println("new " + type + "(" + node_descriptor.getNodeId() + ");");
      }
    }

    if (usesCloseNodeVar()) {
      io.println(indent + "boolean " + closedVar + " = true;");
    }
    io.println(indent + node_descriptor.openNode(nodeVar));
    if (JJTreeOptions.getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + nodeVar + ");");
    }
  }


  void insertCloseNodeCode(IO io, String indent, boolean isFinal)
  {
    io.println(indent + node_descriptor.closeNode(nodeVar));
    if (usesCloseNodeVar() && !isFinal) {
      io.println(indent + closedVar + " = false;");
    }
    if (JJTreeOptions.getNodeScopeHook()) {
      io.println(indent + "jjtreeCloseNodeScope(" + nodeVar + ");");
    }
  }


  void insertOpenNodeAction(IO io, String indent)
  {
    io.println(indent + "{");
    insertOpenNodeCode(io, indent + "  ");
    io.println(indent + "}");
  }


  void insertCloseNodeAction(IO io, String indent)
  {
    io.println(indent + "{");
    insertCloseNodeCode(io, indent + "  ", false);
    io.println(indent + "}");
  }


  private void insertCatchBlocks(IO io, Enumeration thrown_names,
				 String indent)
  {
    String thrown;
    if (thrown_names.hasMoreElements()) {
      io.println(indent + "} catch (Throwable " +  exceptionVar + ") {");

      if (usesCloseNodeVar()) {
	io.println(indent + "  if (" + closedVar + ") {");
	io.println(indent + "    jjtree.clearNodeScope(" + nodeVar + ");");
	io.println(indent + "    " + closedVar + " = false;");
	io.println(indent + "  } else {");
	io.println(indent + "    jjtree.popNode();");
	io.println(indent + "  }");
      }

      while (thrown_names.hasMoreElements()) {
	thrown = (String)thrown_names.nextElement();
	io.println(indent + "  if (" + exceptionVar + " instanceof " +
		   thrown + ") {");
	io.println(indent + "    throw (" + thrown + ")" + exceptionVar + ";");
	io.println(indent + "  }");
      }
      /* This is either an Error or an undeclared Exception.  If it's
         an Error then the cast is good, otherwise we want to force
         the user to declare it by crashing on the bad cast. */
      io.println(indent + "  throw (Error)" + exceptionVar + ";");
    }

  }


  void tryTokenSequence(IO io, String indent, Token first, Token last)
  {
    io.println(indent + "try {");
    SimpleNode.closeJJTreeComment(io);

    /* Print out all the tokens, converting all references to
       `jjtThis' into the current node variable. */
    for (Token t = first; t != last.next; t = t.next) {
      TokenUtils.print(t, io, "jjtThis", nodeVar);
    }

    SimpleNode.openJJTreeComment(io, null);
    io.println();

    Enumeration thrown_names = production.throws_list.elements();
    insertCatchBlocks(io, thrown_names, indent);

    io.println(indent + "} finally {");
    if (usesCloseNodeVar()) {
      io.println(indent + "  if (" + closedVar + ") {");
      insertCloseNodeCode(io, indent + "    ", true);
      io.println(indent + "  }");
    }
    io.println(indent + "}");
    SimpleNode.closeJJTreeComment(io);
  }


  private static void findThrown(Hashtable thrown_set,
				 SimpleNode expansion_unit)
  {
    if (expansion_unit instanceof ASTBNFNonTerminal) {
      /* Should really make the nonterminal explicitly maintain its
         name. */
      String nt = expansion_unit.getFirstToken().image;
      ASTProduction prod = (ASTProduction)JJTreeGlobals.productions.get(nt);
      if (prod != null) {
	Enumeration e = prod.throws_list.elements();
	while (e.hasMoreElements()) {
	  String t = (String)e.nextElement();
	  thrown_set.put(t, t);
	}
      }
    }
    for (int i = 0; i < expansion_unit.jjtGetNumChildren(); ++i) {
      SimpleNode n = (SimpleNode)expansion_unit.jjtGetChild(i);
      findThrown(thrown_set, n);
    }
  }


  void tryExpansionUnit(IO io, String indent, SimpleNode expansion_unit)
  {
    io.println(indent + "try {");
    SimpleNode.closeJJTreeComment(io);

    expansion_unit.print(io);

    SimpleNode.openJJTreeComment(io, null);
    io.println();

    Hashtable thrown_set = new Hashtable();
    findThrown(thrown_set, expansion_unit);
    Enumeration thrown_names = thrown_set.elements();
    insertCatchBlocks(io, thrown_names, indent);

    io.println(indent + "} finally {");
    if (usesCloseNodeVar()) {
      io.println(indent + "  if (" + closedVar + ") {");
      insertCloseNodeCode(io, indent + "    ", true);
      io.println(indent + "  }");
    }
    io.println(indent + "}");
    SimpleNode.closeJJTreeComment(io);
  }


  static NodeScope getEnclosingNodeScope(Node node)
  {
    if (node instanceof ASTBNFDeclaration) {
      return ((ASTBNFDeclaration)node).node_scope;
    }
    for (Node n = node.jjtGetParent(); n != null; n = n.jjtGetParent()) {
      if (n instanceof ASTBNFDeclaration) {
	return ((ASTBNFDeclaration)n).node_scope;
      } else if (n instanceof ASTBNFNodeScope) {
	return ((ASTBNFNodeScope)n).node_scope;
      } else if (n instanceof ASTExpansionNodeScope) {
	return ((ASTExpansionNodeScope)n).node_scope;
      }
    }
    return null;
  }

}

/*end*/
