package normalizer

import (
	"strings"

	"github.com/bblfsh/sdk/v3/uast"
	"github.com/bblfsh/sdk/v3/uast/nodes"
	. "github.com/bblfsh/sdk/v3/uast/transformer"
	"github.com/bblfsh/sdk/v3/uast/transformer/positioner"
)

var Preprocess = Transformers([][]Transformer{
	{
		ResponseMetadata{
			TopLevelIsRootNode: false,
		},
	},
	{Mappings(Preprocessors...)},
}...)

var PreprocessCode = []CodeTransformer{
	positioner.FromUTF16Offset(),
}

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
		Fields{
			{Name: "Name", Op: Var("name")},
			{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
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

var argCases = Cases("caseParams",
	UASTType(uast.Argument{}, Obj{
		"Name": Var("aname"),
		"Type": Var("atype"),
		"Init": If("optInitializer", Var("ainit"), Is(nil)),
	}),
	UASTType(uast.Argument{}, Obj{
		"Name": Var("aname"),
		"Type": Var("atype"),
		"Init": If("optInitializer", Var("ainit"), Is(nil)),
	}),
	UASTType(uast.Argument{}, Obj{
		"Name": Var("aname"),
		"Type": UASTType(uast.Identifier{}, Obj{
			"Name": Var("eltype"),
		}),
		"Init": If("optInitializer", Var("ainit"), Is(nil)),
	}))

var Normalizers = []Mapping{

	// After adding "LeadingComments" to pre-proc statements in native ast,
	// we must drop them in the semantic representation.
	MapSemantic("ASTInclusionStatement", uast.InlineImport{}, MapObj(
		Fields{
			{Name: "Name", Op: Var("path")},
			{Name: "IsSystem", Op: Bool(true)},
			// Always empty on current tests, this should detect other cases
			{Name: "Path", Op: String("")},
			// FIXME(juanjux): save this once we've a way
			{Name: "Resolved", Op: Any()},
			{Name: "LeadingComments", Drop: true, Op: Any()},
			{Name: "TrailingComments", Drop: true, Op: Any()},
		},
		Obj{
			"Path": UASTType(uast.String{}, Obj{
				"Value":  Var("path"),
				"Format": String(""),
			}),
			"All":   Bool(true),
			"Names": Is(nil),
		},
	)),

	MapSemantic("ASTInclusionStatement", uast.InlineImport{}, MapObj(
		Fields{
			{Name: "Name", Op: StringConv(Var("path"), prependDotSlash, removeDotSlash)},
			{Name: "IsSystem", Op: Bool(false)},
			// Always empty on current tests, this should detect other cases
			{Name: "Path", Op: String("")},
			// FIXME(juanjux): save this once we've a way
			{Name: "Resolved", Op: Any()},
			{Name: "LeadingComments", Drop: true, Op: Any()},
			{Name: "TrailingComments", Drop: true, Op: Any()},
		},
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
		Fields{
			{Name: "Prop_Statements", Op: Var("statements")},
			// FIXME(juanjux): save all these once we have a way.
			{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
			{Name: "LeadingComments", Drop: true, Op: Any()},
			{Name: "FreestadingComments", Drop: true, Op: Any()},
			{Name: "TrailingComments", Drop: true, Op: Any()},
		},
		Obj{
			"Statements": Var("statements"),
		},
	)),

	// Empty {}
	MapSemantic("CPPASTCompoundStatement", uast.Block{}, MapObj(
		Fields{
			// FIXME(juanjux): save all these once we have a way
			{Name: "LeadingComments", Drop: true, Op: Any()},
			{Name: "FreestadingComments", Drop: true, Op: Any()},
			{Name: "TrailingComments", Drop: true, Op: Any()},
		},
		Obj{"Statements": Arr()},
	)),

	MapSemantic("CPPASTLiteralExpression", uast.String{}, MapObj(
		Obj{
			"LiteralValue":            Quote(Var("val")),
			"kind":                    String("string_literal"),
			"ExpressionValueCategory": String("LVALUE"),
			"IsLValue":                Bool(true),
			// Will be const char[somenum]
			"ExpressionType": Any(),
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
	Map(
		Fields{
			{Name: "IASTClass", Op: String("CPPASTName")},
			{Name: "Name", Op: String("")},
			// FIXME(juanjux): save this once we have a way.
			{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
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
			{Name: "IsConversionOperator", Op: Bool(false)},
			// Ignored: already on AllSegments
			{Name: "Prop_Qualifier", Drop: true, Op: Any()},
			// FIXME(juanjux): save these two once we've a way
			{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
			{Name: "IsFullyQualified", Op: Any()},
			// Same as Prop_AllSegments but in a single string ("foo::bar::baz") instead of a list
			{Name: "Name", Op: Any()},
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
			// FIXME(juanjux): save this once we've a way
			{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},
			{Name: "LeadingComments", Optional: "optLeadingComments", Op: Var("leadingComments")},
			{Name: "FreestadingComments", Optional: "optFSComments", Op: Var("fsComments")},
			{Name: "TrailingComments", Optional: "optTlComments", Op: Var("tsComments")},
			{Name: "Prop_MemberInitializers", Optional: "optMemberInitializers", Op: Var("memberInitializers")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: Any()},
					{Name: "IsComplex", Drop: true, Op: Any()},
					{Name: "IsConst", Drop: true, Op: Any()},
					{Name: "IsConstExpr", Drop: true, Op: Any()},
					{Name: "IsExplicit", Drop: true, Op: Any()},
					{Name: "IsFriend", Drop: true, Op: Any()},
					{Name: "IsImaginary", Drop: true, Op: Any()},
					{Name: "IsInline", Drop: true, Op: Any()},
					{Name: "IsLong", Drop: true, Op: Any()},
					{Name: "IsLongLong", Drop: true, Op: Any()},
					{Name: "IsRestrict", Drop: true, Op: Any()},
					{Name: "IsShort", Drop: true, Op: Any()},
					{Name: "IsSigned", Drop: true, Op: Any()},
					{Name: "IsThreadLocal", Drop: true, Op: Any()},
					{Name: "IsUnsigned", Drop: true, Op: Any()},
					{Name: "IsVirtual", Drop: true, Op: Any()},
					{Name: "IsVolatile", Drop: true, Op: Any()},
					{Name: "StorageClass", Drop: true, Op: Any()},
					{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
					{Name: "Type", Op: String("void")},
				},
				// unspecified (ie constructor/destructors)
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Drop: true, Op: Any()},
					{Name: "IsComplex", Drop: true, Op: Any()},
					{Name: "IsConst", Drop: true, Op: Any()},
					{Name: "IsConstExpr", Drop: true, Op: Any()},
					{Name: "IsExplicit", Drop: true, Op: Any()},
					{Name: "IsFriend", Drop: true, Op: Any()},
					{Name: "IsImaginary", Drop: true, Op: Any()},
					{Name: "IsInline", Drop: true, Op: Any()},
					{Name: "IsLong", Drop: true, Op: Any()},
					{Name: "IsLongLong", Drop: true, Op: Any()},
					{Name: "IsRestrict", Drop: true, Op: Any()},
					{Name: "IsShort", Drop: true, Op: Any()},
					{Name: "IsSigned", Drop: true, Op: Any()},
					{Name: "IsThreadLocal", Drop: true, Op: Any()},
					{Name: "IsUnsigned", Drop: true, Op: Any()},
					{Name: "IsVirtual", Drop: true, Op: Any()},
					{Name: "IsVolatile", Drop: true, Op: Any()},
					{Name: "StorageClass", Drop: true, Op: Any()},
					{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
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
					{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
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
					{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
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
				{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
				{Name: "Prop_NoexceptExpression", Drop: true, Op: Any()},
				{Name: "Prop_VirtSpecifiers", Drop: true, Op: Any()},

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
				{Name: "Prop_ConstructorChain", Drop: true, Op: Any()},
				{Name: "Prop_PointerOperators", Drop: true, Op: Any()},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Cases("caseParams",
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
						// FIXME(juanjux): save these once we've a way
						{Name: "DeclaresParameterPack", Drop: true, Op: Any()},
						{Name: "Prop_PointerOperators", Drop: true, Op: Any()},
						{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
						{Name: "Prop_PointerOperators", Drop: true, Op: Any()},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTArrayDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
						// FIXME(juanjux): save these once we've a way
						{Name: "DeclaresParameterPack", Drop: true, Op: Any()},
						{Name: "Prop_ArrayModifiers", Drop: true, Op: Any()},
						{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
						{Name: "Prop_PointerOperators", Drop: true, Op: Any()},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTElaboratedTypeSpecifier")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
						{Name: "Kind", Op: Var("eltype")},
						{Name: "IsConst", Op: Any()},
						{Name: "IsConstExpr", Op: Any()},
						{Name: "IsExplicit", Op: Any()},
						{Name: "IsFriend", Op: Any()},
						{Name: "IsInline", Op: Any()},
						{Name: "IsRestrict", Op: Any()},
						{Name: "IsThreadLocal", Op: Any()},
						{Name: "IsVirtual", Op: Any()},
						{Name: "IsVolatile", Op: Any()},
						{Name: "StorageClass", Op: Any()},
						// FIXME(juanjux): save these once we've a way
						{Name: "ExpandedFromMacro", Drop: true, Op: Any()},
					},
				))},
			}},
		},
		Obj{
			"Nodes": Arr(
				Fields{
					{Name: "Comments", Op: Fields{
						{Name: "LeadingComments", Optional: "optLeadingComments", Op: Var("leadingComments")},
						{Name: "FreestadingComments", Optional: "optFSComments", Op: Var("fsComments")},
						{Name: "TrailingComments", Optional: "optTlComments", Op: Var("tsComments")},
					}},
					{Name: "MemberInitializers", Optional: "optMemberInitializers", Op: Var("memberInitializers")},
				},
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
								Each("args", argCases),

								// True, the last arg is variadic
								Append(
									Each("args", argCases),
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
