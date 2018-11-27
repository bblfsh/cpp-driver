package tech.sourced.babelfish;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonGenerationException;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;
import java.util.HashSet;
import java.lang.Comparable;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/// Visitor pattern implementation for the CPP AST. This will write every
/// node in the Json output. Since CDT unfortunately doesnt have something like JDT
/// "structuralPropertiesForType", we must visit ALL the base nodes and check
/// for every derived node because while IASTNode.getChildren will return all the
/// children that derive for IASTNode (including all the ones returned by any method
/// of that node returning an IASTNode-derived object), a lot of nodes also have
/// other methods that return something not derived from IASTNode and this not returned
/// by getChildren. So for all these IASTNode subinterfaces we must call and store the
/// value of those non-IASTNode-returning methods. The most infamous is probably the
/// getOperator of some nodes that return an int than then you have to match in a switch
/// because the possible values are not even declarated in an enum but as final int
/// class members (thank god for [Idea]Vim macros!).

// Many Bothan agents died to bring us this class
public class JsonASTVisitor extends ASTVisitor {

    private JsonGenerator json;
    private NodeCommentMap commentMap;
    private boolean verboseJson = false;
    private HashSet<String> skipMethods;
    private Hashtable<String, Vector<ChildrenTypeCacheValue>> childrenMethodsCache;
    private IASTNode lastTypeVisited = null;

    IOException error;
    boolean hasError = false;

    public class SyntaxErrorException extends Exception {
        public SyntaxErrorException() { super(); }
        public SyntaxErrorException(String message) { super(message); }
        public SyntaxErrorException(String message, Throwable cause) { super(message, cause); }
        public SyntaxErrorException(Throwable cause) { super(cause); }
    }

    // The visitChildren method uses reflection to get the methods and return values
    // to retrieve children and assign them to properties instead of a flat list. That is
    // slow so we'll cache every inspected node using this class and the childrenMethod
    // cache below.
    private class ChildrenTypeCacheValue
    {
        public String propertyName;
        public Method method;
        public String methodName;
        public boolean returnsArray;

        ChildrenTypeCacheValue(String propName, Method meth, String methName, boolean retArray)
        {
            propertyName = propName;
            method = meth;
            methodName = methName;
            returnsArray = retArray;
        }
    }

    // Used to sort arrays of Methods, see the comment in visitChildren
    private class MethodWrapper implements Comparable<MethodWrapper>
    {
        Method method;
        String name;

        MethodWrapper(Method meth, String name_) {
            method = meth;
            name = name_;
        }


        @Override
        public int compareTo(MethodWrapper m) {
            return m.name.compareTo(name);
        }
    }

    JsonASTVisitor(JsonGenerator json, NodeCommentMap commentMap) {
        super();
        this.json = json;
        this.commentMap = commentMap;

        shouldVisitArrayModifiers = true;
        shouldVisitBaseSpecifiers = true;
        shouldVisitDeclarations = true;
        shouldVisitDeclarators = true;
        shouldVisitDeclSpecifiers = true;
        shouldVisitExpressions = true;
        shouldVisitInitializers = true;
        shouldVisitNames = true;
        shouldVisitNamespaces = true;
        shouldVisitParameterDeclarations = true;
        shouldVisitImplicitNames = true;
        shouldVisitPointerOperators = false;
        shouldVisitStatements = true;
        shouldVisitTemplateParameters = true;
        shouldVisitTranslationUnit = true;
        shouldVisitTypeIds = true;
        // FIXME: change when problem visiting is activated (and remove getProblem
        // from skipMethods below)
        shouldVisitProblems = false;
        skipMethods = new HashSet<String>(Arrays.asList("getClass", "getChildren",
                    "getCompletionContext", "getContainingFilename", "getFileLocation",
                    "getImageLocation", "getOffset", "getParent", "getPropertyInParent",
                    "getTranslationUnit", "getLeadingSyntax", "getLength",
                    "getLinkage", "getOriginalNode", "getRawSignature",
                    "getTrailingSyntax", "getSyntax", "getNodeLocations",
                    "getExecution", "getDependencyTree", "getLastName",
                    "getAlignmentSpecifiers", "getAdapter", "getTypeStringCache",
                    "getProblem", "getRoleForName",
                    // Called manually:
                    "getIncludeDirectives", "getAllPreprocessorStatements"
                    // ImplicitNames
                    //"getImplicitNames", "getFunctionCallOperatorName", "getClosureTypeName"
        ));
        childrenMethodsCache = new Hashtable<String, Vector<ChildrenTypeCacheValue>>();
    }

    private void enableErrorState(IOException e) {
        error = e;
        hasError = true;
    }

    private void serializeLocation(IASTFileLocation loc) throws IOException {
        int lineStart = -1;
        int lineEnd = -1;
        int offsetStart = -1;
        int offsetLength = -1;

        if (loc != null) {
            //lineStart = loc.getStartingLineNumber();
            //lineEnd = loc.getEndingLineNumber();
            //json.writeNumberField("LocLineStart", lineStart);
            //json.writeNumberField("LocLineEnd", lineEnd);
            offsetStart = loc.getNodeOffset();
            json.writeNumberField("LocOffsetStart", offsetStart);
            json.writeNumberField("LocOffsetEnd", offsetStart + loc.getNodeLength());
        }
    }

    private void serializeCommonData(IASTNode node) throws IOException {
        json.writeStringField("IASTClass", node.getClass().getSimpleName());
        if (verboseJson)
            json.writeStringField("Snippet", EclipseCPPParser.getSnippet(node));

        if (verboseJson) {
            ASTNodeProperty propInParent = node.getPropertyInParent();
            if (propInParent != null) {
                json.writeStringField("Role", propInParent.getName());
            }
        }

        serializeLocation(node.getFileLocation());
    }

