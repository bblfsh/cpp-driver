package normalizer

import (
	"gopkg.in/bblfsh/sdk.v2/uast"
	"gopkg.in/bblfsh/sdk.v2/uast/nodes"
	. "gopkg.in/bblfsh/sdk.v2/uast/transformer"
	"strings"
)

var Preprocess = Transformers([][]Transformer{
	{
		ResponseMetadata{
			TopLevelIsRootNode: false,
		},
	},
	{Mappings(Preprocessors...)},
}...)

var Normalize = Transformers([][]Transformer{
	{Mappings(Normalizers...)},
}...)

var Preprocessors = []Mapping{
	ObjectToNode{
		InternalTypeKey: "IASTClass",
		OffsetKey:       "LocOffsetStart",
		EndOffsetKey:    "LocOffsetEnd",
	}.Mapping(),
}

func mapIASTNameDerived(typ string) Mapping {
	return MapSemantic(typ, uast.Identifier{}, MapObj(
		Obj{
			"Name": Var("name"),
		},
		Obj{
			"Name": Var("name"),
		},
	))
}

type opJoinNamesArray struct {
	qualified Op
}

func (op opJoinNamesArray) Kinds() nodes.Kind {
	return nodes.KindString
}

func (op opJoinNamesArray) Check(st *State, n nodes.Node) (bool, error) {
	res, err := op.qualified.Check(st, n)
	if err != nil || !res {
		return false, err
	}

	return true, nil
}

func (op opJoinNamesArray) Construct(st *State, n nodes.Node) (nodes.Node, error) {
	names, err := op.qualified.Construct(st, n)
	if err != nil || names == nil {
		return n, err
	}

	namesarr, ok := names.(nodes.Array)
	if !ok && namesarr != nil {
		return nil, ErrExpectedList.New(namesarr)
	}

	var tokens []string
	for _, ident := range namesarr {
		idento, ok := ident.(nodes.Object)
		if !ok {
			return nil, ErrExpectedObject.New(ident)
		}

		strname, ok := idento["Name"].(nodes.String)
		if !ok {
			return nil, ErrExpectedValue.New(idento["Name"])
		}

		tokens = append(tokens, string(strname))
	}

	return nodes.String(strings.Join(tokens, "::")), nil
}

var _ Op = opJoinNamesArray{}

