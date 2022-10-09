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

import java.util.Hashtable;
import java.util.Vector;

import org.javacc.parser.JavaCCGlobals;

public class JJTree {

  private IO io;

  private void p(String s)
  {
    io.getMsg().println(s);
  }

  private void help_message()
  {
    p("Usage:");
    p("    jjtree option-settings inputfile");
    p("");
    p("\"option-settings\" is a sequence of settings separated by spaces.");
    p("Each option setting must be of one of the following forms:");
    p("");
    p("    -optionname=value (e.g., -STATIC=false)");
    p("    -optionname:value (e.g., -STATIC:false)");
    p("    -optionname       (equivalent to -optionname=true.  e.g., -STATIC)");
    p("    -NOoptionname     (equivalent to -optionname=false. e.g., -NOSTATIC)");
    p("");
    p("Option settings are not case-sensitive, so one can say \"-nOsTaTiC\" instead");
    p("of \"-NOSTATIC\".  Option values must be appropriate for the corresponding");
    p("option, and must be either an integer or a string value.");
    p("");

    p("The boolean valued options are:");
    p("");
    p("    STATIC                 (default true)");
    p("    MULTI                  (default false)");
    p("    NODE_DEFAULT_VOID      (default false)");
    p("    NODE_SCOPE_HOOK        (default false)");
    p("    NODE_FACTORY           (default false)");
    p("    NODE_USES_PARSER       (default false)");
    p("    BUILD_NODE_FILES       (default true)");
    p("    VISITOR                (default false)");
    p("");
    p("The string valued options are:");
    p("");
    p("    JDK_VERSION            (default \"1.4\")");
    p("    NODE_PREFIX            (default \"AST\")");
    p("    NODE_PACKAGE           (default \"\")");
    p("    NODE_EXTENDS           (default \"\")");
    p("    OUTPUT_FILE            (default remove input file suffix, add .jj)");
    p("    OUTPUT_DIRECTORY       (default \"\")");
    p("    VISITOR_EXCEPTION      (default \"\")");
    p("");
    p("JJTree also accepts JavaCC options, which it inserts into the generated file.");
    p("");

    p("EXAMPLES:");
    p("    jjtree -STATIC=false mygrammar.jjt");
    p("");
    p("ABOUT JJTree:");
    p("    JJTree is a preprocessor for JavaCC that inserts actions into a");
    p("    JavaCC grammar to build parse trees for the input.");
    p("");
    p("    For more information, ???");
    p("");
  }

  /**
   * A main program that exercises the parser.
   */
  public int main(String args[]) {

    // initialize static state for allowing repeat runs without exiting
    ASTNodeDescriptor.nodeIds = new Vector();
    ASTNodeDescriptor.nodeNames = new Vector();
    ASTNodeDescriptor.nodeSeen = new Hashtable();
    JJTreeGlobals.jjtreeOptions = new Hashtable();
    JJTreeGlobals.toolList = new Vector();
    JJTreeGlobals.parserName = null;
    JJTreeGlobals.packageName = "";
    JJTreeGlobals.parserImplements = null;
    JJTreeGlobals.parserClassBodyStart = null;
    JJTreeGlobals.productions = new Hashtable();
    org.javacc.parser.Main.reInitAll();

    JavaCCGlobals.bannerLine("Tree Builder", "");

    io = new IO();

    try {

      initializeOptions();
      if (args.length == 0) {
	p("");
	help_message();
	return 1;
      } else {
	p("(type \"jjtree\" with no arguments for help)");
      }

      String fn = args[args.length - 1];

      if (JJTreeOptions.isOption(fn)) {
	p("Last argument \"" + fn + "\" is not a filename");
	return 1;
      }
      for (int arg = 0; arg < args.length - 1; arg++) {
	if (!JJTreeOptions.isOption(args[arg])) {
	  p("Argument \"" + args[arg] + "\" must be an option setting.");
	  return 1;
	}
	JJTreeOptions.setCmdLineOption(args[arg]);
      }

      try {
	io.setInput(fn);
      } catch (JJTreeIOException ioe) {
	p("Error setting input: " + ioe.getMessage());
	return 1;
      }
      p("Reading from file " + io.getInputFileName() + " . . .");

      JJTreeGlobals.toolList = JavaCCGlobals.getToolNames(fn);
      JJTreeGlobals.toolList.addElement("JJTree");

      try {
	JJTreeParser parser = new JJTreeParser(io.getIn());
	parser.javacc_input();

	ASTGrammar root = (ASTGrammar)parser.jjtree.rootNode();
	if (Boolean.getBoolean("jjtree-dump")) {
	  root.dump(" ");
	}
	root.generate(io);
	io.getOut().close();

	NodeFiles.generateTreeConstants_java();
	NodeFiles.generateVisitor_java();
	JJTreeState.generateTreeState_java();

	p("Annotated grammar generated successfully in " +
	  io.getOutputFileName());

      } catch (ParseException pe) {
	p("Error parsing input: " + pe.toString());
	return 1;
      } catch (Exception e) {
	p("Error parsing input: " + e.toString());
	e.printStackTrace(io.getMsg());
	return 1;
      }

      return 0;

    } finally {
      io.closeAll();
    }
  }


  /**
   * Initialize for JJTree
   */
  private void initializeOptions() {

    JJTreeOptions.init();

    JJTreeGlobals.jjtreeOptions.put("JDK_VERSION", "1.4");
    JJTreeGlobals.jjtreeOptions.put("MULTI", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("NODE_PREFIX", "AST");
    JJTreeGlobals.jjtreeOptions.put("NODE_PACKAGE", "");
    JJTreeGlobals.jjtreeOptions.put("NODE_EXTENDS", "");
    JJTreeGlobals.jjtreeOptions.put("NODE_STACK_SIZE", new Integer(500));
    JJTreeGlobals.jjtreeOptions.put("NODE_DEFAULT_VOID", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("OUTPUT_FILE", "");
    JJTreeGlobals.jjtreeOptions.put("OUTPUT_DIRECTORY", "");
    JJTreeGlobals.jjtreeOptions.put("CHECK_DEFINITE_NODE", Boolean.TRUE);
    JJTreeGlobals.jjtreeOptions.put("NODE_SCOPE_HOOK", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("NODE_FACTORY", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("NODE_USES_PARSER", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("BUILD_NODE_FILES", Boolean.TRUE);
    JJTreeGlobals.jjtreeOptions.put("VISITOR", Boolean.FALSE);
    JJTreeGlobals.jjtreeOptions.put("VISITOR_EXCEPTION", "");
  }


}

/*end*/