    private void serializeCommentList(List<IASTComment> comments, String commentType) throws IOException {
        if (comments != null && comments.size() > 0) {
            json.writeFieldName(commentType + "Comments");
            json.writeStartArray();
            try {
                for (IASTComment comment : comments) {
                    json.writeStartObject();
                    try {
                        json.writeStringField("IASTClass", "Comment");
                        json.writeStringField("Comment", comment.toString());
                        json.writeBooleanField("IsBlockComment", comment.isBlockComment());
                        serializeLocation(comment.getFileLocation());
                    } finally {
                        json.writeEndObject();
                    }
                }
            } finally {
                json.writeEndArray();
            }
        }
    }

    private void serializeComments(IASTNode node) throws IOException {
        serializeCommentList(commentMap.getLeadingCommentsForNode(node), "Leading");
        serializeCommentList(commentMap.getFreestandingCommentsForNode(node), "Freestading");
        serializeCommentList(commentMap.getTrailingCommentsForNode(node), "Trailing");
    }

    private void writeChildProperty(IASTNode parent, Method method,
            String propertyName, boolean isArray) throws IOException {
        try {
            if (isArray) {
                Object[] oChildren = (Object[])method.invoke(parent);

                if (oChildren == null || oChildren.length == 0 ||
                    !(oChildren[0] instanceof IASTNode))
                    return;

                json.writeFieldName(propertyName);
                json.writeStartArray();

                try {
                    for(Object oChild : oChildren)
                        ((IASTNode)oChild).accept(this);
                } finally {
                    json.writeEndArray();
                }
            } else {
                Object oChild = method.invoke(parent);

                if (oChild == null || !(oChild instanceof IASTNode))
                    return;

                json.writeFieldName(propertyName);
                ((IASTNode)oChild).accept(this);
            }
        } catch (IllegalAccessException e) {
            return;
        } catch (InvocationTargetException e) {
            return;
        }
    }

    private void visitChildren(IASTNode node) throws IOException {
        String nodeClass = node.getClass().getSimpleName();
        Vector<ChildrenTypeCacheValue> catched = childrenMethodsCache.get(nodeClass);

        if (catched != null) {
            for (ChildrenTypeCacheValue val : catched) {
                writeChildProperty(node, val.method, val.propertyName, val.returnsArray);
            }

        } else {
            // Order of getMethods() changes between runs so we need to do this to
            // ensure that integration tests do not break
            Method[] methods = node.getClass().getMethods();
            MethodWrapper[] methodWrappers = new MethodWrapper[methods.length];
            int idx = 0;

            for (Method m : methods) {
                methodWrappers[idx] = new MethodWrapper(m, m.getName());
                ++idx;
            }

            Arrays.sort(methodWrappers);

            for (MethodWrapper mw : methodWrappers) {
                String mname = mw.name;

                if (!mname.startsWith("get") || skipMethods.contains(mname)
                        || mw.method.getParameterCount() > 0)
                    continue;

                String propName = "Prop_" + mname.substring(3);
                Class<?> returnType = mw.method.getReturnType();

                if (returnType.getName().indexOf("AST") == -1)
                    continue;

                writeChildProperty(node, mw.method, propName, returnType.isArray());

                // Add the node and method information to the cache
                ChildrenTypeCacheValue cacheVal = new ChildrenTypeCacheValue(
                        propName, mw.method, mname, returnType.isArray()
                );

                if (!childrenMethodsCache.containsKey(nodeClass))
                    childrenMethodsCache.put(nodeClass, new Vector<ChildrenTypeCacheValue>());

                childrenMethodsCache.get(nodeClass).add(cacheVal);
            }
        }
    }

    private void serializeUnaryExpression(IASTUnaryExpression node) throws IOException {
        int operator = node.getOperator();
        String opStr;

        switch (operator) {
            case IASTUnaryExpression.op_alignOf:
                opStr = "op_alignOf";
                break;
            case IASTUnaryExpression.op_amper:
                opStr = "op_amper";
                break;
            case IASTUnaryExpression.op_bracketedPrimary:
                opStr = "op_bracketedPrimary";
                break;
            case IASTUnaryExpression.op_labelReference:
                opStr = "op_labelReference";
                break;
            case IASTUnaryExpression.op_minus:
                opStr = "op_minus";
                break;
            case IASTUnaryExpression.op_noexcept:
                opStr = "op_noexcept";
                break;
            case IASTUnaryExpression.op_not:
                opStr = "op_not";
                break;
            case IASTUnaryExpression.op_plus:
                opStr = "op_plus";
                break;
            case IASTUnaryExpression.op_postFixDecr:
                opStr = "op_postFixDecr";
                break;
            case IASTUnaryExpression.op_postFixIncr:
                opStr = "op_postFixIncr";
                break;
            case IASTUnaryExpression.op_prefixDecr:
                opStr = "op_prefixDecr";
                break;
            case IASTUnaryExpression.op_prefixIncr:
                opStr = "op_prefixIncr";
                break;
            case IASTUnaryExpression.op_sizeof:
                opStr = "op_sizeof";
                break;
            case IASTUnaryExpression.op_sizeofParameterPack:
                opStr = "op_sizeofParameterPack";
                break;
            case IASTUnaryExpression.op_star:
                opStr = "op_star";
                break;
            case IASTUnaryExpression.op_throw:
                opStr = "op_throw";
                break;
            case IASTUnaryExpression.op_tilde:
                opStr = "op_tilde";
                break;
            case IASTUnaryExpression.op_typeid:
                opStr = "op_typeid";
                break;
            default:
                opStr = "op_unkown";
                break;
        }
        json.writeStringField("operator", opStr);
    }

