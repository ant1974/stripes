package net.sourceforge.stripes.tag;

import net.sourceforge.stripes.exception.StripesJspException;
import net.sourceforge.stripes.util.Log;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.el.ELException;
import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.Tag;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides basic facilities for any tag that wishes to mimic a standard HTML/XHTML tag. Includes
 * getters and setters for all basic HTML attributes and JavaScript event attributes.  Also includes
 * several of the support methods from the Tag interface, but does not directly or indirectly
 * implement either Tag or BodyTag.
 *
 * @author Tim Fennell
 */
public class HtmlTagSupport {
    /** Log implementation used to log errors during tag writing. */
    private final Log log = Log.getInstance(HtmlTagSupport.class);

    /** Map containing all attributes of the tag. */
    private final Map<String,String> attributes = new HashMap<String,String>();

    /** Map containging the results of the EL expression evaluations of the attributes. */
    private final Map<String,Object> elAttributes = new HashMap<String,Object>();

    /** Storage for a PageContext during evaluation. */
    private PageContext pageContext;

    /** Storage for the parent tag of this tag. */
    private Tag parentTag;

    /** Storage for a BodyContent instance, should the eventual child class implement BodyTag. */
    private BodyContent bodyContent;

    /** Sets the named attribute to the supplied value. */
    protected final void set(String name, String value) {
        this.attributes.put(name, value);
    }

    /** Gets the value of the named attribute, or null if it is not set. */
    protected final String get(String name) {
        return this.attributes.get(name);
    }

    /** Gets the map containing the attributes of the tag. */
    protected final Map<String,String> getAttributes() {
        return this.attributes;
    }

    /** Called by the Servlet container to set the page context on the tag. */
    public void setPageContext(PageContext pageContext) {
        this.pageContext = pageContext;
    }

    /** Retrieves the pageContext handed to the tag by the container. */
    public PageContext getPageContext() {
        return this.pageContext;
    }

    /** From the Tag interface - allows the container to set the parent tag on the JSP. */
    public void setParent(Tag tag) {
        this.parentTag = tag;
    }

    /** From the Tag interface - allows fetching the parent tag on the JSP. */
    public Tag getParent() {
        return this.parentTag;
    }

    /** Returns the BodyContent of the tag if one has been provided by the JSP container. */
    public BodyContent getBodyContent() {
        return bodyContent;
    }

    /** Called by the JSP container to set the BodyContent on the tag. */
    public void setBodyContent(BodyContent bodyContent) {
        this.bodyContent = bodyContent;
    }

    /** Release method to clean up the state of the tag ready for re-use. */
    public void release() {
        this.pageContext = null;
        this.parentTag = null;
        this.bodyContent = null;
        this.attributes.clear();
        this.elAttributes.clear();
    }

    /** Fetches the attributes map that has been constructed after EL expression evaluation. */
    protected Map<String,Object> getElAttributes() {
        return this.elAttributes;
    }

    /**
     * Checks to see if there is a body content for this tag, and if its value is non-null
     * and non-zero-length.  If so, returns it as a String, otherwise returns null.

     * @return String the value of the body if one was set
     */
    protected String getBodyContentAsString() {
        String returnValue = null;

        if (this.bodyContent != null) {
            String body = getBodyContent().getString();

            if (body != null && body.length() > 0) {
                returnValue = body;
            }
        }

        return returnValue;
    }

    protected void writeOpenTag(JspWriter writer, String tag) throws JspException {
        try {
            writer.print("<");
            writer.print(tag);
            writeAttributes(writer);
            writer.print(">");
            writer.println();
        }
        catch (IOException ioe) {
            JspException jspe = new JspException("IOException encountered while writing open tag <" +
                tag + "> to the JspWriter.", ioe);
            log.warn(jspe);
            throw jspe;
        }
    }

    protected void writeCloseTag(JspWriter writer, String tag) throws JspException {
        try {
            writer.print("</");
            writer.print(tag);
            writer.print(">");
            writer.println();
        }
        catch (IOException ioe) {
            JspException jspe = new JspException("IOException encountered while writing close tag </" +
                tag + "> to the JspWriter.", ioe);
            log.warn(jspe);
            throw jspe;
        }
    }

    protected void writeSingletonTag(JspWriter writer, String tag) throws JspException{
        try {
            writer.print("<");
            writer.print(tag);
            writeAttributes(writer);
            writer.print("/>");
            writer.println();
        }
        catch (IOException ioe) {
            JspException jspe = new JspException("IOException encountered while writing singleton tag <" +
                tag + "/> to the JspWriter.", ioe);
            log.warn(jspe);
            throw jspe;
        }
    }

    protected void writeAttributes(JspWriter writer) throws IOException {
        for (Map.Entry<String,Object> attr: getElAttributes().entrySet() ) {
            writer.print(" ");
            writer.print(attr.getKey());
            writer.print("=\"");
            writer.print(attr.getValue());
            writer.print("\"");
        }
    }


