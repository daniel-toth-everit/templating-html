/**
 * This file is part of Everit - HTML Templating.
 *
 * Everit - HTML Templating is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - HTML Templating is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - HTML Templating.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.templating.html.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.everit.expression.CompiledExpression;
import org.everit.templating.html.internal.util.HTMLTemplatingUtil;
import org.everit.templating.util.TemplateWriter;
import org.everit.templating.util.UniversalIterable;
import org.htmlparser.Tag;
import org.htmlparser.lexer.Page;
import org.htmlparser.lexer.PageAttribute;

public class TagNode extends ParentNode {

    private class ForeachItem {
        /**
         * Either an array or an iterable.
         */
        public Object collection;

        public String indexVarName;

        public String valueVarName;
    }

    private class TagAttributeRenderContext {

        public final Map<String, Object> appendValueMap;

        public final Map<String, Object> prependValueMap;

        public final TemplateContextImpl templateContext;

        public final Map<String, Object> valueMap;

        public TagAttributeRenderContext(final TemplateContextImpl templateContext) {

            @SuppressWarnings("unchecked")
            Map<String, Object> lam = evaluateExpression(attributeMapExpressionHolder, templateContext, Map.class);
            valueMap = createWrapperMap(lam);

            @SuppressWarnings("unchecked")
            Map<String, Object> lapm = evaluateExpression(attributePrependMapExpressionHolder, templateContext,
                    Map.class);
            prependValueMap = createWrapperMap(lapm);

            @SuppressWarnings("unchecked")
            Map<String, Object> laam = evaluateExpression(attributeAppendMapExpressionHolder, templateContext,
                    Map.class);
            appendValueMap = createWrapperMap(laam);

            this.templateContext = templateContext;
        }

        private Map<String, Object> createWrapperMap(final Map<String, Object> wrapped) {
            if (wrapped == null) {
                return new HashMap<String, Object>();
            }
            return new HashMap<String, Object>(wrapped);
        }
    }

    private CompiledExpressionHolder attributeAppendMapExpressionHolder;

    private CompiledExpressionHolder attributeMapExpressionHolder;

    private CompiledExpressionHolder attributePrependMapExpressionHolder;

    private CompiledExpressionHolder codeExpressionHolder = null;

    private final boolean emptyTag;

    private String endTag = null;

    private boolean escapeText = false;

    private CompiledExpressionHolder foreachExpressionHolder = null;

    private final Map<String, RenderableAttribute> renderableAttributes = new LinkedHashMap<String, RenderableAttribute>();

    /**
     * Defaults to all.
     */
    private CompiledExpressionHolder renderExpressionHolder = null;

    private final int startPositionInTemplate;

    private String tagName;

    private CompiledExpressionHolder textExpressionHolder = null;

    private final char[] textRepresentation;

    private CompiledExpressionHolder varExpressionHolder = null;

    public TagNode(final Tag tag) {
        this.emptyTag = tag.isEmptyXmlTag();
        Page page = tag.getPage();
        startPositionInTemplate = tag.getStartPosition();
        int endPosition = tag.getEndPosition();
        int length = endPosition - startPositionInTemplate;
        textRepresentation = new char[length];
        page.getText(textRepresentation, 0, startPositionInTemplate, endPosition);

    }

    private void assignForEachVariables(final Map<String, Object> vars, final ForeachItem item, final int index,
            final Object value) {
        vars.put(item.valueVarName, value);
        if (item.indexVarName != null) {
            vars.put(item.indexVarName, index);
        }
    }

    private void evaluateCode(final TemplateContextImpl templateContext) {
        if (codeExpressionHolder == null) {
            return;
        }

        codeExpressionHolder.compiledExpression.eval(templateContext.getVars());
    }

    private <R> R evaluateExpression(final CompiledExpressionHolder expressionHolder,
            final TemplateContextImpl templateContext, final Class<R> clazz) {
        if (expressionHolder == null) {
            return null;
        }

        Object result = expressionHolder.compiledExpression.eval(templateContext.getVars());
        if (result == null) {
            return null;
        }

        if (!clazz.isAssignableFrom(result.getClass())) {
            throw new RenderException("The result type " + result.getClass()
                    + " cannot be assigned to the expected type " + clazz.getName(), expressionHolder.attributeInfo);
        }

        @SuppressWarnings("unchecked")
        R typedResult = (R) result;
        return typedResult;

    }

    private Map<Object, Object> evaluateForeachMap(final TemplateContextImpl templateContext) {
        if (foreachExpressionHolder == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> result = evaluateExpression(foreachExpressionHolder, templateContext, Map.class);

        return result;
    }

    private RenderScope evaluateRender(final TemplateContextImpl templateContext) {
        Object renderValue = evaluateExpression(renderExpressionHolder, templateContext, Object.class);
        if (renderValue == null) {
            return RenderScope.ALL;
        }

        if (renderValue instanceof Boolean) {
            if ((Boolean) renderValue) {
                return RenderScope.ALL;
            } else {
                return RenderScope.NONE;
            }
        }

        if (renderValue instanceof RenderScope) {
            return (RenderScope) renderValue;
        }

        String renderString;
        if (!(renderValue instanceof String)) {
            renderString = String.valueOf(renderValue);
        } else {
            renderString = (String) renderValue;
        }

        if (renderString.equalsIgnoreCase(RenderScope.ALL.toString())) {
            return RenderScope.ALL;
        }
        if (renderString.equalsIgnoreCase(RenderScope.NONE.toString())) {
            return RenderScope.NONE;
        }
        if (renderString.equalsIgnoreCase(RenderScope.CONTENT.toString())) {
            return RenderScope.CONTENT;
        }
        if (renderString.equalsIgnoreCase(RenderScope.TAG.toString())) {
            return RenderScope.TAG;
        }
        throw new RenderException("Unrecognized value for render attribute: " + renderString,
                renderExpressionHolder.attributeInfo);
    }

    private Map<String, Object> evaluateTagVariables(final TemplateContextImpl templateContext) {
        if (varExpressionHolder == null) {
            return null;
        }
        CompiledExpression compiledExpression = varExpressionHolder.compiledExpression;
        Object result = compiledExpression.eval(templateContext.getVars());
        if (result == null) {
            return null;
        }
        if (!(result instanceof Map)) {
            throw new RenderException("Unrecognized type at attribute override map: " + result.getClass(),
                    varExpressionHolder.attributeInfo);
        }
        @SuppressWarnings("unchecked")
        Map<Object, Object> tagVarMap = (Map<Object, Object>) result;

        for (Object key : tagVarMap.keySet()) {
            if (key == null || !(key instanceof String)) {
                throw new RenderException("Attribute override Map should have only String keys: " + key,
                        varExpressionHolder.attributeInfo);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mapResult = (Map<String, Object>) result;
        return mapResult;
    }

    private String evaluateText(final Map<String, Object> vars) {
        if (textExpressionHolder == null) {
            return null;
        } else {
            Object text = textExpressionHolder.compiledExpression.eval(vars);
            if (text == null) {
                return "";
            }
            String textString = text.toString();
            if (escapeText) {
                textString = HTMLTemplatingUtil.escape(textString);
            }
            return textString;

        }
    }

    public CompiledExpressionHolder getAttributeAppendMapExpressionHolder() {
        return attributeAppendMapExpressionHolder;
    }

    public CompiledExpressionHolder getAttributeMapExpressionHolder() {
        return attributeMapExpressionHolder;
    }

    public CompiledExpressionHolder getAttributePrependMapExpressionHolder() {
        return attributePrependMapExpressionHolder;
    }

    public CompiledExpressionHolder getCodeExpressionHolder() {
        return codeExpressionHolder;
    }

    public CompiledExpressionHolder getForeachExpressionHolder() {
        return foreachExpressionHolder;
    }

    public Map<String, RenderableAttribute> getRenderableAttributes() {
        return renderableAttributes;
    }

    public CompiledExpressionHolder getRenderExpressionHolder() {
        return renderExpressionHolder;
    }

    public String getTagName() {
        return tagName;
    }

    public CompiledExpressionHolder getTextExpressionHolder() {
        return textExpressionHolder;
    }

    public CompiledExpressionHolder getVarExpressionHolder() {
        return varExpressionHolder;
    }

    public boolean isEscapeText() {
        return escapeText;
    }

    @Override
    public void render(final TemplateContextImpl templateContext) {
        final Map<Object, Object> foreachMap = evaluateForeachMap(templateContext);

        if (foreachExpressionHolder != null && (foreachMap == null || foreachMap.size() == 0)) {
            return;
        }

        if (foreachMap != null) {
            renderEach(templateContext, foreachMap);
        } else {
            renderItem(templateContext);
        }
    }

    private void renderAttribute(final TemplateWriter writer, final String attributeName,
            final RenderableAttribute renderableAttribute, final TagAttributeRenderContext actx) {
        String attributeValue = renderableAttribute.getConstantValue();

        if (actx.valueMap != null && actx.valueMap.containsKey(attributeName)) {
            Object attributeValueObject = actx.valueMap.remove(attributeName);
            attributeValue = (attributeValueObject != null) ? attributeValueObject.toString() : null;
        } else {
            CompiledExpressionHolder expressionHolder = renderableAttribute.getExpressionHolder();
            if (expressionHolder != null) {
                Object attributeValueObject = evaluateExpression(expressionHolder, actx.templateContext,
                        Object.class);
                attributeValue = (attributeValueObject != null) ? attributeValueObject.toString() : null;
            }
        }

        String prependText = resolveXPend(attributeName, actx.prependValueMap,
                renderableAttribute.getPrependExpressionHolder(), actx.templateContext);
        if (prependText != null) {
            attributeValue = prependText + ((attributeValue != null) ? attributeValue : "");
        }

        String appendText = resolveXPend(attributeName, actx.appendValueMap,
                renderableAttribute.getAppendExpressionHolder(), actx.templateContext);
        if (appendText != null) {
            attributeValue = ((attributeValue != null) ? attributeValue : "") + appendText;
        }

        if (attributeValue != null) {
            String previousText = renderableAttribute.getPreviousText();
            if (previousText != null) {
                writer.append(previousText);
            }

            writer.append(attributeName);

            PageAttribute pageAttribute = renderableAttribute.getPageAttribute();
            if (pageAttribute == null) {
                pageAttribute = renderableAttribute.getExpressionPageAttribute();
                if (pageAttribute == null) {
                    pageAttribute = renderableAttribute.getPrependPageAttribute();
                }
                if (pageAttribute == null) {
                    pageAttribute = renderableAttribute.getAppendPageAttribute();
                }
            }

            String assigment = "=";
            char quote = '"';
            if (pageAttribute != null) {
                assigment = pageAttribute.getAssignment();
                quote = pageAttribute.getQuote();
            }
            String quoteString = String.valueOf(quote);
            writer.append(assigment).append(quoteString).append(HTMLTemplatingUtil.escape(attributeValue))
                    .append(quoteString);
        }
    }

    private void renderContent(final TemplateContextImpl templateContext) {
        RenderScope render = evaluateRender(templateContext);
        if (render == RenderScope.NONE) {
            return;
        }

        String text = null;
        if (render == RenderScope.ALL || render == RenderScope.CONTENT) {
            text = evaluateText(templateContext.getVars());
        }

        if (render == RenderScope.ALL || render == RenderScope.TAG) {
            renderTag(templateContext, text, render == RenderScope.ALL);
        } else {

            if (text != null) {
                templateContext.getWriter().append(text);
            } else {
                renderChildren(templateContext);
            }
        }
    }

    private void renderEach(final TemplateContextImpl templateContext,
            final Map<Object, Object> foreachMap) {

        Set<Entry<Object, Object>> entrySet = foreachMap.entrySet();

        ForeachItem[] items = new ForeachItem[foreachMap.size()];

        int i = 0;
        for (Entry<Object, Object> entry : entrySet) {

            Object key = entry.getKey();
            if (key == null) {
                throw new RenderException("Null key in the foreach map: " + foreachMap.toString(),
                        foreachExpressionHolder.attributeInfo);
            }

            Object value = entry.getValue();

            if (value == null) {
                return;
            }

            if (!value.getClass().isArray() && !(value instanceof Iterable)) {
                throw new RenderException("Unrecognized value type in foreach map: " + value.getClass(),
                        foreachExpressionHolder.attributeInfo);
            }

            String valueVarName = null;
            String indexVarName = null;

            if (key instanceof String) {
                valueVarName = (String) key;
            } else if (key instanceof Object[]) {
                Object[] foreachKeyObjArray = (Object[]) key;
                if (foreachKeyObjArray.length == 0 || foreachKeyObjArray.length > 2) {
                    throw new RenderException("Foreach key should contain one or two elements: "
                            + Arrays.toString(foreachKeyObjArray),
                            foreachExpressionHolder.attributeInfo);
                }
                valueVarName = String.valueOf(foreachKeyObjArray[0]);
                if (foreachKeyObjArray.length == 2) {
                    indexVarName = String.valueOf(foreachKeyObjArray[1]);
                }
            } else {
                throw new RenderException("Unrecognized type for foreach key: " + key.getClass(),
                        foreachExpressionHolder.attributeInfo);
            }
            ForeachItem item = new ForeachItem();
            item.collection = value;
            item.indexVarName = indexVarName;
            item.valueVarName = valueVarName;
            items[i] = item;
            i++;
        }

        renderEachRecurse(templateContext, items, 0);

    }

    private void renderEachRecurse(final TemplateContextImpl templateContext,
            final ForeachItem[] items,
            final int mapEntryIndex) {

        if (mapEntryIndex == items.length) {
            renderItem(templateContext);
        } else {
            ForeachItem item = items[mapEntryIndex];
            Object collectionObject = item.collection;
            Map<String, Object> vars = templateContext.getVars();

            UniversalIterable<Object> iterable = new UniversalIterable<Object>(collectionObject);

            Iterator<?> iterator = iterable.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                Object value = iterator.next();
                assignForEachVariables(vars, item, i, value);
                renderEachRecurse(templateContext, items, mapEntryIndex + 1);
                i++;
            }
        }
    }

    private void renderItem(final TemplateContextImpl templateContext) {
        evaluateCode(templateContext);
        final Map<String, Object> tagVars = evaluateTagVariables(templateContext);

        if (tagVars != null && tagVars.size() > 0) {
            templateContext.getVars().putAll(tagVars);
            renderContent(templateContext);
        } else {
            renderContent(templateContext);
        }

    }

    private void renderRemainingAttribute(final TemplateContextImpl templateContext, final String attributeName,
            final Object prepend,
            final Object attributeValue, final Object append) {

        TemplateWriter writer = templateContext.getWriter();

        if (attributeName == null || (prepend == null && attributeValue == null && append == null)) {
            return;
        }
        writer.append(" ").append(attributeName).append("=\"");

        StringBuilder attributeValueSB = new StringBuilder();

        if (prepend != null) {
            attributeValueSB.append(prepend);
        }
        if (attributeValue != null) {
            attributeValueSB.append(attributeValue);
        }
        if (append != null) {
            attributeValueSB.append(append);
        }
        writer.append(HTMLTemplatingUtil.escape(attributeValueSB.toString()));

        writer.append("\"");

    }

    /**
     * Render those attributes that are not listed directly but are available in the value, prepend or append map. The
     * attributes are appended by adding a space in front.
     *
     * @param sb
     *            The stringBuilder or the rendering process.
     * @param vars
     *            The context variables.
     * @param attributeCtx
     *            The context of the tag attributes.
     */
    private void renderRemainingAttributesFromMaps(final TemplateContextImpl templateContext,
            final TagAttributeRenderContext attributeCtx) {

        Map<String, Object> valueMap = attributeCtx.valueMap;
        Map<String, Object> prependMap = attributeCtx.prependValueMap;
        Map<String, Object> appendMap = attributeCtx.appendValueMap;

        Iterator<Entry<String, Object>> iterator = valueMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, Object> entry = iterator.next();
            iterator.remove();
            String attributeName = entry.getKey();
            Object attributeValue = entry.getValue();
            renderRemainingAttribute(templateContext, attributeName, prependMap.remove(attributeName), attributeValue,
                    appendMap.remove(attributeName));
        }

        iterator = prependMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, Object> entry = iterator.next();
            iterator.remove();
            String attributeName = entry.getKey();
            Object prepend = entry.getValue();

            renderRemainingAttribute(templateContext, attributeName, prepend, null, appendMap.remove(attributeName));
        }

        iterator = appendMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, Object> entry = iterator.next();
            iterator.remove();
            String attributeName = entry.getKey();
            Object append = entry.getValue();

            renderRemainingAttribute(templateContext, attributeName, null, null, append);
        }
    }

    private void renderTag(final TemplateContextImpl templateContext, final String text,
            final boolean renderBody) {
        TemplateWriter writer = templateContext.getWriter();
        writer.append("<").append(tagName);

        TagAttributeRenderContext attributeCtx = new TagAttributeRenderContext(templateContext);

        Iterator<Entry<String, RenderableAttribute>> iterator = renderableAttributes.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, RenderableAttribute> entry = iterator.next();
            String attributeName = entry.getKey();
            RenderableAttribute renderableAttribute = entry.getValue();
            renderAttribute(templateContext.getWriter(), attributeName, renderableAttribute, attributeCtx);
        }

        renderRemainingAttributesFromMaps(templateContext, attributeCtx);

        if (!renderBody || (text == null && getChildren().size() == 0)) {
            if (endTag != null) {
                writer.append(">").append(endTag);
            } else {
                if (emptyTag) {
                    writer.append(" /");
                }
                writer.append(">");
            }
        } else {
            writer.append(">");
            if (text != null) {
                writer.append(text);
            } else {
                renderChildren(templateContext);
            }
            if (emptyTag) {
                writer.append("</").append(tagName).append(">");
            } else if (endTag != null) {
                writer.append(endTag);
            }
        }

    }

    private String resolveXPend(final String attributeName, final Map<String, Object> xpendValueMap,
            final CompiledExpressionHolder xpendExpressionHolder, final TemplateContextImpl templateContext) {

        String xpend = null;

        if (xpendValueMap != null && xpendValueMap.containsKey(attributeName)) {
            Object xpendObject = xpendValueMap.remove(attributeName);
            xpend = (xpendObject != null) ? xpendObject.toString() : null;
        } else if (xpendExpressionHolder != null) {
            Object xpendObject = evaluateExpression(xpendExpressionHolder, templateContext, Object.class);
            xpend = (xpendObject != null) ? xpendObject.toString() : null;
        }
        return xpend;
    }

    public void setAttributeAppendMapExpressionHolder(final CompiledExpressionHolder attributeAppendMapExpression) {
        this.attributeAppendMapExpressionHolder = attributeAppendMapExpression;
    }

    public void setAttributeMapExpressionHolder(final CompiledExpressionHolder attributeMapExpression) {
        this.attributeMapExpressionHolder = attributeMapExpression;
    }

    public void setAttributePrependMapExpressionHolder(final CompiledExpressionHolder attributePrependMapExpression) {
        this.attributePrependMapExpressionHolder = attributePrependMapExpression;
    }

    public void setCodeExpressionHolder(final CompiledExpressionHolder codeExpressionHolder) {
        this.codeExpressionHolder = codeExpressionHolder;
    }

    public void setEndTag(final String endTag) {
        this.endTag = endTag;
    }

    public void setEscapeText(final boolean unescapeText) {
        this.escapeText = unescapeText;
    }

    public void setForeachExpressionHolder(final CompiledExpressionHolder foreachExpressionHolder) {
        this.foreachExpressionHolder = foreachExpressionHolder;
    }

    public void setRenderExpressionHolder(final CompiledExpressionHolder renderExpressionHolder) {
        this.renderExpressionHolder = renderExpressionHolder;
    }

    public void setTagName(final String tagName) {
        this.tagName = tagName;
    }

    public void setTextExpressionHolder(final CompiledExpressionHolder textExpressionHolder) {
        this.textExpressionHolder = textExpressionHolder;
    }

    public void setVarExpressionHolder(final CompiledExpressionHolder varExpressionHolder) {
        this.varExpressionHolder = varExpressionHolder;
    }
}