    private String serializeTypeIdExpression(IASTTypeIdExpression node) throws IOException {
        int operator = node.getOperator();
        String opStr = "op_unkown";

        switch (operator) {
            case IASTTypeIdExpression.op_alignof:
                opStr = "op_alignof";
                break;
            case IASTTypeIdExpression.op_has_nothrow_assign:
                opStr = "op_has_nothrow_assign";
                break;
            case IASTTypeIdExpression.op_has_nothrow_constructor:
                opStr = "op_has_nothrow_constructor";
                break;
            case IASTTypeIdExpression.op_has_nothrow_copy:
                opStr = "op_has_nothrow_copy";
                break;
            case IASTTypeIdExpression.op_has_trivial_assign:
                opStr = "op_has_trivial_assign";
                break;
            case IASTTypeIdExpression.op_has_trivial_constructor:
                opStr = "op_has_trivial_constructor";
                break;
            case IASTTypeIdExpression.op_has_trivial_copy:
                opStr = "op_has_trivial_copy";
                break;
            case IASTTypeIdExpression.op_has_trivial_destructor:
                opStr = "op_has_trivial_destructor";
                break;
            case IASTTypeIdExpression.op_has_virtual_destructor:
                opStr = "op_has_virtual_destructor";
                break;
            case IASTTypeIdExpression.op_is_abstract:
                opStr = "op_is_abstract";
                break;
            case IASTTypeIdExpression.op_is_class:
                opStr = "op_is_class";
                break;
            case IASTTypeIdExpression.op_is_empty:
                opStr = "op_is_empty";
                break;
            case IASTTypeIdExpression.op_is_enum:
                opStr = "op_is_enum";
                break;
            case IASTTypeIdExpression.op_is_final:
                opStr = "op_is_final";
                break;
            case IASTTypeIdExpression.op_is_literal_type:
                opStr = "op_is_literal_type";
                break;
            case IASTTypeIdExpression.op_is_pod:
                opStr = "op_is_pod";
                break;
            case IASTTypeIdExpression.op_is_polymorphic:
                opStr = "op_is_polymorphic";
                break;
            case IASTTypeIdExpression.op_is_standard_layout:
                opStr = "op_is_standard_layout";
                break;
            case IASTTypeIdExpression.op_is_trivial:
                opStr = "op_is_trivial";
                break;
            case IASTTypeIdExpression.op_is_trivially_copyable:
                json.writeString("op_is_trivially_copyable");
                break;
            case IASTTypeIdExpression.op_is_union:
                opStr = "op_is_union";
                break;
            case IASTTypeIdExpression.op_sizeof:
                opStr = "op_sizeof";
                break;
            case IASTTypeIdExpression.op_sizeofParameterPack:
                opStr = "op_sizeofParameterPack";
                break;
            case IASTTypeIdExpression.op_typeid:
                opStr = "op_typeid";
                break;
            case IASTTypeIdExpression.op_typeof:
                opStr = "op_typeof";
                break;
            default:
        }

        return opStr;
    }

    private String serializeBinaryExpression(IASTBinaryExpression node) {
        int op = node.getOperator();
        String opStr;
        switch (op) {
            case IASTBinaryExpression.op_assign:
                opStr = "=";
                break;
            case IASTBinaryExpression.op_binaryAnd:
                opStr = "&";
                break;
            case IASTBinaryExpression.op_binaryAndAssign:
                opStr = "&=";
                break;
            case IASTBinaryExpression.op_binaryOr:
                opStr = "|";
                break;
            case IASTBinaryExpression.op_binaryOrAssign:
                opStr = "|=";
                break;
            case IASTBinaryExpression.op_binaryXor:
                opStr = "^";
                break;
            case IASTBinaryExpression.op_binaryXorAssign:
                opStr = "^=";
                break;
            case IASTBinaryExpression.op_divide:
                opStr = "/";
                break;
            case IASTBinaryExpression.op_divideAssign:
                opStr = "/=";
                break;
            case IASTBinaryExpression.op_ellipses:
                opStr = "...";
                break;
            case IASTBinaryExpression.op_equals:
                opStr = "==";
                break;
            case IASTBinaryExpression.op_notequals:
                opStr = "!=";
                break;
            case IASTBinaryExpression.op_greaterThan:
                opStr = ">";
                break;
            case IASTBinaryExpression.op_greaterEqual:
                opStr = ">=";
                break;
            case IASTBinaryExpression.op_lessEqual:
                opStr = "<=";
                break;
            case IASTBinaryExpression.op_lessThan:
                opStr = "<";
                break;
            case IASTBinaryExpression.op_logicalAnd:
                opStr = "&&";
                break;
            case IASTBinaryExpression.op_logicalOr:
                opStr = "||";
                break;
            case IASTBinaryExpression.op_max:
                opStr = "max";
                break;
            case IASTBinaryExpression.op_min:
                opStr = "min";
                break;
            case IASTBinaryExpression.op_minus:
                opStr = "-";
                break;
            case IASTBinaryExpression.op_minusAssign:
                opStr = "-=";
                break;
            case IASTBinaryExpression.op_modulo:
                opStr = "%";
                break;
            case IASTBinaryExpression.op_moduloAssign:
                opStr = "%=";
                break;
            case IASTBinaryExpression.op_multiply:
                opStr = "*";
                break;
            case IASTBinaryExpression.op_multiplyAssign:
                opStr = "*=";
                break;
            case IASTBinaryExpression.op_plus:
                opStr = "+";
                break;
            case IASTBinaryExpression.op_plusAssign:
                opStr = "+=";
                break;
            case IASTBinaryExpression.op_pmarrow:
                opStr = "->";
                break;
            case IASTBinaryExpression.op_pmdot:
                opStr = ".";
                break;
            case IASTBinaryExpression.op_shiftLeft:
                opStr = "<<";
                break;
            case IASTBinaryExpression.op_shiftLeftAssign:
                opStr = "<<=";
                break;
            case IASTBinaryExpression.op_shiftRight:
                opStr = ">>";
                break;
            case IASTBinaryExpression.op_shiftRightAssign:
                opStr = ">>=";
                break;
            default:
                opStr = "unkown_operator";
                break;
        }

        return opStr;
    }

