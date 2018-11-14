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

	MapSemantic("CPPASTLiteralExpression", uast.String{}, MapObj(
		Obj{
			"LiteralValue": Var("val"),
			"kind": String("string_literal"),
		},
		Obj{
			"Value": Var("val"),
			"Format": String(""),
		},
	)),

	MapSemantic("Comment", uast.Comment{}, MapObj(
		Obj{
			"Comment": CommentText([2]string{"/*", "*/"}, "comm"),
			"IsBlockComment": Bool(true),
		},
		CommentNode(true, "comm", nil),
	)),

	MapSemantic("Comment", uast.Comment{}, MapObj(
		Obj{
			"Comment": CommentText([2]string{"//", ""}, "comm"),
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
}
