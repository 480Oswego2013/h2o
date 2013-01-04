package water.util;

import hex.rf.*;

import java.io.File;
import java.io.PrintWriter;

import org.apache.commons.codec.binary.Base64;

import water.*;
import water.ValueArray.Column;
import water.web.RString;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

public class TreeRenderer {
  private static final String DOT_PATH;
  static {
    File f = new File("/usr/local/bin/dot");
    if( !f.exists() ) f = new File("/usr/bin/dot");
    if( !f.exists() ) f = new File("C:\\Program Files (x86)\\Graphviz 2.28\\bin\\dot.exe");
    DOT_PATH = f.exists() ? f.getAbsolutePath() : null;
  }

  private final String[] _domain;
  private final Column[] _columns;
  private final byte[] _treeBits;
  private final int _nodeCount;

  public TreeRenderer(Model model, int treeNum, ValueArray ary, int classCol) {
    _treeBits = model.tree(treeNum);
    _columns = ary._cols;
    _domain = _columns[classCol]._domain;

    long dl = Tree.depth_leaves(new AutoBuffer(_treeBits));
    int leaves= (int)(dl&0xFFFFFFFFL);
    _nodeCount = leaves*2-1;
  }

  public String code() {
    if( _nodeCount > 10000 )
      return "<div class='alert'>Tree is too large to print psuedo code</div>";
    try {
      StringBuilder sb = new StringBuilder();
      sb.append("<pre><code>");
      new CodeTreePrinter(sb, _columns, _domain).walk_serialized_tree(new AutoBuffer(_treeBits));
      sb.append("</code></pre>");
      return sb.toString();
    } catch( Exception e ) {
      return errorRender(e);
    }
  }

  public String graphviz() {
    if( DOT_PATH == null )
      return "<div class='alert'>Install <a href=\"http://www.graphviz.org/\">graphviz</a> to " +
      "see visualizations of small trees</div>";
    if( _nodeCount > 1000 )
      return "<div class='alert'>Tree is too large to graph.</div>";

    try {
      RString img = new RString("<img src=\"data:image/svg+xml;base64,%rawImage\" width='80%%' ></img><p>");
      Process exec = Runtime.getRuntime().exec(new String[] { DOT_PATH, "-Tsvg" });
      new GraphvizTreePrinter(exec.getOutputStream(), _columns, _domain).walk_serialized_tree(new AutoBuffer(_treeBits));
      exec.getOutputStream().close();
      byte[] data = ByteStreams.toByteArray(exec.getInputStream());
      img.replace("rawImage", new String(Base64.encodeBase64(data), "UTF-8"));
      return img.toString();
    } catch( Exception e ) {
      return errorRender(e);
    }
  }

  private String errorRender(Exception e) {
    StringBuilder sb = new StringBuilder();
    sb.append("<div class='alert alert-error'>");
    sb.append("Error Generating Dot file:\n<pre>");
    e.printStackTrace(new PrintWriter(CharStreams.asWriter(sb)));
    sb.append("</pre>");
    sb.append("</div>");
    return sb.toString();
  }
}
