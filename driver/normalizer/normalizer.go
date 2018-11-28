package normalizer

import (
	"gopkg.in/bblfsh/sdk.v2/uast"
	. "gopkg.in/bblfsh/sdk.v2/uast/transformer"
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

var Normalizers = []Mapping{

	MapSemantic("ASTInclusionStatement", uast.Import{}, MapObj(
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
		"IASTClass":   String("CPPASTName"),
		"Name":        String(""),
		"IsQualified": Var("ignIsQual"),
	}, Obj{
		uast.KeyType: String("uast:Identifier"),
		"Name":       String(""),
	}),

	MapSemantic("CPPASTName", uast.Identifier{}, MapObj(
		Obj{
			"Name":        Var("name"),
			"IsQualified": Var("ignIsQual"),
		},
		Obj{
			"Name": Var("name"),
		},
	)),

	MapSemantic("CPPASTImplicitName", uast.Identifier{}, MapObj(
		Obj{
			"Name":                 Var("name"),
			"IsQualified":          Var("ignIsQual"),
			"IsAlternate":          Var("ignIsAlternate"),
			"IsOverloadedOperator": Var("ignIsOver"),
		},
		Obj{
			"Name": Var("name"),
		},
	)),

	// Disabled: parts can be anything, not only identifiers (like function calls or object instantiations)
	// and the SDK always expects identifiers (but should except Any[] for this object)
	//MapSemantic("CPPASTQualifiedName", uast.QualifiedIdentifier{}, MapObj(
	//	Obj{
	//		"Prop_AllSegments": Var("names"),
	//		"IsQualified": Var("ignIsQual"),
	//		"IsConversionOperator": Var("ignIsConv"),
	//		"IsFullyQualified": Var("ignFullyQual"),
	//	},
	//	Obj{
	//		"Names": Var("names"),
	//	},
	//)),

	MapSemantic("CPPASTFunctionDefinition", uast.FunctionGroup{}, MapObj(
		Fields{
			{Name: "IsDefaulted", Optional: "optDefaulted", Op: Var("ignDefaulted")},
			{Name: "IsDeleted", Optional: "optDeleted", Op: Var("ignDeleted")},

			// TODO: optional modifiers that only appear when true
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				// SimpleDeclSpecifier
				Fields{
					{Name: uast.KeyType, Op: Var("ignTypeRet")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "IsComplex", Optional: "optIsComplex", Op: Var("ignIsComplex")},
					{Name: "IsConst", Optional: "optIsConst", Op: Var("ignIsConst")},
					{Name: "IsConstExpr", Optional: "optIsConstExpr", Op: Var("ignIsConstExpr")},
					{Name: "IsExplicit", Optional: "optIsExplicit", Op: Var("ignIsExplicit")},
					{Name: "IsFriend", Optional: "optIsFriend", Op: Var("ignIsFriend")},
					{Name: "IsImaginary", Optional: "optIsImaginary", Op: Var("ignIsImaginary")},
					{Name: "IsInline", Optional: "optIsInline", Op: Var("ignIsInline")},
					{Name: "IsLong", Optional: "optIsLong", Op: Var("ignIsLong")},
					{Name: "IsLongLong", Optional: "optIsLongLong", Op: Var("ignIsLongLong")},
					{Name: "IsRestrict", Optional: "optIsRestrict", Op: Var("ignIsRestrict")},
					{Name: "IsShort", Optional: "optIsShort", Op: Var("ignIsShort")},
					{Name: "IsSigned", Optional: "optIsSigned", Op: Var("ignIsSigned")},
					{Name: "IsThreadLocal", Optional: "optIsThreadLocal", Op: Var("ignIsThreadLocal")},
					{Name: "IsUnsigned", Optional: "optIsUnsigned", Op: Var("ignIsUnsigned")},
					{Name: "IsVirtual", Optional: "optIsVirtual", Op: Var("ignIsVirtual")},
					{Name: "IsVolatile", Optional: "optIsVol", Op: Var("ignIsVol")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "Type", Op: Var("retType")},
				},
				// NamedTypeSpecifier
				Fields{
					{Name: uast.KeyType, Op: Var("ignTypeRet")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "IsConst", Optional: "optIsConst", Op: Var("ignIsConst")},
					{Name: "IsConstExpr", Optional: "optIsConstExpr", Op: Var("ignIsConstExpr")},
					{Name: "IsExplicit", Optional: "optIsExplicit", Op: Var("ignIsExplicit")},
					{Name: "IsFriend", Optional: "optIsFriend", Op: Var("ignIsFriend")},
					{Name: "IsInline", Optional: "optIsInline", Op: Var("ignIsInline")},
					{Name: "IsRestrict", Optional: "optIsRestrict", Op: Var("ignIsRestrict")},
					{Name: "IsThreadLocal", Optional: "optIsThreadLocal", Op: Var("ignIsThreadLocal")},
					{Name: "IsTypeName", Optional: "optIsTypeName", Op: Var("ignIsTypeName")},
					{Name: "IsVirtual", Optional: "optIsVirtual", Op: Var("ignIsVirtual")},
					{Name: "IsVolatile", Optional: "optIsVolatile", Op: Var("ignIsVolatile")},
					{Name: "Prop_Name", Op: Var("retType")},
				},
			)},

			{Name: "Prop_Declarator", Op: Fields{
				{Name: uast.KeyType, Op: String("CPPASTFunctionDeclarator")},
				{Name: uast.KeyPos, Op: Var("fdpos")},
				{Name: "IsConst", Optional: "optIsConst", Op: Var("ignIsConstFn")},
				{Name: "IsFinal", Optional: "optIsFinal", Op: Var("ignIsFinalFn")},
				{Name: "IsMutable", Optional: "optIsMutable", Op: Var("ignIsMutableFn")},
				{Name: "IsOverride", Optional: "optIsOverride", Op: Var("ignIsOverrideFn")},
				{Name: "IsPureVirtual", Optional: "optIsPureVirtual", Op: Var("ignIsPureVirtualFn")},
				{Name: "IsVolatile", Optional: "optIsVolatile", Op: Var("ignIsVolatileFn")},

				{Name: "Prop_Name", Op: Fields{
					{Name: uast.KeyType, Op: Var("ignTypeName")},
					{Name: uast.KeyPos, Op: Var("ignPosName")},
					{Name: "Prop_AllSegments", Optional: "optSegments", Op: Var("ignSegmentsName")},
					{Name: "Prop_Qualifier", Optional: "optPropQual", Op: Var("ignPropQual")},
					{Name: "IsConversionOperator", Optional: "optConvOp", Op: Var("ignIsConvOp")},
					{Name: "IsFullyQualified", Optional: "optIsFully", Op: Var("ignIsFully")},
					{Name: "IsQualified", Optional: "optQual", Op: Var("ignIsQual")},
					{Name: "Name", Op: Var("name")},
				}},

				{Name: "TakesVarArgs", Op: Cases("takesVarArgs", Bool(false), Bool(true))},
				{Name: "Prop_ConstructorChain", Op: Var("ignConsChain"), Optional: "optConsChain"},
				{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Var("ignPointerOps")},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Fields{
					{Name: uast.KeyType, Op: Var("ignParamType")},
					{Name: uast.KeyPos, Op: Var("parampos")},
					{Name: "Prop_Name", Op: Var("aname")},
					{Name: "Prop_TypeNode", Op: Var("atype")},
					{Name: "DeclaresParameterPack", Optional: "optParamPack", Op: Var("ignParamPack")},
					{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
					{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Var("ignPointerOps2")},
					{Name: "Prop_ArrayModifiers", Optional: "optArrayMod", Op: Var("ignArrayMod")},
				})},
			}},
		},
		Obj{
			"Nodes": Arr(
				UASTType(uast.Alias{}, Obj{
					"Name": UASTType(uast.Identifier{}, Obj{
						"Name": Var("name"),
					}),

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
	))}
