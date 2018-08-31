package tech.sourced.babelfish;

import com.fasterxml.jackson.core.JsonGenerator;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.*;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

// FIXME: Uppercase attribute fields (node.ATTR.toString())
// FIXME: IASTProblem

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
            lineStart = loc.getStartingLineNumber();
            lineEnd = loc.getEndingLineNumber();
            offsetStart = loc.getNodeOffset();
            offsetLength = loc.getNodeLength();
        }
        json.writeNumberField("LocLineStart", lineStart);
        json.writeNumberField("LocLineEnd", lineEnd);
        json.writeNumberField("LocOffsetStart", offsetStart);
        json.writeNumberField("LocOffsetLength", offsetLength);
    }

    private void serializeCommonData(IASTNode node) throws IOException {
        json.writeStringField("IASTClass", node.getClass().getSimpleName());
        json.writeStringField("Snippet", EclipseCPPParser.getSnippet(node));
        json.writeBooleanField("IsActive", node.isActive());
        json.writeBooleanField("IsFrozen", node.isFrozen());

        ASTNodeProperty propInParent = node.getPropertyInParent();
        if (propInParent != null) {
            json.writeStringField("Role", propInParent.getName());
        }

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

//    private void serializeIASTNameOwner(IASTNameOwner node) throws IOException {
//        int roleOfName = node.getRoleForName(node);
//        json.writeFieldName("RoleForName");
//
//        switch (roleOfName) {
//            case IASTNameOwner.r_declaration:
//                json.writeString("declaration");
//                break;
//            case IASTNameOwner.r_definition:
//                json.writeString("definition");
//                break;
//            case IASTNameOwner.r_reference:
//                json.writeString("reference");
//                break;
//            default:
//                json.writeString("unclear");
//        }
//    }

    // We need to call this on each visitor instead of just retuning
    // PROCESS_SKIP and let the base clase do it automatically because
    // we need to start the child JSON array
    private void visitChilds(IASTNode node) throws IOException {
        IASTNode[] children = node.getChildren();

        if (children != null && children.length > 0) {
            json.writeFieldName("childs");
            json.writeStartArray();
            try {
                for (IASTNode child : children) {
                    child.accept(this);
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
                json.writeStringField("FullName", Arrays.toString(node.toCharArray()));
                json.writeBooleanField("IsQualified", node.isQualified());
                json.writeStringField("Binding", node.getBinding().toString());
                json.writeStringField("ResolvedBinding", node.resolveBinding().toString());
                json.writeStringField("PreBinding", node.getPreBinding().toString());
                json.writeStringField("ResolvedPreBinding", node.resolvePreBinding().toString());
//                serializeIASTNameOwner((IASTNameOwner) node);

                if (node instanceof IASTImplicitName) {
                    IASTImplicitName impl = (IASTImplicitName) node;
                    json.writeBooleanField("IsAlternate", impl.isAlternate());
                    json.writeBooleanField("IsOverloadedOperator", impl.isOperator());
                }

                if (node instanceof ICPPASTName) {
                    if (node instanceof ICPPASTConversionName) {
                        ICPPASTConversionName impl = (ICPPASTConversionName) node;
                        json.writeStringField("Type_Id", impl.TYPE_ID.toString());
                    }

                    if (node instanceof ICPPASTQualifiedName) {
                        ICPPASTQualifiedName impl = (ICPPASTQualifiedName) node;
                        json.writeStringField("Segment_Name", impl.SEGMENT_NAME.toString());
                        json.writeBooleanField("IsConversionOperator", impl.isConversionOrOperator());
                        json.writeBooleanField("IsFullyQualified", impl.isFullyQualified());
                    }

                    if (node instanceof ICPPASTTemplateId) {
                        ICPPASTTemplateId impl = (ICPPASTTemplateId) node;
                        json.writeStringField("Template_Id_Argument", impl.TEMPLATE_ID_ARGUMENT.toString());
                        json.writeStringField("Template_Name", impl.TEMPLATE_NAME.toString());
                    }
                }

                serializeComments(node);
                visitChilds(node);
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
                    json.writeStringField("TYPE_ID", ((IASTTypeIdExpression) node).TYPE_ID.toString());

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
                    json.writeStringField("OPERAND", impl.OPERAND.toString());
                    json.writeStringField("TYPE_ID", impl.TYPE_ID.toString());

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

                if (node instanceof IASTConditionalExpression) {
                    IASTConditionalExpression impl = (IASTConditionalExpression) node;
                    json.writeStringField("LOGICAL_CONDITION", impl.LOGICAL_CONDITION.toString());
                    json.writeStringField("NEGATIVE_RESULT", impl.NEGATIVE_RESULT.toString());
                    json.writeStringField("POSITIVE_RESULT", impl.POSITIVE_RESULT.toString());
                }

                if (node instanceof IASTExpressionList) {
                    IASTExpressionList impl = (IASTExpressionList) node;
                    json.writeStringField("NESTED_EXPRESSION", impl.NESTED_EXPRESSION.toString());
                }

                if (node instanceof IASTTypeIdInitializerExpression) {
                    IASTTypeIdInitializerExpression impl = (IASTTypeIdInitializerExpression) node;
                    json.writeStringField("INITIALIZER", impl.INITIALIZER.toString());
                    json.writeStringField("TYPE_ID", impl.TYPE_ID.toString());
                }

                if (node instanceof IASTIdExpression) {
                    IASTIdExpression impl = (IASTIdExpression) node;
                    json.writeStringField("ID_NAME", impl.ID_NAME.toString());
                }

                if (node instanceof ICPPASTSimpleTypeConstructorExpression) {
                    ICPPASTSimpleTypeConstructorExpression impl = (ICPPASTSimpleTypeConstructorExpression) node;
                    json.writeStringField("INITIALIZER", impl.INITIALIZER.toString());
                }

                if (node instanceof ICPPASTLambdaExpression) {
                    ICPPASTLambdaExpression impl = (ICPPASTLambdaExpression) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CAPTURE", impl.CAPTURE.toString());
                    json.writeStringField("DECLARATOR", impl.DECLARATOR.toString());
                }

                if (node instanceof ICPPASTPackExpansionExpression) {
                    ICPPASTPackExpansionExpression impl = (ICPPASTPackExpansionExpression) node;
                    json.writeStringField("PATTERN", impl.PATTERN.toString());
                }

                if (node instanceof IASTFunctionCallExpression) {
                    IASTFunctionCallExpression impl = (IASTFunctionCallExpression) node;
                    json.writeStringField("FUNCTION_NAME", impl.FUNCTION_NAME.toString());
                    json.writeStringField("ARGUMENT", impl.ARGUMENT.toString());
                }


                visitChilds(node);
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
                visitChilds(node);
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
                visitChilds(node);
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
                    json.writeStringField("Owned_Declaration", impl.OWNED_DECLARATION.toString());
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
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                    json.writeStringField("Implicit_Name", ((IASTImplicitNameOwner) node).IMPLICIT_NAME.toString());
                }

                if (node instanceof ICPPASTUsingDeclaration) {
                    ICPPASTUsingDeclaration impl = (ICPPASTUsingDeclaration) node;
                    json.writeStringField("Name", impl.NAME.toString());
                    json.writeBooleanField("IsTypeName", impl.isTypename());
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                    json.writeStringField("Implicit_Name", ((IASTImplicitNameOwner) node).IMPLICIT_NAME.toString());
                }

                if (node instanceof ICPPASTUsingDirective) {
                    ICPPASTUsingDirective impl = (ICPPASTUsingDirective) node;
                    json.writeStringField("Qualified_Name", impl.QUALIFIED_NAME.toString());
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                }

                if (node instanceof ICPPASTTemplateSpecialization) {
                    ICPPASTTemplateSpecialization impl = (ICPPASTTemplateSpecialization) node;
                    json.writeStringField("Owned_Declaration", impl.OWNED_DECLARATION.toString());
                }

                if (node instanceof ICPPASTTemplateDeclaration) {
                    ICPPASTTemplateDeclaration impl = (ICPPASTTemplateDeclaration) node;
                    json.writeStringField("Owned_Declaration", impl.OWNED_DECLARATION.toString());
                    json.writeStringField("Parameter", impl.PARAMETER.toString());
                    json.writeBooleanField("IsExported", impl.isExported());
                }

                if (node instanceof ICPPASTStaticAssertDeclaration) {
                    ICPPASTStaticAssertDeclaration impl = (ICPPASTStaticAssertDeclaration) node;
                    json.writeStringField("Condition", impl.CONDITION.toString());
                    json.writeStringField("Message", impl.MESSAGE.toString());
                }

                if (node instanceof ICPPASTNamespaceDefinition) {
                    ICPPASTNamespaceDefinition impl = (ICPPASTNamespaceDefinition) node;
                    json.writeStringField("Namespace_Name", impl.NAMESPACE_NAME.toString());
                    json.writeStringField("Owned_Declaration", impl.OWNED_DECLARATION.toString());
                    json.writeBooleanField("IsInline", impl.isInline());
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                }

                if (node instanceof ICPPASTNamespaceAlias) {
                    ICPPASTNamespaceAlias impl = (ICPPASTNamespaceAlias) node;
                    json.writeStringField("Alias_Name", impl.ALIAS_NAME.toString());
                    json.writeStringField("Mapping_Name", impl.MAPPING_NAME.toString());
//                    serializeIASTNameOwner((IASTNameOwner)node);
                }

                if (node instanceof IASTSimpleDeclaration) {
                    IASTSimpleDeclaration impl = (IASTSimpleDeclaration) node;
                    json.writeStringField("Decl_Specifier", impl.DECL_SPECIFIER.toString());
                    json.writeStringField("Declarator", impl.DECLARATOR.toString());
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                }

                if (node instanceof ICPPASTAliasDeclaration) {
                    ICPPASTAliasDeclaration impl = (ICPPASTAliasDeclaration) node;
                    json.writeStringField("Alias_Name", impl.ALIAS_NAME.toString());
                    json.writeStringField("Target_TypeID", impl.TARGET_TYPEID.toString());
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("Attribute_Specifier", ((IASTAttributeOwner) node).ATTRIBUTE_SPECIFIER.toString());
                }

                if (node instanceof ICPPASTLinkageSpecification) {
                    ICPPASTLinkageSpecification impl = (ICPPASTLinkageSpecification) node;
                    json.writeStringField("Owned_Declaration", impl.OWNED_DECLARATION.toString());
                    json.writeStringField("Literal", impl.getLiteral());
                }

                if (node instanceof IASTFunctionDefinition) {
                    IASTFunctionDefinition impl = (IASTFunctionDefinition) node;
                    json.writeStringField("Decl_Specifier", impl.DECL_SPECIFIER.toString());
                    json.writeStringField("Declarator", impl.DECLARATOR.toString());
                    json.writeStringField("Function_Body", impl.FUNCTION_BODY.toString());

                    if (node instanceof ICPPASTFunctionDefinition) {
                        ICPPASTFunctionDefinition impl2 = (ICPPASTFunctionDefinition) node;
                        json.writeStringField("Member_Initializer", impl2.MEMBER_INITIALIZER.toString());
                        json.writeStringField("Attribute_Specifier", impl2.ATTRIBUTE_SPECIFIER.toString());
                        json.writeBooleanField("IsDefaulted", impl2.isDefaulted());
                        json.writeBooleanField("IsDeleted", impl2.isDeleted());
                    }

                    if (node instanceof ICPPASTFunctionWithTryBlock) {
                        ICPPASTFunctionWithTryBlock impl2 = (ICPPASTFunctionWithTryBlock) node;
                        json.writeStringField("Catch_Handler", impl2.CATCH_HANDLER.toString());
                        json.writeStringField("Attribute_Specifier", impl2.ATTRIBUTE_SPECIFIER.toString());
                    }
                }

                serializeComments(node);
                visitChilds(node);
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
                json.writeStringField("DECLARATOR_NAME", node.DECLARATOR_NAME.toString());
                json.writeStringField("INITIALIZER", node.INITIALIZER.toString());
                json.writeStringField("NESTED_DECLARATOR", node.NESTED_DECLARATOR.toString());
                json.writeStringField("POINTER_OPERATOR", node.POINTER_OPERATOR.toString());
//                serializeIASTNameOwner(node);
                json.writeStringField("Attribute_Specifier", node.ATTRIBUTE_SPECIFIER.toString());

                if (node instanceof IASTStandardFunctionDeclarator) {
                    IASTStandardFunctionDeclarator impl = (IASTStandardFunctionDeclarator) node;
                    json.writeStringField("FUNCTION_PARAMETER", impl.FUNCTION_PARAMETER.toString());
                    json.writeBooleanField("TakesVarArgs", impl.takesVarArgs());

                    if (node instanceof ICPPASTFunctionDeclarator) {
                        ICPPASTFunctionDeclarator impl2 = (ICPPASTFunctionDeclarator) node;
                        json.writeStringField("EXCEPTION_TYPEID", impl2.EXCEPTION_TYPEID.toString());
                        json.writeStringField("NOEXCEPT_EXPRESSION", impl2.NOEXCEPT_EXPRESSION.toString());
                        json.writeStringField("TRAILING_RETURN_TYPE", impl2.TRAILING_RETURN_TYPE.toString());
                        json.writeStringField("VIRT_SPECIFIER", impl2.VIRT_SPECIFIER.toString());
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
                visitChilds(node);
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
                json.writeStringField("Attribute_Specifier", node.ATTRIBUTE_SPECIFIER.toString());
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
                    json.writeStringField("ALIGNMENT_SPECIFIER", impl.ALIGNMENT_SPECIFIER.toString());
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
                    json.writeStringField("MEMBER_DECLARATION", impl.MEMBER_DECLARATION.toString());
                    json.writeStringField("TYPE_NAME", impl.TYPE_NAME.toString());

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
                        json.writeStringField("BASE_SPECIFIER", impl2.BASE_SPECIFIER.toString());
                        json.writeStringField("CLASS_VIRT_SPECIFIER", impl2.CLASS_VIRT_SPECIFIER.toString());

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
//                    serializeIASTNameOwner((IASTNameOwner)node);
                    json.writeStringField("TYPE_NAME", impl.TYPE_NAME.toString());

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
                    json.writeStringField("DECLTYPE_EXPRESSION", impl.DECLTYPE_EXPRESSION.toString());
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

                if (node instanceof IASTEnumerationSpecifier) {
                    IASTEnumerationSpecifier impl = (IASTEnumerationSpecifier) node;
                    json.writeStringField("ENUMERATION_NAME", impl.ENUMERATION_NAME.toString());
                    json.writeStringField("ENUMERATOR", impl.ENUMERATOR.toString());
//                    serializeIASTNameOwner((IASTNameOwner)node);

                    if (node instanceof ICPPASTEnumerationSpecifier) {
                        ICPPASTEnumerationSpecifier impl2 = (ICPPASTEnumerationSpecifier) node;
                        json.writeStringField("BASE_TYPE", impl2.BASE_TYPE.toString());
                        json.writeBooleanField("IsOpaque", impl2.isOpaque());
                        json.writeBooleanField("IsScoped", impl2.isScoped());
                    }
                }

                if (node instanceof IASTNamedTypeSpecifier) {
                    IASTNamedTypeSpecifier impl = (IASTNamedTypeSpecifier) node;
                    json.writeStringField("NAME", impl.NAME.toString());

                    if (node instanceof ICPPASTNamedTypeSpecifier) {
                        ICPPASTNamedTypeSpecifier impl2 = (ICPPASTNamedTypeSpecifier) node;
                        json.writeBooleanField("IsTypeName", impl2.isTypename());
                    }
                }

                if (node instanceof ICPPASTTypeTransformationSpecifier) {
                    ICPPASTTypeTransformationSpecifier impl = (ICPPASTTypeTransformationSpecifier) node;
                    json.writeStringField("OPERAND", impl.OPERAND.toString());
                    json.writeStringField("Operator", impl.getOperator().toString());
                }

                serializeComments(node);
                visitChilds(node);
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
                if (node instanceof ICPPASTDesignatedInitializer) {
                    ICPPASTDesignatedInitializer impl = (ICPPASTDesignatedInitializer) node;
                    json.writeStringField("Designator", impl.DESIGNATOR.toString());
                    json.writeStringField("Operand", impl.OPERAND.toString());
                }

                if (node instanceof ICASTDesignatedInitializer) {
                    ICASTDesignatedInitializer impl = (ICASTDesignatedInitializer) node;
                    json.writeStringField("Designator", impl.DESIGNATOR.toString());
                    json.writeStringField("Operand", impl.OPERAND.toString());
                }

                if (node instanceof ICPPASTConstructorInitializer) {
                    ICPPASTConstructorInitializer impl = (ICPPASTConstructorInitializer) node;
                    json.writeStringField("Argument", impl.ARGUMENT.toString());
                }

                if (node instanceof ICPPASTConstructorChainInitializer) {
                    ICPPASTConstructorChainInitializer impl = (ICPPASTConstructorChainInitializer) node;
                    json.writeStringField("Initializer", impl.INITIALIZER.toString());
                    json.writeStringField("Member_Id", impl.MEMBER_ID.toString());
                }

                if (node instanceof IASTEqualsInitializer) {
                    IASTEqualsInitializer impl = (IASTEqualsInitializer) node;
                    json.writeStringField("Initializer", impl.INITIALIZER.toString());

                }

                if (node instanceof IASTInitializerList) {
                    IASTInitializerList impl = (IASTInitializerList) node;
                    json.writeStringField("Nested_Initializer", impl.NESTED_INITIALIZER.toString());
                    json.writeNumberField("Size", impl.getSize());
                }

                serializeComments(node);
                visitChilds(node);
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
                json.writeStringField("Attribute_Specifier", node.ATTRIBUTE_SPECIFIER.toString());

                if (node instanceof IASTPointer) {
                    IASTPointer impl = (IASTPointer) node;
                    json.writeBooleanField("IsConst", impl.isConst());
                    json.writeBooleanField("IsRestrict", impl.isRestrict());
                    json.writeBooleanField("IsVolatile", impl.isVolatile());

                    if (node instanceof ICPPASTPointerToMember) {
                        ICPPASTPointerToMember impl2 = (ICPPASTPointerToMember) node;
                        json.writeStringField("NAME", impl2.NAME.toString());
                    }
                }

                if (node instanceof ICPPASTReferenceOperator) {
                    ICPPASTReferenceOperator impl = (ICPPASTReferenceOperator) node;
                    json.writeBooleanField("IsRValueReference", impl.isRValueReference());
                }

                serializeComments(node);
                visitChilds(node);
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
                json.writeStringField("Attribute_Specifier", node.ATTRIBUTE_SPECIFIER.toString());

                if (node instanceof IASTCaseStatement) {
                    IASTCaseStatement impl = (IASTCaseStatement) node;
                    json.writeStringField("EXPRESSION", impl.EXPRESSION.toString());
                }

                if (node instanceof IASTExpressionStatement) {
                    IASTExpressionStatement impl = (IASTExpressionStatement) node;
                    json.writeStringField("EXPRESSION", impl.EXPRESSION.toString());
                }

                if (node instanceof IASTCompoundStatement) {
                    IASTCompoundStatement impl = (IASTCompoundStatement) node;
                    json.writeStringField("NESTED_STATEMENT", impl.NESTED_STATEMENT.toString());
                }

                if (node instanceof IASTGotoStatement) {
                    IASTGotoStatement impl = (IASTGotoStatement) node;
                    json.writeStringField("NAME", impl.NAME.toString());
                }

                if (node instanceof IASTDeclarationStatement) {
                    IASTDeclarationStatement impl = (IASTDeclarationStatement) node;
                    json.writeStringField("DECLARATION", impl.DECLARATION.toString());
                }

                if (node instanceof IASTReturnStatement) {
                    IASTReturnStatement impl = (IASTReturnStatement) node;
                    json.writeStringField("RETURNVALUE", impl.RETURNVALUE.toString());
                }

                if (node instanceof IASTDoStatement) {
                    IASTDoStatement impl = (IASTDoStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CONDITION", impl.CONDITION.toString());
                }

                if (node instanceof IASTWhileStatement) {
                    IASTWhileStatement impl = (IASTWhileStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CONDITIONEXPRESSION", impl.CONDITIONEXPRESSION.toString());

                    if (node instanceof ICPPASTWhileStatement) {
                        ICPPASTWhileStatement impl2 = (ICPPASTWhileStatement) node;
                        json.writeStringField("CONDITIONDECLARATION", impl2.CONDITIONDECLARATION.toString());
                    }
                }

                if (node instanceof IASTSwitchStatement) {
                    IASTSwitchStatement impl = (IASTSwitchStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CONTROLLER_EXP", impl.CONTROLLER_EXP.toString());

                    if (node instanceof ICPPASTSwitchStatement) {
                        ICPPASTSwitchStatement impl2 = (ICPPASTSwitchStatement) node;
                        json.writeStringField("CONTROLLER_DECLARATION", impl2.CONTROLLER_DECLARATION.toString());
                        json.writeStringField("INIT_STATEMENT", impl2.INIT_STATEMENT.toString());
                    }
                }

                if (node instanceof IASTLabelStatement) {
                    IASTLabelStatement impl = (IASTLabelStatement) node;
                    json.writeStringField("NAME", impl.NAME.toString());
                    json.writeStringField("NESTED_STATEMENT", impl.NESTED_STATEMENT.toString());
                }

                if (node instanceof IASTIfStatement) {
                    IASTIfStatement impl = (IASTIfStatement) node;
                    json.writeStringField("CONDITION", impl.CONDITION.toString());
                    json.writeStringField("THEN", impl.THEN.toString());
                    json.writeStringField("ELSE", impl.ELSE.toString());

                    if (node instanceof ICPPASTIfStatement) {
                        ICPPASTIfStatement impl2 = (ICPPASTIfStatement) node;
                        json.writeStringField("INIT_STATEMENT", impl2.INIT_STATEMENT.toString());
                    }
                }

                if (node instanceof ICPPASTCatchHandler) {
                    ICPPASTCatchHandler impl = (ICPPASTCatchHandler) node;
                    json.writeStringField("CATCH_BODY", impl.CATCH_BODY.toString());
                    json.writeStringField("DECLARATION", impl.DECLARATION.toString());
                    json.writeBooleanField("IsCatchAll", impl.isCatchAll());
                }

                if (node instanceof IASTForStatement) {
                    IASTForStatement impl = (IASTForStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CONDITION", impl.CONDITION.toString());
                    json.writeStringField("INITIALIZER", impl.INITIALIZER.toString());
                    json.writeStringField("ITERATION", impl.ITERATION.toString());

                    if (node instanceof ICPPASTForStatement) {
                        ICPPASTForStatement impl2 = (ICPPASTForStatement) node;
                        json.writeStringField("CONDITION_DECLARATION", impl2.CONDITION_DECLARATION.toString());
                    }
                }

                if (node instanceof ICPPASTRangeBasedForStatement) {
                    ICPPASTRangeBasedForStatement impl = (ICPPASTRangeBasedForStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("DECLARATION", impl.DECLARATION.toString());
                    json.writeStringField("INITIALIZER", impl.INITIALIZER.toString());
                }

                if (node instanceof ICPPASTTryBlockStatement) {
                    ICPPASTTryBlockStatement impl = (ICPPASTTryBlockStatement) node;
                    json.writeStringField("BODY", impl.BODY.toString());
                    json.writeStringField("CATCH_HANDLER", impl.CATCH_HANDLER.toString());
                }

                serializeComments(node);
                visitChilds(node);
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

                if (node instanceof IASTTokenList) {
                    json.writeStringField("NESTED_TOKEN", ((IASTTokenList)node).NESTED_TOKEN.toString());
                }

                serializeComments(node);
                visitChilds(node);
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
                visitChilds(node);
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
                json.writeStringField("Abstract_Declarator", node.ABSTRACT_DECLARATOR.toString());
                json.writeStringField("Decl_Specifier", node.DECL_SPECIFIER.toString());
                serializeComments(node);
                visitChilds(node);
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
                visitChilds(node);
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
                json.writeStringField("Name_Specifier", node.NAME_SPECIFIER.toString());
                // getVisibility
                json.writeBooleanField("IsVirtual", node.isVirtual());
                serializeComments(node);
                visitChilds(node);
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

                if (node instanceof ICPPASTSimpleTypeTemplateParameter) {
                    ICPPASTSimpleTypeTemplateParameter impl = (ICPPASTSimpleTypeTemplateParameter) node;
                    json.writeStringField("Default_Type", impl.DEFAULT_TYPE.toString());
                    json.writeStringField("Parameter_Name", impl.PARAMETER_NAME.toString());
                    // getParameterType
                }

                if (node instanceof ICPPASTTemplatedTypeTemplateParameter) {
                    ICPPASTTemplatedTypeTemplateParameter impl = (ICPPASTTemplatedTypeTemplateParameter) node;
                    json.writeStringField("Default_Value", impl.DEFAULT_VALUE.toString());
                    json.writeStringField("Parameter", impl.PARAMETER.toString());
                    json.writeStringField("Parameter_Name", impl.PARAMETER_NAME.toString());
                    // asScope
                }

                serializeComments(node);
                visitChilds(node);
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
                json.writeStringField("Attribute", node.ATTRIBUTE.toString());
                serializeComments(node);
                visitChilds(node);
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
