package tech.sourced.babelfish;

import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.cpp.ICPPASTFunctionDeclarator;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFunctionDeclarator;

// TODO: tree structure

// TODO: call getMacroDefinitions, getBuiltinMacroDefinition, getIncludeDirectives,
// TODO: getAllPreprocessorStatement, getMacroExpansions, insert them in the tree
// TODO: call getComments, insert them in the tree or the nodes

// TODO: test getPreprocessorComments, isHeaderUnit, getOriginalTranslationUnit,
// TODO: hasNodesOmitted

public class CPPASTVisitor extends ASTVisitor {
    private int level = 0;

    CPPASTVisitor() {
        super(true);
//        includeInactiveNodes = true;
//        includeInactiveNodes = true;
//        shouldVisitAmbiguousNodes = true;
//        shouldVisitArrayModifiers = true;
//        shouldVisitAttributes = true;
//        shouldVisitBaseSpecifiers = true;
//        shouldVisitCaptures = true;
//        shouldVisitDeclSpecifiers = true;
//        shouldVisitDeclarations = true; // XXX test
//        shouldVisitDeclarators = true;
//        shouldVisitDesignators = true;
//        shouldVisitEnumerators = true;
//        shouldVisitExpressions = true;
//        shouldVisitImplicitNameAlternates = true;
//        shouldVisitImplicitNames = true;
//        shouldVisitInitializers = true;
//        shouldVisitNames = true;
//        shouldVisitNamespaces = true;
//        shouldVisitParameterDeclarations = true;
//        shouldVisitPointerOperators = true;
//        shouldVisitProblems = true; // XXX test
//        shouldVisitStatements = true;
//        shouldVisitStatements = true;
//        shouldVisitTemplateParameters = true;
//        shouldVisitTokens = true; // XXX test
//        shouldVisitTranslationUnit = true; // XXX test
//        shouldVisitTypeIds = true;
//        shouldVisitSpecifiers = true; // XXX update to 5.7
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

    public void echo(String msg) {
        String prefix = new String(new char[level]).replace("\0", "-");
        System.out.println(prefix + msg);
    }

    public static String getSnippet(IASTNode node) {
        String snippet = node.getRawSignature().replaceAll("\n", " \\ ");
        int maxlen = node.getRawSignature().length();
        if (maxlen > 20) maxlen = 20;
        return snippet.substring(0, maxlen) + (maxlen == 20 ? "..." : "");
    }

    public int visitChildren(IASTNode node)
    {
        IASTNode[] children = node.getChildren();
        if ((children != null) && (children.length > 0)) {
            for (IASTNode child : children)
                child.accept(this);
//                visit(child);
        }
       return 3;
    }

    public int visit(IASTNode node)
    {
        echo("Visit.Node (default): " + getSnippet(node));
        echo("ToStr: " + node.getClass().getSimpleName());
        echo("Location: " + node.getFileLocation().toString());
        echo("Snippet: " + getSnippet(node));
        echo("\n");
        // This one gives the node BEFORE being preproccesed
//        getSnippet(node);
        // This get the tokens of the node
//        node.getSyntax(); // FIXME: in a try-except

        // These can be used to get comments and whitespace!
//        node.getLeadingSyntax();
//        node.getTrailingSyntax();

        visitChildren(node);
        return 3;
    }

    public int visit(IASTName name)
    {
        echo("Visit.Name:" + getSnippet(name));
        if ((name.getParent() instanceof CPPASTFunctionDeclarator)) {
            echo("Visit.Name.FunctionDeclarator: " + name.getClass().getSimpleName() + "(" +
                    getSnippet(name) + ") >> parent: " + name.getParent().getClass().getSimpleName());
            echo("-- isVisible: " + EclipseCPPParser.isVisible(name));
        }
        return 3;
    }

    public int visit(IASTDeclaration declaration)
    {
        // FIXME: check the other possible classes that the declaration can be instance of and cover them
        // FIXME: visit the childs calling the visit method


        echo("Visit.Declaration: " + declaration + " [[" + getSnippet(declaration) + "]]");

        if ((declaration instanceof IASTSimpleDeclaration)) {
            echo("  Visit.Declaration.SimpleDeclaration: ");
            IASTSimpleDeclaration ast = (IASTSimpleDeclaration)declaration;
            try
            {
                echo(" type: " + ast.getSyntax() + " (childs: " + ast.getChildren().length + ")");

                IASTNode typedef = ast.getChildren().length == 1 ? ast.getChildren()[0] : ast.getChildren()[1];
                echo(" typedef: " + typedef);
                IASTNode[] declChildren = ast.getChildren();
                if ((declChildren != null) && (declChildren.length > 0)) {
                    for (IASTNode child : declChildren)
                        visit(child);
                }

                IASTNode[] children = typedef.getChildren();
                if ((children != null) && (children.length > 0)) {
                    echo(" typedef-name: " + children[0].getRawSignature());

                    for (IASTNode child : children)
                        child.accept(this);
//                        visit(child);
                }

            }
            catch (ExpansionOverlapsBoundaryException e)
            {
                e.printStackTrace();
            }

            IASTDeclarator[] declarators = ast.getDeclarators();
            for (IASTDeclarator iastDeclarator : declarators) {
                echo(" Declarator >> " + iastDeclarator.getName());
                iastDeclarator.accept(this);
//                visit(iastDeclarator);
            }

            IASTAttribute[] attributes = ast.getAttributes();
            for (IASTAttribute iastAttribute : attributes) {
                echo(" Attribute >> " + iastAttribute);
                iastAttribute.accept(this);
//                visit(iastAttribute);
            }

        }

        if ((declaration instanceof IASTFunctionDefinition)) {
            // FIXME: children
            echo("Visit.FunctionDefinition: " + getSnippet(declaration));
            IASTFunctionDefinition ast = (IASTFunctionDefinition)declaration;
            IScope scope = ast.getScope();
            try
            {
                echo(" Parent = " + scope.getParent().getScopeName());
                echo(" Syntax = " + ast.getSyntax());
            }
            catch (DOMException e) {
                e.printStackTrace();
            } catch (ExpansionOverlapsBoundaryException e) {
                e.printStackTrace();
            }
            ICPPASTFunctionDeclarator typedef = (ICPPASTFunctionDeclarator)ast.getDeclarator();
            echo(" typedef: " + typedef.getName());
        }

        return 3;
    }

    public int visit(IASTTypeId typeId)
    {
        echo("Visit.TypeId: " + getSnippet(typeId));
       return 3;
    }

    public int visit(IASTStatement statement)
    {
        echo("Visit.Statement: " + getSnippet(statement));
       return 3;
    }

    public int visit(IASTAttribute attribute)
    {
        echo("Visit.Attribute: " + getSnippet(attribute));
       return 3;
    }

    public int visit(IASTInitializer initializer)
    {
        echo("Visit.Initializer: " + getSnippet(initializer));
       return 3;
    }

    public int visit(IASTParameterDeclaration paramdecl)
    {
        echo("Visit.ParameterDeclaration: " + getSnippet(paramdecl));
       return 3;
    }
    public int visit(IASTDeclarator decl)
    {
        echo("Visit.Declarator: " + getSnippet(decl));
       return 3;
    }
    public int visit(IASTDeclSpecifier decl)
    {
        echo("Visit.DeclSpecifier: " + getSnippet(decl));
       return 3;
    }
    public int visit(IASTArrayModifier arrm)
    {
        echo("Visit.ArrayModifier: " + getSnippet(arrm));
        return 3;
    }
    public int visit(IASTPointerOperator pointer)
    {

        echo("Visit.PointerOperator: " + getSnippet(pointer));
        return 3;
    }
// FIXME: enable once updated to 5.7
//    public int visit(IASTAttributeSpecifier attrSpec)
//    {
//        echo("Visit.AttributeSpecifier: " + getSnippet(attrSpec));
//        return 3;
//    }

    public int visit(IASTToken token)
    {
        echo("Visit.Token: " + getSnippet(token));
        return 3;
    }
}