    @Override
    public int visit(IASTName node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("Name", node.toString());
                writeIfTrue("IsQualified", node.isQualified());

                if (shouldVisitImplicitNames && node instanceof IASTImplicitName) {
                    IASTImplicitName impl = (IASTImplicitName) node;
                    json.writeBooleanField("IsAlternate", impl.isAlternate());
                    json.writeBooleanField("IsOverloadedOperator", impl.isOperator());
                }

                if (node instanceof ICPPASTQualifiedName) {
                    ICPPASTQualifiedName impl = (ICPPASTQualifiedName) node;
                    json.writeBooleanField("IsConversionOperator", impl.isConversionOrOperator());
                    json.writeBooleanField("IsFullyQualified", impl.isFullyQualified());
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTExpression node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                String exprType = node.getExpressionType().toString();

                if (exprType.indexOf("ProblemType@") != -1) {
                    // Trying to get the type of some untyped expressions give something like:
                    // org.eclipse.cdt.internal.core.dom.parser.ProblemType@50a638b5
                    // The last part is variable so integration tests will fail (and
                    // it doesn't give any information) so we remove it
                    exprType = "org.eclipse.cdt.internal.core.dom.parser.ProblemType";
                } else if (exprType.indexOf("TypeParameter@") != -1) { // ditto
                    exprType = "org.eclipse.cdt.internal.core.dom.parser.cpp.CPPImplicitTTemplateTypeParameter";
                }

                json.writeStringField("ExpressionType", exprType);
                json.writeStringField("ExpressionValueCategory",node.getValueCategory().toString());
                json.writeBooleanField("IsLValue", node.isLValue());

                if (node instanceof IASTBinaryExpression) {
                    String opStr = serializeBinaryExpression((IASTBinaryExpression) node);

                    if (node instanceof ICPPASTBinaryExpression) {
                        ICPPASTBinaryExpression impl = (ICPPASTBinaryExpression) node;

                        switch (impl.getOperator()) {
                            case ICPPASTBinaryExpression.op_pmarrow:
                                opStr = "arrow ->";
                                break;
                            case ICPPASTBinaryExpression.op_pmdot:
                                opStr = "dot .";
                                break;
                        }
                    }
                    json.writeStringField("Operator", opStr);
                }

                if (node instanceof IASTLiteralExpression) {
                    IASTLiteralExpression impl = (IASTLiteralExpression) node;
                    json.writeStringField("LiteralValue", impl.toString());

                    String kindStr;
                    switch (impl.getKind()) {
                        case IASTLiteralExpression.lk_char_constant:
                            kindStr = "char_constant";
                            break;
                        case IASTLiteralExpression.lk_true:
                            kindStr = "true";
                            break;
                        case IASTLiteralExpression.lk_false:
                            kindStr = "false";
                            break;
                        case IASTLiteralExpression.lk_float_constant:
                            kindStr = "float_constant";
                            break;
                        case IASTLiteralExpression.lk_integer_constant:
                            kindStr = "integer_constant";
                            break;
                        case IASTLiteralExpression.lk_nullptr:
                            kindStr = "nullptr";
                            break;
                        case IASTLiteralExpression.lk_string_literal:
                            kindStr = "string_literal";
                            break;
                        case IASTLiteralExpression.lk_this:
                            kindStr = "this";
                            break;
                        default:
                            kindStr = "unknow_literal_value";
                    }
                    json.writeStringField("kind", kindStr);
                }

                if (node instanceof IASTTypeIdExpression) {
                    String opStr = serializeTypeIdExpression((IASTTypeIdExpression) node);

                    if (node instanceof ICPPASTTypeIdExpression) {
                        ICPPASTTypeIdExpression impl = (ICPPASTTypeIdExpression) node;
                        if (impl.getOperator() == ICPPASTTypeIdExpression.op_typeid) {
                            opStr = "typeid";
                        }
                    }
                    json.writeStringField("operator", opStr);
                }

                if (node instanceof IASTBinaryTypeIdExpression) {
                    IASTBinaryTypeIdExpression impl = (IASTBinaryTypeIdExpression) node;
                    IASTBinaryTypeIdExpression.Operator operator = impl.getOperator();
                    json.writeStringField("BinaryTypeIdOperator", operator.toString());
                }

                if (node instanceof IASTFieldReference) {
                    IASTFieldReference impl = (IASTFieldReference) node;
                    json.writeBooleanField("IsPointerDereference", impl.isPointerDereference());

                    if (node instanceof ICPPASTFieldReference) {
                        json.writeBooleanField("IsTemplate", ((ICPPASTFieldReference) node).isTemplate());
                    }
                }

                if (node instanceof ICPPASTDeleteExpression) {
                    ICPPASTDeleteExpression impl = (ICPPASTDeleteExpression) node;
                    json.writeBooleanField("IsGlobal", impl.isGlobal());
                    json.writeBooleanField("IsVectored", impl.isVectored());
                }

                if (node instanceof ICPPASTNaryTypeIdExpression) {
                    ICPPASTNaryTypeIdExpression.Operator operator = ((ICPPASTNaryTypeIdExpression) node).getOperator();
                    json.writeStringField("NaryTypeIdOperator", operator.toString());
                }

                if (node instanceof ICPPASTNewExpression) {
                    json.writeBooleanField("IsArrayAllocation", ((ICPPASTNewExpression) node).isArrayAllocation());
                    json.writeBooleanField("IsGlobal", ((ICPPASTNewExpression) node).isGlobal());
                    json.writeBooleanField("IsNewTypeId", ((ICPPASTNewExpression) node).isNewTypeId());
                }

                if (node instanceof IASTUnaryExpression) {
                    serializeUnaryExpression((IASTUnaryExpression) node);

                    if (node instanceof ICPPASTUnaryExpression) {
                        ICPPASTUnaryExpression uexp = (ICPPASTUnaryExpression) node;

                        ICPPFunction overload = uexp.getOverload();
                        if (overload != null) {
                            json.writeStringField("OverloadedBy", overload.toString());
                        }
                    }
                }

                if (node instanceof IASTCastExpression) {
                    IASTCastExpression impl = (IASTCastExpression) node;

                    if (node instanceof ICPPASTCastExpression) {
                        ICPPASTCastExpression impl2 = (ICPPASTCastExpression) node;

                        String opStr = "";
                        switch (impl2.getOperator()) {
                            case ICPPASTCastExpression.op_cast:
                                opStr = "normal_cast";
                                break;
                            case ICPPASTCastExpression.op_const_cast:
                                opStr = "const_cast";
                                break;
                            case ICPPASTCastExpression.op_dynamic_cast:
                                opStr = "dynamic_cast";
                                break;
                            case ICPPASTCastExpression.op_reinterpret_cast:
                                opStr = "reinterpret_cast";
                                break;
                            case ICPPASTCastExpression.op_static_cast:
                                opStr = "static_cast";
                                break;
                        }
                        json.writeStringField("CastOperator", opStr);
                    }
                }

                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTArrayModifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                if (node instanceof ICASTArrayModifier) {
                    ICASTArrayModifier carr = (ICASTArrayModifier) node;
                    writeIfTrue("IsConst", carr.isConst());
                    writeIfTrue("IsRestrict", carr.isRestrict());
                    writeIfTrue("IsStatic", carr.isStatic());
                    writeIfTrue("IsVolatile", carr.isVolatile());
                }
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTAttribute node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("AttrName", String.valueOf(node.getName()));

                if (node instanceof ICPPASTAttribute) {
                    ICPPASTAttribute attr = (ICPPASTAttribute) node;
                    json.writeBooleanField("HasPackExpansion", attr.hasPackExpansion());
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTDeclaration node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                if (node instanceof IASTASMDeclaration) {
                    IASTASMDeclaration impl = (IASTASMDeclaration) node;
                    json.writeStringField("Assembly", impl.getAssembly());
                }

                if (node instanceof ICPPASTExplicitTemplateInstantiation) {
                    ICPPASTExplicitTemplateInstantiation impl = (ICPPASTExplicitTemplateInstantiation) node;

                    String opMod;
                    switch (impl.getModifier()) {
                        case ICPPASTExplicitTemplateInstantiation.EXTERN:
                            opMod = "extern";
                            break;
                        case ICPPASTExplicitTemplateInstantiation.INLINE:
                            opMod = "inline";
                            break;
                        case ICPPASTExplicitTemplateInstantiation.STATIC:
                            opMod = "static";
                            break;
                        default:
                            opMod = "";
                            break;
                    }
                    json.writeStringField("Modifier", opMod);
                }

                if (node instanceof ICPPASTVisibilityLabel) {
                    ICPPASTVisibilityLabel impl = (ICPPASTVisibilityLabel) node;

                    String opMod;
                    switch (impl.getVisibility()) {
                        case ICPPASTVisibilityLabel.v_private:
                            opMod = "private";
                            break;
                        case ICPPASTVisibilityLabel.v_protected:
                            opMod = "protected";
                            break;
                        case ICPPASTVisibilityLabel.v_public:
                            opMod = "public";
                            break;
                        default:
                            opMod = "";
                            break;
                    }
                    json.writeStringField("Visibility", opMod);
                }

                if (node instanceof ICPPASTUsingDeclaration) {
                    ICPPASTUsingDeclaration impl = (ICPPASTUsingDeclaration) node;
                    json.writeStringField("Name", ICPPASTUsingDeclaration.NAME.toString());
                    json.writeBooleanField("IsTypeName", impl.isTypename());
                }

                if (node instanceof ICPPASTTemplateDeclaration) {
                    ICPPASTTemplateDeclaration impl = (ICPPASTTemplateDeclaration) node;
                    json.writeBooleanField("IsExported", impl.isExported());
                }

                if (node instanceof ICPPASTNamespaceDefinition) {
                    ICPPASTNamespaceDefinition impl = (ICPPASTNamespaceDefinition) node;
                    json.writeBooleanField("IsInline", impl.isInline());
                }

                if (node instanceof ICPPASTLinkageSpecification) {
                    ICPPASTLinkageSpecification impl = (ICPPASTLinkageSpecification) node;
                    json.writeStringField("Literal", impl.getLiteral());
                }

                if (node instanceof IASTFunctionDefinition) {
                    IASTFunctionDefinition impl = (IASTFunctionDefinition) node;

                    if (node instanceof ICPPASTFunctionDefinition) {
                        ICPPASTFunctionDefinition impl2 = (ICPPASTFunctionDefinition) node;
                        json.writeBooleanField("IsDefaulted", impl2.isDefaulted());
                        json.writeBooleanField("IsDeleted", impl2.isDeleted());
                    }
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    private void writeIfTrue(String name, boolean field) throws IOException {
        if (field) {
            json.writeBooleanField(name, true);
        }
    }

    @Override
    public int visit(IASTDeclarator node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);

                if (node instanceof IASTStandardFunctionDeclarator) {
                    IASTStandardFunctionDeclarator impl = (IASTStandardFunctionDeclarator) node;
                    json.writeBooleanField("TakesVarArgs", impl.takesVarArgs());

                    if (node instanceof ICPPASTFunctionDeclarator) {
                        ICPPASTFunctionDeclarator impl2 = (ICPPASTFunctionDeclarator) node;
                        writeIfTrue("IsConst", impl2.isConst());
                        writeIfTrue("IsFinal", impl2.isFinal());
                        writeIfTrue("IsMutable", impl2.isMutable());
                        writeIfTrue("IsOverride", impl2.isOverride());
                        writeIfTrue("IsPureVirtual", impl2.isPureVirtual());
                        writeIfTrue("IsVolatile", impl2.isVolatile());
                    }
                }

                if (node instanceof IASTDeclarator && !(node instanceof IASTFunctionDeclarator)) {
                    ICPPASTDeclarator impl = (ICPPASTDeclarator) node;
                    json.writeBooleanField("DeclaresParameterPack", impl.declaresParameterPack());

                    if (lastTypeVisited != null) {
                        // Reparent the type node here
                        json.writeFieldName("Prop_TypeNode");
                        lastTypeVisited.accept(this);
                        lastTypeVisited = null;
                    } else {
                        json.writeNullField("Prop_TypeNode");
                    }
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTDeclSpecifier node) {
        try {
            if (lastTypeVisited == null &&
               (node instanceof IASTSimpleDeclSpecifier ||
                node instanceof IASTNamedTypeSpecifier) &&
                node.getParent() instanceof IASTParameterDeclaration) {

                // Will be visited as Prop_TypeNode child of the next Parameter->IASTDeclarator node
                lastTypeVisited = node;
                return PROCESS_SKIP;
            }

            json.writeStartObject();
            try {

                serializeCommonData(node);
                writeIfTrue("IsConst", node.isConst());
                writeIfTrue("IsInline", node.isInline());
                writeIfTrue("IsRestrict", node.isRestrict());
                writeIfTrue("IsVolatile", node.isVolatile());

                String stStr;
                switch (node.getStorageClass()) {
                    case IASTDeclSpecifier.sc_auto:
                        stStr = "auto";
                        break;
                    case IASTDeclSpecifier.sc_extern:
                        stStr = "extern";
                        break;
                    case IASTDeclSpecifier.sc_mutable:
                        stStr = "mutable";
                        break;
                    case IASTDeclSpecifier.sc_register:
                        stStr = "register";
                        break;
                    case IASTDeclSpecifier.sc_static:
                        stStr = "static";
                        break;
                    case IASTDeclSpecifier.sc_typedef:
                        stStr = "typedef";
                        break;
                    default:
                        stStr = "unspecified";
                        break;
                }

                json.writeStringField("StorageClass", stStr);

                if (node instanceof ICPPASTDeclSpecifier) {
                    ICPPASTDeclSpecifier impl = (ICPPASTDeclSpecifier) node;
                    writeIfTrue("IsConstExpr", impl.isConstexpr());
                    writeIfTrue("IsExplicit", impl.isExplicit());
                    writeIfTrue("IsFriend", impl.isFriend());
                    writeIfTrue("IsThreadLocal", impl.isThreadLocal());
                    writeIfTrue("IsVirtual", impl.isVirtual());
                }

                if (node instanceof IASTCompositeTypeSpecifier) {
                    IASTCompositeTypeSpecifier impl = (IASTCompositeTypeSpecifier) node;

                    String keyStr = "";
                    switch (impl.getKey()) {
                        case IASTCompositeTypeSpecifier.k_struct:
                            keyStr = "struct";
                            break;
                        case IASTCompositeTypeSpecifier.k_union:
                            keyStr = "union";
                            break;
                    }


                    if (node instanceof ICPPASTCompositeTypeSpecifier) {
                        ICPPASTCompositeTypeSpecifier impl2 = (ICPPASTCompositeTypeSpecifier) node;

                        switch (impl2.getKey()) {
                            case ICPPASTCompositeTypeSpecifier.k_class:
                                keyStr = "class";
                                break;
                        }

                        json.writeBooleanField("IsFinal", impl2.isFinal());
                    }

                    json.writeStringField("Key", keyStr);
                }

                if (node instanceof IASTElaboratedTypeSpecifier) {
                    IASTElaboratedTypeSpecifier impl = (IASTElaboratedTypeSpecifier) node;

                    String kindStr = "";
                    switch (impl.getKind()) {
                        case IASTElaboratedTypeSpecifier.k_enum:
                            kindStr = "enum";
                            break;
                        case IASTElaboratedTypeSpecifier.k_struct:
                            kindStr = "struct";
                            break;
                        case IASTElaboratedTypeSpecifier.k_union:
                            kindStr = "union";
                            break;
                    }

                    if (node instanceof ICPPASTElaboratedTypeSpecifier) {
                        ICPPASTElaboratedTypeSpecifier impl2 = (ICPPASTElaboratedTypeSpecifier) node;

                        switch (impl2.getKind()) {
                            case ICPPASTElaboratedTypeSpecifier.k_class:
                                kindStr = "class";
                                break;
                        }
                    }

                    json.writeStringField("Kind", kindStr);
                }

                if (node instanceof IASTSimpleDeclSpecifier) {
                    IASTSimpleDeclSpecifier impl = (IASTSimpleDeclSpecifier) node;
                    writeIfTrue("IsComplex", impl.isComplex());
                    writeIfTrue("IsImaginary", impl.isImaginary());
                    writeIfTrue("IsLong", impl.isLong());
                    writeIfTrue("IsLongLong", impl.isLongLong());
                    writeIfTrue("IsShort", impl.isShort());
                    writeIfTrue("IsSigned", impl.isSigned());
                    writeIfTrue("IsUnsigned", impl.isUnsigned());

                    String typeStr;

                    switch (impl.getType()) {
                        case IASTSimpleDeclSpecifier.t_auto:
                            typeStr = "auto";
                            break;
                        case IASTSimpleDeclSpecifier.t_bool:
                            typeStr = "bool";
                            break;
                        case IASTSimpleDeclSpecifier.t_char:
                            typeStr = "char";
                            break;
                        case IASTSimpleDeclSpecifier.t_char16_t:
                            typeStr = "char16";
                            break;
                        case IASTSimpleDeclSpecifier.t_char32_t:
                            typeStr = "char32";
                            break;
                        case IASTSimpleDeclSpecifier.t_decimal32:
                            typeStr = "decimal32";
                            break;
                        case IASTSimpleDeclSpecifier.t_decimal64:
                            typeStr = "decimal64";
                            break;
                        case IASTSimpleDeclSpecifier.t_decimal128:
                            typeStr = "decimal128";
                            break;
                        case IASTSimpleDeclSpecifier.t_decltype:
                            typeStr = "decltype";
                            break;
                        case IASTSimpleDeclSpecifier.t_decltype_auto:
                            typeStr = "decltype_auto";
                            break;
                        case IASTSimpleDeclSpecifier.t_double:
                            typeStr = "double";
                            break;
                        case IASTSimpleDeclSpecifier.t_float:
                            typeStr = "float";
                            break;
                        case IASTSimpleDeclSpecifier.t_float128:
                            typeStr = "float128";
                            break;
                        case IASTSimpleDeclSpecifier.t_int:
                            typeStr = "int";
                            break;
                        case IASTSimpleDeclSpecifier.t_int128:
                            typeStr = "int128";
                            break;
                        case IASTSimpleDeclSpecifier.t_typeof:
                            typeStr = "typeof";
                            break;
                        case IASTSimpleDeclSpecifier.t_void:
                            typeStr = "void";
                            break;
                        case IASTSimpleDeclSpecifier.t_wchar_t:
                            typeStr = "wchar_t";
                            break;
                        default:
                            typeStr = "unespecified";
                            break;
                        }

                        json.writeStringField("Type", typeStr);
                }

                if (node instanceof ICPPASTEnumerationSpecifier) {
                    ICPPASTEnumerationSpecifier impl2 = (ICPPASTEnumerationSpecifier) node;
                    json.writeBooleanField("IsOpaque", impl2.isOpaque());
                    json.writeBooleanField("IsScoped", impl2.isScoped());
                }

                if (node instanceof IASTNamedTypeSpecifier) {
                    IASTNamedTypeSpecifier impl = (IASTNamedTypeSpecifier) node;
                    if (node instanceof ICPPASTNamedTypeSpecifier) {
                        ICPPASTNamedTypeSpecifier impl2 = (ICPPASTNamedTypeSpecifier) node;
                        json.writeBooleanField("IsTypeName", impl2.isTypename());
                    }
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTInitializer node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                if (node instanceof IASTInitializerList) {
                    IASTInitializerList impl = (IASTInitializerList) node;
                    json.writeNumberField("Size", impl.getSize());
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTPointerOperator node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);

                if (node instanceof IASTPointer) {
                    IASTPointer impl = (IASTPointer) node;
                    writeIfTrue("IsConst", impl.isConst());
                    writeIfTrue("IsRestrict", impl.isRestrict());
                    writeIfTrue("IsVolatile", impl.isVolatile());

                    if (node instanceof ICPPASTPointerToMember) {
                        ICPPASTPointerToMember impl2 = (ICPPASTPointerToMember) node;
                    }
                }

                if (node instanceof ICPPASTReferenceOperator) {
                    ICPPASTReferenceOperator impl = (ICPPASTReferenceOperator) node;
                    json.writeBooleanField("IsRValueReference", impl.isRValueReference());
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTStatement node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);

                if (node instanceof ICPPASTCatchHandler) {
                    ICPPASTCatchHandler impl = (ICPPASTCatchHandler) node;
                    json.writeBooleanField("IsCatchAll", impl.isCatchAll());
                }

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTToken node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("Image", String.valueOf(node.getTokenCharImage()));
                // FIXME: this returns and int which meaning is undocumented... try to infer them?
                json.writeNumberField("IntTokenType", node.getTokenType());

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    private void serializePreproStatements(IASTTranslationUnit unit) throws IOException {
        IASTPreprocessorStatement[] stmts = unit.getAllPreprocessorStatements();
        json.writeFieldName("Prop_PreprocStatements");
        json.writeStartArray();
        try {
            for (IASTPreprocessorStatement stmt : stmts) {
                json.writeStartObject();
                try {
                    serializeCommonData(stmt);

                    if (stmt instanceof IASTPreprocessorMacroDefinition) {
                        IASTPreprocessorMacroDefinition s = (IASTPreprocessorMacroDefinition)stmt;
                        json.writeStringField("Name", s.getName().toString());
                        json.writeBooleanField("IsActive", s.isActive());
                        json.writeStringField("Expansion", s.getExpansion());

                        json.writeFieldName("ExpansionPosition");
                        json.writeStartObject();
                        try {
                            serializeLocation(s.getExpansionLocation());
                        } finally {
                            json.writeEndObject();
                        }
                    }

                    if (stmt instanceof IASTPreprocessorIfStatement) {
                        IASTPreprocessorIfStatement s = (IASTPreprocessorIfStatement)stmt;
                        json.writeStringField("Condition", new String(s.getCondition()));
                        json.writeBooleanField("IsTaken", s.taken());
                    } else if (stmt instanceof IASTPreprocessorIncludeStatement) {
                        IASTPreprocessorIncludeStatement s = (IASTPreprocessorIncludeStatement)stmt;
                        json.writeStringField("Name", s.getName().toString());
                        json.writeStringField("Path", s.getPath());
                        json.writeBooleanField("Resolved", s.isResolved());
                        json.writeBooleanField("IsSystem", s.isSystemInclude());
                    } else if (stmt instanceof IASTPreprocessorIfndefStatement) {
                        IASTPreprocessorIfndefStatement s = (IASTPreprocessorIfndefStatement)stmt;
                        json.writeStringField("Condition", new String(s.getCondition()));
                        json.writeBooleanField("IsTaken", s.taken());
                        json.writeStringField("MacroReference", s.getMacroReference().toString());
                    } else if (stmt instanceof IASTPreprocessorIfdefStatement) {
                        IASTPreprocessorIfdefStatement s = (IASTPreprocessorIfdefStatement)stmt;
                        json.writeStringField("Condition", new String(s.getCondition()));
                        json.writeBooleanField("IsTaken", s.taken());
                        json.writeStringField("MacroReference", s.getMacroReference().toString());
                    } else if (stmt instanceof IASTPreprocessorElifStatement) {
                        IASTPreprocessorElifStatement s = (IASTPreprocessorElifStatement)stmt;
                        json.writeStringField("Condition", new String(s.getCondition()));
                        json.writeBooleanField("IsTaken", s.taken());
                    } else if (stmt instanceof IASTPreprocessorElseStatement) {
                        IASTPreprocessorElseStatement s = (IASTPreprocessorElseStatement)stmt;
                        json.writeBooleanField("IsTaken", s.taken());
                    } else if (stmt instanceof IASTPreprocessorErrorStatement) {
                        IASTPreprocessorErrorStatement s = (IASTPreprocessorErrorStatement)stmt;
                        json.writeStringField("ErrorMsg", new String(s.getMessage()));
                    } else if (stmt instanceof IASTPreprocessorFunctionStyleMacroDefinition) {
                        IASTPreprocessorFunctionStyleMacroDefinition s = (IASTPreprocessorFunctionStyleMacroDefinition)stmt;
                        IASTFunctionStyleMacroParameter[] params = s.getParameters();
                        json.writeFieldName("Parameters");
                        json.writeStartArray();
                        try {
                            for (IASTFunctionStyleMacroParameter param : params) {
                                json.writeString(param.getParameter());
                            }
                        } finally {
                            json.writeEndArray();
                        }

                    } else if (stmt instanceof IASTPreprocessorPragmaStatement) {
                        IASTPreprocessorPragmaStatement s = (IASTPreprocessorPragmaStatement)stmt;
                        json.writeStringField("Message", new String(s.getMessage()));
                        json.writeBooleanField("IsPragmaOperator", s.isPragmaOperator());
                    } else if (stmt instanceof IASTPreprocessorUndefStatement) {
                        IASTPreprocessorUndefStatement s = (IASTPreprocessorUndefStatement)stmt;
                        json.writeStringField("Name", s.getMacroName().toString());
                        json.writeBooleanField("IsActive", s.isActive());
                    }

                } finally {
                    json.writeEndObject();
                }
            }
        } finally {
            json.writeEndArray();
        }
    }

    @Override
    public int visit(IASTTranslationUnit node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                //serializeIncludes(node);
                serializePreproStatements(node);

                // Include directives
                // FIXME:
                // This must be explored to get the preprocessor directives with:
                // THIS
                // getMacroDefinitions (non built in macros defined in this file)
                // getMacroExpansions (where are they expanded)
                // getBuildtinMacroDefinitions
                // getIncludeDirectives
                // getAllPreprocessorStatements
                // isHeaderUnit
                // flattenLocationsToFile (try it)
                // copy (to modify it)
                // setSignificantMacros
                //
                // Plus: ICPPASTTranslationUnit getNamespaceScope, getMemberBindings, isInline
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTTypeId node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(ICPPASTCapture node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("Identifier", ICPPASTCapture.IDENTIFIER.toString());
                json.writeBooleanField("CapturesThisPointer", node.capturesThisPointer());
                json.writeBooleanField("IsByReference", node.isByReference());
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(ICPPASTCompositeTypeSpecifier.ICPPASTBaseSpecifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                // getVisibility
                json.writeBooleanField("IsVirtual", node.isVirtual());
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(ICPPASTTemplateParameter node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeBooleanField("IsParameterPack", node.isParameterPack());

                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int visit(IASTAttributeSpecifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChildren(node);
            } finally {
                json.writeEndObject();
            }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }
}
