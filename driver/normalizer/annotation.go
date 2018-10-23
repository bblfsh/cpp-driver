package normalizer

import (
	"gopkg.in/bblfsh/sdk.v2/uast"
	"gopkg.in/bblfsh/sdk.v2/uast/role"
	. "gopkg.in/bblfsh/sdk.v2/uast/transformer"
	"gopkg.in/bblfsh/sdk.v2/uast/transformer/positioner"
)

var Native = Transformers([][]Transformer{
	{Mappings(Annotations...)},
	{
		RolesDedup(),
	},
}...)

var Code = []CodeTransformer{
	positioner.NewFillLineColFromOffset(),
}

func annotateTypeToken(typ, token string, roles ...role.Role) Mapping {
	return AnnotateType(typ,
		FieldRoles{
			uast.KeyToken: {Add: true, Op: String(token)},
		}, roles...)
}

var (
	typeRoles = StringToRolesMap(map[string][]role.Role{
		"int": {role.Type, role.Number},
		"int128": {role.Type, role.Number},
		"auto": {role.Type, role.Incomplete},
		"bool": {role.Type, role.Boolean},
		"char": {role.Type, role.Character},
		"char16": {role.Type, role.Character},
		"char32": {role.Type, role.Character},
		"wchar_t": {role.Type, role.Character},
		"decimal32": {role.Type, role.Number},
		"decimal64": {role.Type, role.Number},
		"decimal128": {role.Type, role.Number},
		"decltype": {role.Type, role.Incomplete},
		"decltype_auto": {role.Type, role.Incomplete},
		"double": {role.Type, role.Number},
		"float": {role.Type, role.Number},
		"float128": {role.Type, role.Number},
		"typeof": {role.Type, role.Incomplete},
		"void": {role.Type, role.Null},
		"unespecified": {role.Type, role.Noop},
	})

	litExprRoles = StringToRolesMap(map[string][]role.Role{
		"char_constant": {role.Expression, role.Literal, role.Character},
		"float_constant": {role.Expression, role.Literal, role.Number},
		"integer_constant": {role.Expression, role.Literal, role.Number},
		"nullptr": {role.Expression, role.Literal, role.Null},
		"string_literal": {role.Expression, role.Literal, role.String},
		"this": {role.Expression, role.Literal, role.Instance, role.Incomplete},
		"true": {role.Expression, role.Literal, role.Boolean},
		"false": {role.Expression, role.Literal, role.Boolean},
	})

	binaryExprRoles = StringToRolesMap(map[string][]role.Role{
		"=": {role.Binary, role.Expression, role.Assignment},
		"&": {role.Binary, role.Expression, role.Bitwise, role.And},
		"&=": {role.Binary, role.Expression, role.Bitwise, role.And, role.Assignment},
		"|": {role.Binary, role.Expression, role.Bitwise, role.Or},
		"|=": {role.Binary, role.Expression, role.Bitwise, role.Or, role.Assignment},
		"^": {role.Binary, role.Expression, role.Bitwise, role.Xor},
		"^=": {role.Binary, role.Expression, role.Bitwise, role.Xor, role.Assignment},
		"...": {role.Binary, role.Expression, role.Incomplete},
		"==": {role.Binary, role.Expression, role.Relational, role.Equal},
		"!=": {role.Binary, role.Expression, role.Relational, role.Equal, role.Not},
		">": {role.Binary, role.Expression, role.Relational, role.GreaterThan},
		">=": {role.Binary, role.Expression, role.Relational, role.GreaterThanOrEqual},
		"<": {role.Binary, role.Expression, role.Relational, role.LessThan},
		"<=": {role.Binary, role.Expression, role.Relational, role.LessThanOrEqual},
		"&&": {role.Binary, role.Expression, role.Boolean, role.And},
		"||": {role.Binary, role.Expression, role.Boolean, role.Or},
		"max": {role.Binary, role.Expression, role.Incomplete},
		"min": {role.Binary, role.Expression, role.Incomplete},
		"-": {role.Binary, role.Expression, role.Arithmetic, role.Substract},
		"-=": {role.Binary, role.Expression, role.Arithmetic, role.Substract, role.Assignment},
		"+": {role.Binary, role.Expression, role.Arithmetic, role.Add},
		"+=": {role.Binary, role.Expression, role.Arithmetic, role.Add, role.Assignment},
		"%": {role.Binary, role.Expression, role.Arithmetic, role.Modulo},
		"%=": {role.Binary, role.Expression, role.Arithmetic, role.Modulo, role.Assignment},
		"*": {role.Binary, role.Expression, role.Arithmetic, role.Multiply},
		"*=": {role.Binary, role.Expression, role.Arithmetic, role.Multiply, role.Assignment},
		"/": {role.Binary, role.Expression, role.Arithmetic, role.Divide},
		"/=": {role.Binary, role.Expression, role.Arithmetic, role.Divide, role.Assignment},
		"->": {role.Binary, role.Expression, role.Incomplete},
		".": {role.Binary, role.Expression, role.Incomplete},
		"<<": {role.Binary, role.Expression, role.Bitwise, role.LeftShift},
		"<<=": {role.Binary, role.Expression, role.Bitwise, role.LeftShift, role.Assignment},
		">>": {role.Binary, role.Expression, role.Bitwise, role.RightShift},
		">>=": {role.Binary, role.Expression, role.Bitwise, role.RightShift, role.Assignment},
		"unknown_operator": {role.Binary, role.Expression, role.Incomplete},
	})
)

