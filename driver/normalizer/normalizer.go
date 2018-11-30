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
			{Name: "IsDefaulted", Op: Var("ignDefaulted")},
			{Name: "IsDeleted", Op: Var("ignDeleted")},
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTSimpleDeclSpecifier")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "IsComplex", Op: Var("ignIsComplex")},
					{Name: "IsConst", Op: Var("ignIsConst")},
					{Name: "IsConstExpr", Op: Var("ignIsConstExpr")},
					{Name: "IsExplicit", Op: Var("ignIsExplicit")},
					{Name: "IsFriend", Op: Var("ignIsFriend")},
					{Name: "IsImaginary", Op: Var("ignIsImaginary")},
					{Name: "IsInline", Op: Var("ignIsInline")},
					{Name: "IsLong", Op: Var("ignIsLong")},
					{Name: "IsLongLong", Op: Var("ignIsLongLong")},
					{Name: "IsRestrict", Op: Var("ignIsRestrict")},
					{Name: "IsShort", Op: Var("ignIsShort")},
					{Name: "IsSigned", Op: Var("ignIsSigned")},
					{Name: "IsThreadLocal", Op: Var("ignIsThreadLocal")},
					{Name: "IsUnsigned", Op: Var("ignIsUnsigned")},
					{Name: "IsVirtual", Op: Var("ignIsVirtual")},
					{Name: "IsVolatile", Op: Var("ignIsVol")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "Type", Op: Var("retType")},
				},
				Fields{
					{Name: uast.KeyType, Op: String("CPPASTNamedTypeSpecifier")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "IsConst", Op: Var("ignIsConst")},
					{Name: "IsConstExpr", Op: Var("ignIsConstExpr")},
					{Name: "IsExplicit", Op: Var("ignIsExplicit")},
					{Name: "IsFriend", Op: Var("ignIsFriend")},
					{Name: "IsInline", Op: Var("ignIsInline")},
					{Name: "IsRestrict", Op: Var("ignIsRestrict")},
					{Name: "IsThreadLocal", Op: Var("ignIsThreadLocal")},
					{Name: "IsTypeName", Op: Var("ignIsTypeName")},
					{Name: "IsVirtual", Op: Var("ignIsVirtual")},
					{Name: "IsVolatile", Op: Var("ignIsVolatile")},
					{Name: "Prop_Name", Op: Var("retType")},
				},
			)},

			{Name: "Prop_Declarator", Op: Fields{
				{Name: uast.KeyType, Op: String("CPPASTFunctionDeclarator")},
				{Name: uast.KeyPos, Op: Var("fdpos")},
				{Name: "IsConst", Op: Var("ignIsConstFn")},
				{Name: "IsFinal", Op: Var("ignIsFinalFn")},
				{Name: "IsMutable", Op: Var("ignIsMutableFn")},
				{Name: "IsOverride", Op: Var("ignIsOverrideFn")},
				{Name: "IsPureVirtual", Op: Var("ignIsPureVirtualFn")},
				{Name: "IsVolatile", Op: Var("ignIsVolatileFn")},

				{Name: "Prop_Name", Op: Cases("caseName",
					Fields{
						{Name: uast.KeyType, Op: String("uast:Identifier")},
						{Name: uast.KeyPos, Op: Var("ignPosName")},
						{Name: "Name", Op: Var("name")},
					},
					Fields{
						// TODO: change to uast:QualifiedIdentifier when fixed
						{Name: uast.KeyType, Op: String("CPPASTQualifiedName")},
						{Name: uast.KeyPos, Op: Var("ignPosName")},
						{Name: "IsConversionOperator", Op: Var("ignIsConvOp")},
						{Name: "Prop_AllSegments", Op: Var("ignSegmentsName")},
						{Name: "Prop_Qualifier", Op: Var("ignPropQual")},
						{Name: "IsQualified", Op: Var("ignIsQual")},
						{Name: "IsFullyQualified", Op: Var("ignIsFully")},
						{Name: "Name", Op: Var("name")},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTOperatorName")},
						{Name: uast.KeyPos, Op: Var("ignPosName")},
						{Name: "IsQualified", Op: Var("ignIsQual")},
						{Name: "Name", Op: Var("name")},
					},
				)},

				{Name: "TakesVarArgs", Op: Cases("takesVarArgs", Bool(false), Bool(true))},
				{Name: "Prop_ConstructorChain", Optional: "optConsChain", Op: Var("ignConsChain")},
				{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Var("ignPointerOps")},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Cases("caseParams",
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: Var("ignParamPack")},
						{Name: "Prop_PointerOperators", Optional: "optPointerOps", Op: Var("ignPointerOps2")},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
					},
					Fields{
						{Name: uast.KeyType, Op: String("CPPASTArrayDeclarator")},
						{Name: uast.KeyPos, Op: Var("parampos")},
						{Name: "Prop_Name", Op: Var("aname")},
						{Name: "Prop_TypeNode", Op: Var("atype")},
						{Name: "DeclaresParameterPack", Op: Var("ignParamPack")},
						{Name: "Prop_ArrayModifiers", Op: Var("ignArrayMod")},
						{Name: "Prop_Initializer", Optional: "optInitializer", Op: Var("ainit")},
					},
				))},
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
