package tech.sourced.babelfish;

import com.fasterxml.jackson.core.JsonGenerator;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;

// FIXME: IASTProblem
//

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

// Many Bothans died to bring us this class
public class JsonASTVisitor extends ASTVisitor {

    private JsonGenerator json;
    private NodeCommentMap commentMap;
    private boolean verboseJson = false;
    IOException error;
    boolean hasError = false;


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
        shouldVisitPointerOperators = true;
        shouldVisitStatements = true;
        shouldVisitTemplateParameters = true;
        shouldVisitTranslationUnit = true;
        shouldVisitTypeIds = true;
    }

    private void enableErrorState(IOException e) {
        error = e;
        hasError = true;
    }

    private void serializeNodeLocation(IASTNode node) throws IOException {
        IASTFileLocation loc = node.getFileLocation();
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
        json.writeBooleanField("IsActive", node.isActive());
        json.writeBooleanField("IsFrozen", node.isFrozen());

        // Unneded: now this is a property in the parent, uncomment code when the
        // problem with the lambda expression is fixed (FIXME)
        //if (verboseJson) {
            ASTNodeProperty propInParent = node.getPropertyInParent();
            if (propInParent != null) {
                json.writeStringField("Role", propInParent.getName());
            }
        //}

        serializeNodeLocation(node);
    }

    private void serializeCommentList(List<IASTComment> comments, String commentType) throws IOException {
        if (comments != null && comments.size() > 0) {
            json.writeFieldName(commentType + "Comments");
            json.writeStartArray();
            try {
                for (IASTComment comment : comments) {
                    json.writeStartObject();
                    try {
                        json.writeStringField("Comment", comment.toString());
                        json.writeBooleanField("IsBlockComment", comment.isBlockComment());
                        serializeNodeLocation(comment);
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

    // We need to call this on each visitor instead of just retuning
    // PROCESS_SKIP and let the base clase do it automatically because
    // we need to start the child JSON array
    private void visitChildren(IASTNode node) throws IOException {
        IASTNode[] children = node.getChildren();
        if (children == null || children.length == 0)
            return;

        // Load the children into a hashtable, then write children with the same role
        // inside an array value
        Hashtable<String, Vector<IASTNode>> hash = new Hashtable<String, Vector<IASTNode>>();

        // FIXME: this doesnt seem to work for CPPASTLambdaExpression (all children
        // are dumped into ICPPASTLambdaExpression property)
        for (IASTNode child : children) {
            ASTNodeProperty role = child.getPropertyInParent();

            if (role == null)
                continue;

            String key = role.getName().split(" ")[0];
            Vector<IASTNode> l;

            if (hash.containsKey(key)) {
                l = hash.get(key);
            } else {
                l = new Vector<IASTNode>();
                hash.put(key, l);
            }

            l.add(child);
        }

        for (String key: hash.keySet()) {
            json.writeFieldName(key);
            json.writeStartArray();
            try {
                for (IASTNode n: hash.get(key)) {
                    n.accept(this);
                }
            } finally {
                json.writeEndArray();
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
                opStr = "assignement =";
                break;
            case IASTBinaryExpression.op_binaryAnd:
                opStr = "binary And &";
                break;
            case IASTBinaryExpression.op_binaryAndAssign:
                opStr = "binary And assign &=";
                break;
            case IASTBinaryExpression.op_binaryOr:
                opStr = "binary or |";
                break;
            case IASTBinaryExpression.op_binaryOrAssign:
                opStr = "Or assign |=";
                break;
            case IASTBinaryExpression.op_binaryXor:
                opStr = "Xor ^";
                break;
            case IASTBinaryExpression.op_binaryXorAssign:
                opStr = "Xor assign ^=";
                break;
            case IASTBinaryExpression.op_divide:
                opStr = "/";
                break;
            case IASTBinaryExpression.op_divideAssign:
                opStr = "assignemnt /=";
                break;
            case IASTBinaryExpression.op_ellipses:
                opStr = "gcc compilers, only.";
                break;
            case IASTBinaryExpression.op_equals:
                opStr = "==";
                break;
            case IASTBinaryExpression.op_greaterEqual:
                opStr = "than or equals >=";
                break;
            case IASTBinaryExpression.op_greaterThan:
                opStr = "than >";
                break;
            case IASTBinaryExpression.op_lessEqual:
                opStr = "than or equals <=";
                break;
            case IASTBinaryExpression.op_lessThan:
                opStr = "than <";
                break;
            case IASTBinaryExpression.op_logicalAnd:
                opStr = "and &&";
                break;
            case IASTBinaryExpression.op_logicalOr:
                opStr = "or ||";
                break;
            case IASTBinaryExpression.op_max:
                opStr = "g++, only.";
                break;
            case IASTBinaryExpression.op_min:
                opStr = "g++, only.";
                break;
            case IASTBinaryExpression.op_minus:
                opStr = "-";
                break;
            case IASTBinaryExpression.op_minusAssign:
                opStr = "assignment -=";
                break;
            case IASTBinaryExpression.op_modulo:
                opStr = "%";
                break;
            case IASTBinaryExpression.op_moduloAssign:
                opStr = "assignment %=";
                break;
            case IASTBinaryExpression.op_multiply:
                opStr = "*";
                break;
            case IASTBinaryExpression.op_multiplyAssign:
                opStr = "assignment *=";
                break;
            case IASTBinaryExpression.op_notequals:
                opStr = "equals !";
                break;
            case IASTBinaryExpression.op_plus:
                opStr = "+";
                break;
            case IASTBinaryExpression.op_plusAssign:
                opStr = "assignment +=";
                break;
            case IASTBinaryExpression.op_pmarrow:
                opStr = "c++, only.";
                break;
            case IASTBinaryExpression.op_pmdot:
                opStr = "c==, only.";
                break;
            case IASTBinaryExpression.op_shiftLeft:
                opStr = "left <<";
                break;
            case IASTBinaryExpression.op_shiftLeftAssign:
                opStr = "left assignment <<=";
                break;
            case IASTBinaryExpression.op_shiftRight:
                opStr = "right >>";
                break;
            case IASTBinaryExpression.op_shiftRightAssign:
                opStr = "right assign >>=";
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
                json.writeBooleanField("IsQualified", node.isQualified());

                if (node instanceof IASTImplicitName) {
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
                json.writeFieldName("ExpressionType");
                json.writeString(node.getExpressionType().toString());
                json.writeFieldName("ExpressionValueCategory");
                json.writeString(node.getValueCategory().toString());
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
                    json.writeStringField("OPERAND1", impl.OPERAND1.toString());
                    json.writeStringField("OPERAND2", impl.OPERAND2.toString());
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
                    json.writeBooleanField("IsConst", carr.isConst());
                    json.writeBooleanField("IsRestrict", carr.isRestrict());
                    json.writeBooleanField("IsStatic", carr.isStatic());
                    json.writeBooleanField("IsVolatile", carr.isVolatile());
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
                    json.writeStringField("Name", impl.NAME.toString());
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
                        json.writeBooleanField("IsConst", impl2.isConst());
                        json.writeBooleanField("IsFinal", impl2.isFinal());
                        json.writeBooleanField("IsMutable", impl2.isMutable());
                        json.writeBooleanField("IsOverride", impl2.isOverride());
                        json.writeBooleanField("IsPureVirtual", impl2.isPureVirtual());
                        json.writeBooleanField("IsVolatile", impl2.isVolatile());
                    }
                }

                if (node instanceof ICPPASTDeclarator) {
                    ICPPASTDeclarator impl = (ICPPASTDeclarator) node;
                    json.writeBooleanField("DeclaresParameterPack", impl.declaresParameterPack());
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
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeBooleanField("IsConst", node.isConst());
                json.writeBooleanField("IsInline", node.isInline());
                json.writeBooleanField("IsRestrict", node.isRestrict());
                json.writeBooleanField("IsVolatile", node.isVolatile());

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

                if (node instanceof ICASTDeclSpecifier) {
                    ICASTDeclSpecifier impl = (ICASTDeclSpecifier) node;
                }

                if (node instanceof ICPPASTDeclSpecifier) {
                    ICPPASTDeclSpecifier impl = (ICPPASTDeclSpecifier) node;
                    json.writeBooleanField("IsConstExpr", impl.isConstexpr());
                    json.writeBooleanField("IsExplicit", impl.isExplicit());
                    json.writeBooleanField("IsFriend", impl.isFriend());
                    json.writeBooleanField("IsThreadLocal", impl.isThreadLocal());
                    json.writeBooleanField("IsVirtual", impl.isVirtual());
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
                    json.writeBooleanField("IsComplex", impl.isComplex());
                    json.writeBooleanField("IsImaginary", impl.isImaginary());
                    json.writeBooleanField("IsLong", impl.isLong());
                    json.writeBooleanField("IsLongLong", impl.isLongLong());
                    json.writeBooleanField("IsShort", impl.isShort());
                    json.writeBooleanField("IsSigned", impl.isSigned());
                    json.writeBooleanField("IsUnsigned", impl.isUnsigned());

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
                            typeStr = "unexpecified";
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
                    json.writeBooleanField("IsConst", impl.isConst());
                    json.writeBooleanField("IsRestrict", impl.isRestrict());
                    json.writeBooleanField("IsVolatile", impl.isVolatile());

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

    @Override
    public int visit(IASTTranslationUnit node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
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

    // XXX try and check remove (common data, children, etc)
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
                json.writeStringField("Identifier", node.IDENTIFIER.toString());
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