var Annotations = []Mapping{
	AnnotateType("internal-type", nil, role.Incomplete),
	AnnotateType("CPPASTTranslationUnit", nil, role.File, role.Module),
	// XXX isQualified field?
	AnnotateType("CPPASTName", FieldRoles{"Name": {Rename: uast.KeyToken}},
		role.Identifier),

	// XXX ExpressionType, get all possible values
	AnnotateType("CPPASTIdExpression", nil, role.Expression, role.Variable),

	AnnotateType("CPPASTNullStatement", nil, role.Literal, role.Null, role.Expression,
		role.Primitive),
	AnnotateType("CPPASTGotoStatement", nil, role.Goto, role.Statement),
	AnnotateType("CPPASTLabelStatement", nil, role.Name, role.Incomplete),
	AnnotateType("CPPASTSimpleDeclaration", nil, role.Declaration, role.Statement),
	AnnotateType("CPPASTDeclarationStatement", nil, role.Declaration, role.Statement),

	AnnotateTypeCustom("CPPASTSimpleDeclSpecifier",
		FieldRoles {
			"Type": {Rename: uast.KeyToken, Op: Var("type")},
		},
		LookupArrOpVar("type", typeRoles),
	),

	AnnotateTypeCustom("CPPASTLiteralExpression",
		FieldRoles {
			"LiteralValue": {Rename: uast.KeyToken},
			"kind": {Op: Var("kind")},
		},
		LookupArrOpVar("kind", litExprRoles),
	),

	AnnotateType("CPPASTCompoundStatement", nil, role.Body),
	AnnotateType("CPPASTDeclarator", nil, role.Declaration, role.Variable, role.Name),

	AnnotateType("CPPASTFunctionDefinition", ObjRoles{
		"Prop_Body": {role.Function, role.Declaration, role.Body},
		"Prop_DeclSpecifier": {role.Function, role.Declaration, role.Return, role.Type},
	}, role.Function, role.Declaration),

	AnnotateType("CPPASTFunctionDeclarator", FieldRoles{
		"Prop_Name": {Roles: role.Roles{role.Function, role.Declaration, role.Name}},
		"Prop_Parameters": {Arr: true, Roles: role.Roles{role.Function, role.Declaration, role.Argument}},
	}, role.Function, role.Declaration),

	AnnotateType("CPPASTReturnStatement", ObjRoles{
		"Prop_ReturnArgument": {role.Return, role.Value},
	}, role.Statement, role.Return),

	AnnotateTypeCustom("CPPASTBinaryExpression", FieldRoles{
		"Operator": {Rename: uast.KeyToken, Op: Var("op")},
		"Prop_Operand1": {Roles: role.Roles{role.Binary, role.Expression, role.Left}},
		"Prop_Operand2": {Roles: role.Roles{role.Binary, role.Expression, role.Left}},
	}, LookupArrOpVar("op", binaryExprRoles)),

	AnnotateType("CPPASTEqualsInitializer", nil, role.Declaration, role.Assignment, role.Expression, role.Right),
}