var Normalizers = []Mapping{

	MapSemantic("ASTInclusionStatement", uast.InlineImport{}, MapObj(
		Obj{
			"Name": Var("path"),
		},
		Obj{
			"Path":  UASTType(uast.Identifier{}, Obj{"Name": Var("path")}),
			"All":   Bool(true),
			"Names": Arr(),
		},
	)),

	MapSemantic("CPPASTCompoundStatement", uast.Block{}, MapObj(
		Obj{
			"Prop_Statements": Var("statements"),
		},
		Obj{
			"Statements": Var("statements"),
		},
	)),

	// Empty {}
	MapSemantic("CPPASTCompoundStatement", uast.Block{}, MapObj(
		Obj{},
		Obj{"Statements": Arr()},
	)),

	MapSemantic("CPPASTLiteralExpression", uast.String{}, MapObj(
		Obj{
			"LiteralValue": Quote(Var("val")),
			"kind":         String("string_literal"),
		},
		Obj{
			"Value":  Var("val"),
			"Format": String(""),
		},
	)),

	MapSemantic("Comment", uast.Comment{}, MapObj(
		Obj{
			"Comment":        CommentText([2]string{"/*", "*/"}, "comm"),
			"IsBlockComment": Bool(true),
		},
		CommentNode(true, "comm", nil),
	)),

	MapSemantic("Comment", uast.Comment{}, MapObj(
		Obj{
			"Comment":        CommentText([2]string{"//", ""}, "comm"),
			"IsBlockComment": Bool(false),
		},
		CommentNode(false, "comm", nil),
	)),

	// Args in C can have type but be empty (typically in headers, but also in implementations): int main(int, char**)
	Map(Obj{
		"IASTClass": String("CPPASTName"),
		"Name":      String(""),
	}, Obj{
		uast.KeyType: String("uast:Identifier"),
		"Name":       String(""),
	}),

	mapIASTNameDerived("CPPASTName"),
	mapIASTNameDerived("CPPASTOperatorName"),
	AnnotateType("CPPASTTemplateId", MapObj(
		Obj{
			"Name": Var("name"),
		},
		Obj{
			"Name": UASTType(uast.Identifier{}, Obj{
				"Name": Var("name"),
			}),
		},
	)),
	AnnotateType("CPPASTConversionName", MapObj(
		Obj{
			"Name": Var("name"),
		},
		Obj{
			"Name": UASTType(uast.Identifier{}, Obj{
				"Name": Var("name"),
			}),
		},
	)),

	MapSemantic("CPPASTQualifiedName", uast.QualifiedIdentifier{}, MapObj(
		Obj{
			"Prop_AllSegments": Each("qualParts", Cases("caseQualParts",
				Fields{
					{Name: uast.KeyType, Op: String("uast:Identifier")},
					{Name: uast.KeyPos, Op: Var("qualItemPos")},
					{Name: "Name", Op: Var("name")},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTTemplateId")},
					{Name: uast.KeyPos, Op: AnyNode(nil)},
					{Name: "Name", Op: Obj{
						uast.KeyType: String("uast:Identifier"),
						"Name":       Var("name"),
					}},
					{Name: "Prop_TemplateArguments", Op: AnyNode(nil)},
					{Name: "Prop_TemplateName", Op: AnyNode(nil)},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTConversionName")},
					{Name: uast.KeyPos, Op: AnyNode(nil)},
					{Name: "Name", Op: Obj{
						uast.KeyType: String("uast.Identifier"),
						"Name":       Var("name"),
					}},
				},
			)),
		},
		Obj{
			"Names": Each("qualParts",
				UASTType(uast.Identifier{}, Obj{
					"Name": Var("name"),
				})),
		},
	)),

	MapSemantic("CPPASTFunctionDefinition", uast.FunctionGroup{}, MapObj(
		Fields{
			{Name: "IsDefaulted", Op: AnyNode(nil)},
			{Name: "IsDeleted", Op: AnyNode(nil)},
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: AnyNode(nil)},
					{Name: "IsComplex", Op: AnyNode(nil)},
					{Name: "IsConst", Op: AnyNode(nil)},
					{Name: "IsConstExpr", Op: AnyNode(nil)},
					{Name: "IsExplicit", Op: AnyNode(nil)},
					{Name: "IsFriend", Op: AnyNode(nil)},
					{Name: "IsImaginary", Op: AnyNode(nil)},
					{Name: "IsInline", Op: AnyNode(nil)},
					{Name: "IsLong", Op: AnyNode(nil)},
					{Name: "IsLongLong", Op: AnyNode(nil)},
					{Name: "IsRestrict", Op: AnyNode(nil)},
					{Name: "IsShort", Op: AnyNode(nil)},
					{Name: "IsSigned", Op: AnyNode(nil)},
					{Name: "IsThreadLocal", Op: AnyNode(nil)},
					{Name: "IsUnsigned", Op: AnyNode(nil)},
					{Name: "IsVirtual", Op: AnyNode(nil)},
					{Name: "IsVolatile", Op: AnyNode(nil)},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "Type", Op: Var("retType")},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTNamedTypeSpecifier")},
					{Name: uast.KeyPos, Op: AnyNode(nil)},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "IsConst", Op: AnyNode(nil)},
					{Name: "IsConstExpr", Op: AnyNode(nil)},
					{Name: "IsExplicit", Op: AnyNode(nil)},
					{Name: "IsFriend", Op: AnyNode(nil)},
					{Name: "IsInline", Op: AnyNode(nil)},
					{Name: "IsRestrict", Op: AnyNode(nil)},
					{Name: "IsThreadLocal", Op: AnyNode(nil)},
					{Name: "IsTypeName", Op: AnyNode(nil)},
					{Name: "IsVirtual", Op: AnyNode(nil)},
					{Name: "IsVolatile", Op: AnyNode(nil)},
					{Name: "Prop_Name", Op: Var("retType")},
				},
			)},

			{Name: "Prop_Declarator", Op: Fields{
				{Name: uast.KeyType, Op: String("CPPASTFunctionDeclarator")},
				{Name: uast.KeyPos, Op: Var("fdpos")},
				{Name: "IsConst", Op: AnyNode(nil)},
				{Name: "IsFinal", Op: AnyNode(nil)},
				{Name: "IsMutable", Op: AnyNode(nil)},
				{Name: "IsOverride", Op: AnyNode(nil)},
				{Name: "IsPureVirtual", Op: AnyNode(nil)},
				{Name: "IsVolatile", Op: AnyNode(nil)},

				{Name: "Prop_Name", Op: Cases("caseName",
					Fields{
						{Name: uast.KeyType, Op: String("uast:Identifier")},
						{Name: uast.KeyPos, Op: AnyNode(nil)},
						{Name: "Name", Op: Var("name")},
					},
					Fields{
						{Name: uast.KeyType, Op: String("uast:QualifiedIdentifier")},
						{Name: uast.KeyPos, Op: AnyNode(nil)},
						// FIXME: join the Names into a single string
						{Name: "Names", Op: Var("qualnames")},
					},
				)},

				{Name: "TakesVarArgs", Op: Cases("takesVarArgs", Bool(false), Bool(true))},
				{Name: "Prop_ConstructorChain", Optional: "optConsChain", Op: AnyNode(nil)},
				{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: AnyNode(nil)},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Cases("caseParams",
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: AnyNode(nil)},
						{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: AnyNode(nil)},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTArrayDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: AnyNode(nil)},
						{Name: "Prop_ArrayModifiers", Op: AnyNode(nil)},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
					},
				))},
			}},
		},
		Obj{
			"Nodes": Arr(
				UASTType(uast.Alias{}, Obj{
					"Name": UASTType(uast.Identifier{}, CasesObj("caseName", Obj{},
						Objs{
							// Normal Identifier
							{
								"Name": Var("name"),
							},
							// Qualified Identifier
							{
								"Name": opJoinNamesArray{qualified: Var("qualnames")},
							},
						},
					)),

					"Node": UASTType(uast.Function{}, Fields{
						{Name: "Body", Optional: "optBody", Op: Var("body")},

						{Name: "Type", Op: UASTType(uast.FunctionType{}, Fields{
							{Name: "Returns", Op: Arr(UASTType(uast.Argument{}, CasesObj("retTypeCase", Obj{},
								Objs{
									// SimpleDeclSpecifier
									{
										"Type": UASTType(uast.Identifier{}, Obj{
											"Name": Var("retType"),
										}),
									},
									// NamedTypeSpecifier
									{
										"Type": Var("retType"),
									},
								},
							)))},

							{Name: "Arguments", Optional: "optArgs", Op: Cases("takesVarArgs",
								// False, no varargs
								Each("args", UASTType(uast.Argument{}, Obj{
									"Name": Var("aname"),
									"Type": Var("atype"),
									"Init": If("optInitializer", Var("ainit"), Is(nil)),
								})),
								// True, the last arg is variadic
								Append(
									Each("args", UASTType(uast.Argument{}, Obj{
										"Name": Var("aname"),
										"Type": Var("atype"),
										"Init": If("optInitializer", Var("ainit"), Is(nil)),
									})),
									Arr(
										UASTType(uast.Argument{}, Obj{
											"Variadic": Bool(true),
										}),
									),
								),
							)},
						})},
					}),
				}),
			),
		},
	)),
}
