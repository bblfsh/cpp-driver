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

func prependDotSlash(path string) (string, error) {
	return "./" + path, nil
}

var _ StringFunc = prependDotSlash

func removeDotSlash(path string) (string, error) {
	if strings.HasPrefix(path, "./") {
		return path[2 : len(path)-1], nil
	}

	return path, nil
}

var _ StringFunc = removeDotSlash

type opJoinNamesArray struct {
	qualified Op
}

func (op opJoinNamesArray) Kinds() nodes.Kind {
	return nodes.KindString
}

func (op opJoinNamesArray) Check(st *State, n nodes.Node) (bool, error) {
	// Reverse op: split string into Identifiers, load the QualifiedIdentifer and check it.
	s, ok := n.(nodes.String)
	if !ok {
		return false, ErrExpectedValue.New(n)
	}

	tokens := strings.Split(string(s), "::")
	var names []uast.Identifier

	for _, t := range tokens {
		id := uast.Identifier{Name: t}
		names = append(names, id)
	}

	n, err := uast.ToNode(names)
	if err != nil {
		return false, err
	}

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
		CasesObj("isSystemCase", Obj{},
			Objs{
				{
					"Name":     Var("path"),
					"IsSystem": Bool(true),
				},
				{
					"Name":     StringConv(Var("path"), prependDotSlash, removeDotSlash),
					"IsSystem": Bool(false),
				},
			}),
		Obj{
			"Path": UASTType(uast.String{}, Obj{
				"Value":  Var("path"),
				"Format": String(""),
			}),
			"All":   Bool(true),
			"Names": Is(nil),
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
	},
		Is(nil),
	),

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
		Fields{
			{Name: "Prop_AllSegments", Op: Each("qualParts", Cases("caseQualParts",
				Fields{
					{Name: uast.KeyType, Op: String("uast:Identifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "Name", Op: Var("name")},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTTemplateId")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "Name", Op: Obj{
						uast.KeyType: String("uast:Identifier"),
						"Name":       Var("name"),
					}},
					{Name: "Prop_TemplateArguments", Op: Any()},
					{Name: "Prop_TemplateName", Op: Any()},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTConversionName")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "Name", Op: Obj{
						uast.KeyType: String("uast.Identifier"),
						"Name":       Var("name"),
					}},
				},
				Is(nil),
			))},
			// Ignored: already on AllSegments
			{Name: "Prop_Qualifier", Optional: "optPropQual", Op: Any()},
		},
		Obj{
			"Names": Each("qualParts", Cases("caseQualParts",
				UASTType(uast.Identifier{}, Obj{
					"Name": Var("name"),
				}),
				UASTType(uast.Identifier{}, Obj{
					"Name": Var("name"),
				}),
				UASTType(uast.Identifier{}, Obj{
					"Name": Var("name"),
				}),
				Is(nil),
			)),
		},
	)),

	MapSemantic("CPPASTFunctionDefinition", uast.FunctionGroup{}, MapObj(
		Fields{
			{Name: "IsDefaulted", Op: Any()},
			{Name: "IsDeleted", Op: Any()},
			{Name: "ExpandedFromMacro", Optional: "optMacro1", Op: Any()},
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				// FIXME XXX: use an Or("void", "unspecified") or the equivalent
				// void
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "IsComplex", Op: Any()},
					{Name: "IsConst", Op: Any()},
					{Name: "IsConstExpr", Op: Any()},
					{Name: "IsExplicit", Op: Any()},
					{Name: "IsFriend", Op: Any()},
					{Name: "IsImaginary", Op: Any()},
					{Name: "IsInline", Op: Any()},
					{Name: "IsLong", Op: Any()},
					{Name: "IsLongLong", Op: Any()},
					{Name: "IsRestrict", Op: Any()},
					{Name: "IsShort", Op: Any()},
					{Name: "IsSigned", Op: Any()},
					{Name: "IsThreadLocal", Op: Any()},
					{Name: "IsUnsigned", Op: Any()},
					{Name: "IsVirtual", Op: Any()},
					{Name: "IsVolatile", Op: Any()},
					{Name: "StorageClass", Op: Any()},
					{Name: "ExpandedFromMacro", Optional: "optMacro2", Op: Any()},
					{Name: "Type", Op: String("void")},
				},
				// unspecified (ie constructor/destructors)
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "IsComplex", Op: Any()},
					{Name: "IsConst", Op: Any()},
					{Name: "IsConstExpr", Op: Any()},
					{Name: "IsExplicit", Op: Any()},
					{Name: "IsFriend", Op: Any()},
					{Name: "IsImaginary", Op: Any()},
					{Name: "IsInline", Op: Any()},
					{Name: "IsLong", Op: Any()},
					{Name: "IsLongLong", Op: Any()},
					{Name: "IsRestrict", Op: Any()},
					{Name: "IsShort", Op: Any()},
					{Name: "IsSigned", Op: Any()},
					{Name: "IsThreadLocal", Op: Any()},
					{Name: "IsUnsigned", Op: Any()},
					{Name: "IsVirtual", Op: Any()},
					{Name: "IsVolatile", Op: Any()},
					{Name: "StorageClass", Op: Any()},
					{Name: "ExpandedFromMacro", Optional: "optMacro3", Op: Any()},
					{Name: "Type", Op: String("unspecified")},
				},
				Fields{
					// simpledeclspecifier
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "IsComplex", Op: Any()},
					{Name: "IsConst", Op: Any()},
					{Name: "IsConstExpr", Op: Any()},
					{Name: "IsExplicit", Op: Any()},
					{Name: "IsFriend", Op: Any()},
					{Name: "IsImaginary", Op: Any()},
					{Name: "IsInline", Op: Any()},
					{Name: "IsLong", Op: Any()},
					{Name: "IsLongLong", Op: Any()},
					{Name: "IsRestrict", Op: Any()},
					{Name: "IsShort", Op: Any()},
					{Name: "IsSigned", Op: Any()},
					{Name: "IsThreadLocal", Op: Any()},
					{Name: "IsUnsigned", Op: Any()},
					{Name: "IsVirtual", Op: Any()},
					{Name: "IsVolatile", Op: Any()},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "ExpandedFromMacro", Optional: "optMacro4", Op: Any()},
					{Name: "Type", Op: Var("retType")},
				},
				Fields{
					// namedtypespecifier
					{Name: uast.KeyType, Op: String("CPPASTNamedTypeSpecifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "IsConst", Op: Any()},
					{Name: "IsConstExpr", Op: Any()},
					{Name: "IsExplicit", Op: Any()},
					{Name: "IsFriend", Op: Any()},
					{Name: "IsInline", Op: Any()},
					{Name: "IsRestrict", Op: Any()},
					{Name: "IsThreadLocal", Op: Any()},
					{Name: "IsTypeName", Op: Any()},
					{Name: "IsVirtual", Op: Any()},
					{Name: "IsVolatile", Op: Any()},
					{Name: "ExpandedFromMacro", Optional: "optMacro5", Op: Any()},
					{Name: "Prop_Name", Op: Var("retType")},
				},
			)},

			{Name: "Prop_Declarator", Op: Fields{
				{Name: uast.KeyType, Op: String("CPPASTFunctionDeclarator")},
				{Name: uast.KeyPos, Op: Var("fdpos")},
				{Name: "IsConst", Op: Any()},
				{Name: "IsFinal", Op: Any()},
				{Name: "IsMutable", Op: Any()},
				{Name: "IsOverride", Op: Any()},
				{Name: "IsPureVirtual", Op: Any()},
				{Name: "IsVolatile", Op: Any()},
				{Name: "ExpandedFromMacro", Optional: "optMacro6", Op: Any()},
				{Name: "Prop_NoexceptExpression", Optional: "declNoExcept", Op: Any()},
				{Name: "Prop_VirtSpecifiers", Optional: "declVirtSpecs", Op: Any()},

				{Name: "Prop_Name", Op: Cases("caseName",
					// Empty identifier
					Is(nil),
					// Normal identifier
					Fields{
						{Name: uast.KeyType, Op: String("uast:Identifier")},
						{Name: uast.KeyPos, Op: Any()},
						{Name: "Name", Op: Var("name")},
					},
					// Qualified identifier
					Fields{
						{Name: uast.KeyType, Op: String("uast:QualifiedIdentifier")},
						{Name: uast.KeyPos, Op: Any()},
						// FIXME: join the Names into a single string
						{Name: "Names", Op: Var("qualnames")},
					},
				)},

				{Name: "TakesVarArgs", Op: Cases("takesVarArgs", Bool(false), Bool(true))},
				{Name: "Prop_ConstructorChain", Optional: "optConsChain", Op: Any()},
				{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Any()},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Cases("caseParams",
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: Any()},
						{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Any()},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
						{Name: "ExpandedFromMacro", Optional: "optMacro7", Op: Any()},
						{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Any()},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTArrayDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: Any()},
						{Name: "Prop_ArrayModifiers", Op: Any()},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
						{Name: "ExpandedFromMacro", Optional: "optMacro8", Op: Any()},
						{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Any()},
					},
				))},
			}},
		},
		Obj{
			"Nodes": Arr(
				UASTType(uast.Alias{}, Obj{
					"Name": UASTType(uast.Identifier{}, CasesObj("caseName", Obj{},
						Objs{
							// Empty identifier
							{
								"Name": Any(),
							},
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
							{Name: "Returns", Op: Cases("retTypeCase",
								// void or unspecified
								Any(),
								Any(),
								// SimpleDeclSpecifier
								Arr(UASTType(uast.Argument{},
									Obj{
										"Type": UASTType(uast.Identifier{}, Obj{
											"Name": Var("retType"),
										})})),
								// NamedTypeSpecifier
								Arr(UASTType(uast.Argument{},
									Obj{
										"Type": Var("retType"),
									})),
							)},

							{Name: "Arguments", Optional: "optArgs", Op: Cases("takesVarArgs",
								// False, no varargs
								Each("args", UASTType(uast.Argument{}, Obj{
									"Name": Var("aname"),
									//"Name": Cases("caseParamsName",
									//	Var("aname"),
									//	Is(nil),
									//),
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