    /**
     * Uses the container's built in EL support to evaluate all the attributes set on the tag. The
     * newly evaluated values are then used to replace the original values input by the user. It is
     * expected that most tags will call this as the first act in doStartTag in order to translate
     * the values before any further processing is done.
     *
     * @throws StripesJspException when an ELException occurs trying to evaluate an attribute
     */
    protected void evaluateExpressions() throws StripesJspException {
        ExpressionEvaluator evaluator     = this.pageContext.getExpressionEvaluator();
        VariableResolver variableResolver = this.pageContext.getVariableResolver();

        for (Map.Entry<String,String> entry : this.attributes.entrySet()) {
            try {
                Object result = evaluator.evaluate(entry.getValue(),
                                                   Object.class,
                                                   variableResolver,
                                                   null);
                this.elAttributes.put(entry.getKey(), result);
            }
            catch (ELException ele) {
                throw new StripesJspException
                    ("Could not evaluate EL expression for tag attribute [" + entry.getKey() +
                     "] with value[" + entry.getValue() + "] in class of type: " +
                     getClass().getName(), ele);
            }
        }
    }

    /**
     * Evaluates a single expression and returns the result.  If the expression cannot be evaluated
     * then an ELException is caught, wrapped in a JspException and re-thrown.
     *
     * @param expression the expression to be evaluated
     * @param resultType the Class representing the desired return type from the expression
     * @throws StripesJspException when an ELException occurs trying to evaluate the expression
     */
    protected <R> R evaluateExpression(String expression, Class<R> resultType) throws StripesJspException {
        try {
            return (R) this.pageContext.getExpressionEvaluator().
                evaluate(expression, resultType, this.pageContext.getVariableResolver(), null);
        }
        catch (ELException ele) {
            throw new StripesJspException
                ("Could not evaluate EL expression  [" + expression + "] with result type [" +
                    resultType.getName() + "] in tag class of type: " + getClass().getName(), ele);
        }
    }

    /**
     * <p>Locates the enclosing tag of the type supplied.  If no enclosing tag of the type supplied
     * can be found anywhere in the ancestry of this tag, null is returned..</p>
     *
     * @return T Tag of the type supplied, or null if none can be found
     */
    protected <T extends Tag> T getParentTag(Class<T> tagType) {
        Tag parent = getParent();
        while (parent != null && !tagType.isAssignableFrom(parent.getClass())) {
            parent = parent.getParent();
        }

        return parent==null ? null : (T) parent;
    }


    /**
     * Returns a String representation of the class, including the map of attributes that
     * are set on the tag, the toString of its parent tag, and the pageContext.
     */
    public String toString() {
        return getClass().getSimpleName()+ "{" +
            "attributes=" + attributes +
            "elAttributes=" + elAttributes +
            ", parentTag=" + parentTag +
            ", pageContext=" + pageContext +
            "}";
    }


    public void setId(String id) { set("id", id); }
    public String getId() { return get("id"); }

    public void setClass(String cssClass) { set("class", cssClass); }
    public void setCssClass(String cssClass) { set("class", cssClass); }
    public String getCssClass() { return get(" class"); }

    public void setTitle(String  title) { set("title",  title); }
    public String getTitle() { return get("title"); }

    public void setStyle(String  style) { set("style",  style); }
    public String getStyle() { return get("style"); }

    public void setDir(String  dir) { set("dir",  dir); }
    public String getDir() { return get("dir"); }

    public void setLang(String  lang) { set("lang",  lang); }
    public String getLang() { return get("lang"); }

    public void setTabindex(String  tabindex) { set("tabindex",  tabindex); }
    public String getTabindex() { return get("tabindex"); }

    public void setAccesskey(String  accesskey) { set("accesskey",  accesskey); }
    public String getAccesskey() { return get("accesskey"); }

    public void setOnfocus(String  onfocus) { set("onfocus",  onfocus); }
    public String getOnfocus() { return get("onfocus"); }

    public void setOnblur(String  onblur) { set("onblur",  onblur); }
    public String getOnblur() { return get("onblur"); }

    public void setOnselect(String  onselect) { set("onselect",  onselect); }
    public String getOnselect() { return get("onselect"); }

    public void setOnchange(String  onchange) { set("onchange",  onchange); }
    public String getOnchange() { return get("onchange"); }

    public void setOnclick(String  onclick) { set("onclick",  onclick); }
    public String getOnclick() { return get("onclick"); }

    public void setOndblclick(String  ondblclick) { set("ondblclick",  ondblclick); }
    public String getOndblclick() { return get("ondblclick"); }

    public void setOnmousedown(String  onmousedown) { set("onmousedown",  onmousedown); }
    public String getOnmousedown() { return get("onmousedown"); }

    public void setOnmouseup(String  onmouseup) { set("onmouseup",  onmouseup); }
    public String getOnmouseup() { return get("onmouseup"); }

    public void setOnmouseover(String  onmouseover) { set("onmouseover",  onmouseover); }
    public String getOnmouseover() { return get("onmouseover"); }

    public void setOnmousemove(String  onmousemove) { set("onmousemove",  onmousemove); }
    public String getOnmousemove() { return get("onmousemove"); }

    public void setOnmouseout(String  onmouseout) { set("onmouseout",  onmouseout); }
    public String getOnmouseout() { return get("onmouseout"); }

    public void setOnkeypress(String  onkeypress) { set("onkeypress",  onkeypress); }
    public String getOnkeypress() { return get("onkeypress"); }

    public void setOnkeydown(String  onkeydown) { set("onkeydown",  onkeydown); }
    public String getOnkeydown() { return get("onkeydown"); }

    public void setOnkeyup(String  onkeyup) { set("onkeyup",  onkeyup); }
    public String getOnkeyup() { return get("onkeyup"); }
}
