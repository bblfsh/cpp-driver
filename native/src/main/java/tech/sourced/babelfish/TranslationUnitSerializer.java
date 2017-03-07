package tech.sourced.babelfish;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.eclipse.cdt.core.dom.ast.*;

import java.io.IOException;

/**
 * Custom Jackson serializer for org.eclipse.cdt.core.dom.IASTTranslationUnit
 */
public class TranslationUnitSerializer extends StdSerializer<IASTTranslationUnit>
{
    TranslationUnitSerializer() {
        this(null);
    }

    private TranslationUnitSerializer(Class<IASTTranslationUnit> t) {
        super(t);
    }

    @Override
    public void serialize(IASTTranslationUnit translationUnit, JsonGenerator jsonGenerator,
                          SerializerProvider provider) throws IOException {

        System.err.println("XXX in TUSerializer.serialize");
        serializeNode(translationUnit, jsonGenerator);
        // TODO: close the jsonGenerator? Check that this doesnt close the associated
        // outputstream
    }

    private void serializeNode(IASTNode node, JsonGenerator json) throws IOException {
        json.writeStartObject();
        json.writeFieldName("IASTClass");
        json.writeString(node.getClass().getSimpleName());

        json.writeFieldName("Snippet");
        json.writeString(EclipseCPPParser.getSnippet(node));

        json.writeFieldName("Location");
        json.writeString(EclipseCPPParser.getNodeLocationStr(node));

        ASTNodeProperty propInParent = node.getPropertyInParent();
        if (propInParent != null) {
            json.writeFieldName("Role");
            json.writeString(propInParent.getName());
        }

        if (node instanceof IASTLiteralExpression) {
            IASTLiteralExpression lit = (IASTLiteralExpression) node;

            json.writeFieldName("LiteralKind");
            json.writeString(lit.getExpressionType().toString());

            json.writeFieldName("LiteralValue");
            json.writeString(lit.toString());

        }

        if (node instanceof IASTName) {
            IASTName name = (IASTName) node;
            json.writeFieldName("SymbolName");
            json.writeString(node.toString());
        }

        // FIXME: add the comments, how can we access commentMap without an instance? Probably
        // I need to create a boxing class for the IASTTranslationUnit with a reference to the commentMap.
        // This will be returned but the parseCPP method

        // Leading comments
//        jsonGenerator.writeStartObject();
//        jsonGenerator.writeString("LeadingComments");


        IASTNode[] children = node.getChildren();
        if (children != null && children.length > 0) {
            json.writeFieldName("childs");
            json.writeStartArray();
            for (IASTNode child : children) {
                serializeNode(child, json);
            }
            json.writeEndArray();
        }
        json.writeEndObject();
    }
}
