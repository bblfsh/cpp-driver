package tech.sourced.babelfish;

import com.fasterxml.jackson.core.JsonGenerator;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.c.ICASTArrayModifier;
import org.eclipse.cdt.core.dom.ast.c.ICASTDesignator;
import org.eclipse.cdt.core.dom.ast.cpp.*;
import org.eclipse.cdt.internal.core.dom.rewrite.commenthandler.NodeCommentMap;

import java.io.IOException;
import java.util.List;

// FIXME: Review all nodes implemented on the ASTVisitor interface
// and do the isinstance of their subclasses inside all call the
// right methods on them, renaming them from visit to visit_something

// Visitor pattern implementation for the CPP AST. This will write every
// node in the Json output.
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

    // We need to call this on each visitor instead of just retuning
    // PROCESS_SKIP and let the base clase do it automatically because
    // we need to start the child JSON array
    public void visitChilds(IASTNode node) throws IOException {
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

    public int visit(IASTNode node)
    {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;

    }

// TEMPLATE, FIXME: remove
//    public int visit(IASTLiteralExpression node) {
//        try {
//            serializeCommonData(node);
//            serializeComments(node);
//        } catch (IOException e) {
//            enableErrorState(e);
//            return PROCESS_ABORT;
//        }
//        return PROCESS_SKIP;
//    }

    @Override
    public int visit(IASTName node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("Name", node.toString());

                if (node instanceof IASTImplicitName) {
                    IASTImplicitName impl = (IASTImplicitName) node;
                    json.writeStringField("IsAlternate", String.valueOf(impl.isAlternate()));
                    json.writeStringField("IsOverloadedOperator", String.valueOf(impl.isOperator()));
                }

                // FIXME: Missing specific node checks & fields:
                // this .getLastName .getRoleName
                // ICPPASTConversionName.getTypeId
                // IASTImplicitDestructorName.getConstructionPoint
                // ICPPASTTemplateId.getTemplateArguments? (could be childs), getTemplateName?

                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    public int XXX(IASTASMDeclaration node) {
        try {
            serializeCommonData(node);
            json.writeStringField("ASMContent", node.getAssembly());
            serializeComments(node);
            visitChilds(node);
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    public void serializeBinaryExpression(IASTBinaryExpression node) throws IOException {
        int op = node.getOperator();
        String opStr = "UNKOWN OPERATOR WITH CODE " + Integer.toString(op);
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
        }
        json.writeFieldName("Operator");
        json.writeString(opStr);
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
                    serializeBinaryExpression((IASTBinaryExpression) node);
                }

                if (node instanceof IASTLiteralExpression) {
                    IASTLiteralExpression lit = (IASTLiteralExpression) node;
                    json.writeStringField("LiteralKind", lit.getExpressionType().toString());
                    json.writeStringField("LiteralValue", lit.toString());
                }

                // FIXME missing:
                // TypeIdExpression .getOperator
                // BinaryTypeIdExpression .getOperator
                // CastExpression .getOperator
                // FieldReference .isPointerDereference (pointer instead of arrow)
                // DeleteExpression .isGlobal .isVectored
                // ICPPASTNaryTypeIdExpression .getOperator
                // ICPPASTNewExpression .getTypeId .isArrayAllocation .isGlobal .isNewTypeId,
                // ICPPASTUnaryExpression .getOverload .getOperator

                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    // FIXME: update the cdt jar to have these too
    //int	visit(IASTAttributeSpecifier specifier)
    //int	visit(ICPPASTDesignator designator)
    //int	visit(ICPPASTVirtSpecifier virtSpecifier)

    @Override
    public int	visit(IASTArrayModifier node) {
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
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTAttribute node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                json.writeStringField("AttrName", String.valueOf(node.getName()));

                // FIXME missing:
                // ICPPASTAttribute .getScope .hasPackExpansion
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    // XXX continue here
    @Override
    public int	visit(IASTDeclaration node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTDeclarator node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTDeclSpecifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTInitializer node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTParameterDeclaration node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTPointerOperator node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTProblem node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTStatement node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTToken node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTTranslationUnit node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(IASTTypeId node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(ICASTDesignator node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(ICPPASTCapture node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(ICPPASTCompositeTypeSpecifier.ICPPASTBaseSpecifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

//    @Override
    public int	visit(ICPPASTDecltypeSpecifier node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(ICPPASTNamespaceDefinition node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }

    @Override
    public int	visit(ICPPASTTemplateParameter node) {
        try {
            json.writeStartObject();
            try {
                serializeCommonData(node);
                serializeComments(node);
                visitChilds(node);
            } finally { json.writeEndObject(); }
        } catch (IOException e) {
            enableErrorState(e);
            return PROCESS_ABORT;
        }
        return PROCESS_SKIP;
    }
}
