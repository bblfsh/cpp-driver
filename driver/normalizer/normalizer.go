package normalizer

import (
	"gopkg.in/bblfsh/sdk.v2/uast"
	//"gopkg.in/bblfsh/sdk.v2/uast/role"
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
	// Empty {}
	MapSemantic("CPPASTCompoundStatement", uast.Block{}, MapObj(
		Obj{},
		Obj{"Statements": Arr()},
	)),

	MapSemantic("CPPASTCompoundStatement", uast.Block{}, MapObj(
		Obj{
			"Prop_Statements": Var("statements"),
		},
		Obj{
			"Statements": Var("statements"),
		},
	)),

	MapSemantic("CPPASTLiteralExpression", uast.String{}, MapObj(
		Obj{
			"LiteralValue": Var("val"),
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

	MapSemantic("CPPASTName", uast.Identifier{}, MapObj(
		Obj{
			"Name": Var("name"),
		},
		Obj{
			"Name": Var("name"),
		},
	)),

	MapSemantic("CPPASTImplicitName", uast.Identifier{}, MapObj(
		Obj{
			"Name": Var("name"),
		},
		Obj{
			"Name": Var("name"),
		},
	)),

	MapSemantic("CPPASTQualifiedNames", uast.QualifiedIdentifier{}, MapObj(
		Obj{
			"Prop_AllSegments": Var("names"),
		},
		Obj{
			"Names": Var("names"),
		},
	)),

	MapSemantic("CPPASTFunctionDefinition", uast.FunctionGroup{}, MapObj(
		Fields{
			// TODO: optional modifiers that only appear when true
			{Name: "Prop_Body", Optional: "optBody", Op: Var("body")},

			{Name: "Prop_DeclSpecifier", Op: Cases("retTypeCase",
				// SimpleDeclSpecifier
				Fields{
					{Name: uast.KeyType, Op: Var("ignTypeRet")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "IsTypeName", Op: Var("ignIsTypeName")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "Type", Op: Var("retType")},
				},
				// NamedTypeSpecifier
				Fields{
					{Name: uast.KeyType, Op: Var("ignTypeRet")},
					{Name: uast.KeyPos, Op: Var("ignPosRet")},
					{Name: "IsTypeName", Op: Var("ignIsTypeName")},
					{Name: "StorageClass", Op: Var("StorageClass")},
					{Name: "Prop_Name", Op: Var("retType")},
				},
			)},

			{Name: "Prop_Declarator", Op: Fields{
				{Name: uast.KeyType, Op: String("CPPASTFunctionDeclarator")},
				{Name: uast.KeyPos, Op: Var("fdpos")},

				{Name: "Prop_Name", Op: Fields{
					{Name: uast.KeyType, Op: Var("ignTypeName")},
					{Name: uast.KeyPos, Op: Var("ignPosName")},
					{Name: "Prop_AllSegments", Optional: "optSegments", Op: Var("ignSegmentsName")},
					{Name: "IsConversionOperator", Optional: "optIsConv", Op: Var("ignIsConv")},
					{Name: "Prop_Qualifier", Optional: "optPropQual", Op: Var("ignPropQual")},
					{Name: "IsFullyQualified", Optional: "optIsFully", Op: Var("ignIsFully")},
					{Name: "Name", Op: Var("name")},
				}},

				{Name: "TakesVarArgs", Op: Cases("takesVarArgs", Bool(true), Bool(false))},
				{Name: "Prop_ConstructorChain", Op: Var("ignConsChain"), Optional: "optConsChain"},

				{Name: "Prop_Parameters", Optional: "optArgs", Op: Each("args", Fields{
					{Name: uast.KeyType, Op: String("CPPASTDeclarator")},
					{Name: uast.KeyPos, Op: Var("parampos")},
					{Name: "Prop_Name", Op: Var("aname")},
					{Name: "Prop_TypeNode", Op: Var("atype")},
					{Name: "DeclaresParameterPack", Optional: "optParamPack", Op: Var("ignParamPack")},
					{Name: "Prop_PointerOperators", Op: Var("ignPointerOps"), Optional: "optPointerOps"},
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
								// True, append a synthetic arg with Name = "..." and Variadic = true
								// FIXME: doesnt work
								Each("args", UASTType(uast.Argument{}, Obj{
									"Name": Var("aname"),
									"Type": Var("atype"),
								})),
								//Append(
								//	Each("args", UASTType(uast.Argument{}, Obj{
								//		"Name": Var("aname"),
								//		"Type": Var("atype"),
								//	})),
								//	UASTType(uast.Argument{}, Obj{
								//		"Name": String("..."),
								//		"Variadic": Bool(true),
								//	}),
								//	),
								// False, no varargs
								Each("args", UASTType(uast.Argument{}, Obj{
									"Name": Var("aname"),
									"Type": Var("atype"),
								}))),
							},
						})},
					}),
				}),
			),
		},
	),
	)}
