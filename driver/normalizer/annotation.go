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
		"unespecified": {role.Type},
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
		"-=": {role.Binary, role.Expression, role.Arithmetic, role.Substract,
			role.Assignment},
		"+": {role.Binary, role.Expression, role.Arithmetic, role.Add},
		"+=": {role.Binary, role.Expression, role.Arithmetic, role.Add,
			role.Assignment},
		"%": {role.Binary, role.Expression, role.Arithmetic, role.Modulo},
		"%=": {role.Binary, role.Expression, role.Arithmetic, role.Modulo,
			role.Assignment},
		"*": {role.Binary, role.Expression, role.Arithmetic, role.Multiply},
		"*=": {role.Binary, role.Expression, role.Arithmetic, role.Multiply,
			role.Assignment},
		"/": {role.Binary, role.Expression, role.Arithmetic, role.Divide},
		"/=": {role.Binary, role.Expression, role.Arithmetic, role.Divide,
			role.Assignment},
		"->": {role.Binary, role.Expression, role.Incomplete},
		".": {role.Binary, role.Expression, role.Incomplete},
		"<<": {role.Binary, role.Expression, role.Bitwise, role.LeftShift},
		"<<=": {role.Binary, role.Expression, role.Bitwise, role.LeftShift,
			role.Assignment},
		">>": {role.Binary, role.Expression, role.Bitwise, role.RightShift},
		">>=": {role.Binary, role.Expression, role.Bitwise, role.RightShift,
			role.Assignment},
		"unknown_operator": {role.Binary, role.Expression, role.Incomplete},
	})

	unaryExprRoles = StringToRolesMap(map[string][]role.Role{
		"op_alignof": {role.Unary, role.Incomplete},
		"op_amper": {role.Unary, role.Incomplete},
		"op_bracketedPrimary": {role.Unary, role.Incomplete},
		"op_labelReference": {role.Unary, role.Incomplete},
		"op_plus": {role.Unary, role.Noop},
		"op_minus": {role.Unary, role.Arithmetic, role.Incomplete},
		"op_noexcept": {role.Unary, role.Incomplete},
		"op_not": {role.Unary, role.Relational, role.Not},
		"op_postFixIncr": {role.Unary, role.Arithmetic, role.Add},
		"op_postFixDecr": {role.Unary, role.Arithmetic, role.Substract},
		"op_prefixIncr": {role.Unary, role.Arithmetic, role.Add},
		"op_prefixDecr": {role.Unary, role.Arithmetic, role.Substract},
		"op_sizeof": {role.Unary, role.Incomplete},
		"op_sizeofParameterPack": {role.Unary, role.Incomplete},
		"op_star": {role.Unary, role.Dereference},
		"op_throw": {role.Unary, role.Throw},
		"op_tilde": {role.Unary, role.Bitwise, role.Not},
		"op_typeid": {role.Unary, role.Incomplete},
		"op_unkown": {role.Unary, role.Incomplete},
	})
)

var Annotations = []Mapping{
	AnnotateType("internal-type", nil, role.Incomplete),
	AnnotateType("CPPASTTranslationUnit", nil, role.File, role.Module),
	AnnotateType("CPPASTExpressionStatement", nil, role.Expression),

	// Empty names i.e. for empty function arguments like: "void main(int, char**)"
	Map(Obj{
		"IASTClass": String("CPPASTName"),
		"Name": String(""),
		"IsQualified": Var("isqual"),
	}, Obj{
		uast.KeyType: String("CPPASTName"),
		uast.KeyRoles: Roles(role.Identifier),
		uast.KeyToken: String(""),
		"IsQualified": Var("isqual"),
	}),

	AnnotateType("CPPASTName", FieldRoles{
		"Name": {Rename: uast.KeyToken},
	}, role.Identifier),

	AnnotateType("CPPASTImplicitName", FieldRoles{
		"Name": {Rename: uast.KeyToken},
	}, role.Identifier),
	AnnotateType("ASTInclusionStatement", FieldRoles{"Name": {Rename: uast.KeyToken}}, role.Import),

	AnnotateType("CPPASTIdExpression", nil, role.Expression, role.Variable),
	AnnotateType("CPPASTNullStatement", nil, role.Literal, role.Null, role.Expression,
		role.Primitive),
	AnnotateType("CPPASTGotoStatement", nil, role.Goto, role.Statement),
	AnnotateType("CPPASTBreakStatement", nil, role.Break, role.Statement),
	AnnotateType("CPPASTContinueStatement", nil, role.Continue, role.Statement),
	AnnotateType("CPPASTLabelStatement", nil, role.Name, role.Incomplete),
	AnnotateType("CPPASTSimpleDeclaration", nil, role.Declaration, role.Statement),
	AnnotateType("CPPASTDeclarationStatement", nil, role.Declaration, role.Statement),
	AnnotateType("CPPASTFieldReference", nil, role.Qualified, role.Expression),
	AnnotateType("CPPASTBaseSpecifier", nil, role.Type, role.Declaration, role.Base),
	AnnotateType("CPPASTNamedTypeSpecifier", nil, role.Type, role.Instance),
	AnnotateType("CPPASTProblemStatement", nil, role.Incomplete),
	AnnotateType("CPPASTUsingDirective", nil, role.Scope, role.Alias),
	AnnotateType("CPPASTNewExpression", nil, role.Instance, role.Value),
	AnnotateType("CPPASTTypeId", nil, role.Type),
	AnnotateType("CPPASTTemplateDeclaration", nil, role.Type, role.Declaration,
		role.Incomplete),
	AnnotateType("CPPASTSimpleTypeTemplateParameter", nil, role.Type, role.Declaration,
		role.Argument, role.Incomplete),
	AnnotateType("CPPASTTemplateId", nil, role.Type, role.Incomplete),
	AnnotateType("CPPASTDeleteExpression", nil, role.Call, role.Expression,
		role.Incomplete),
	AnnotateType("CPPASTInitializerList", nil, role.Initialization, role.List),
	AnnotateType("CPPASTCastExpression", nil, role.Expression, role.Incomplete),
	AnnotateType("CPPASTDesignatedInitializer", nil, role.Expression,
		role.Initialization),
	AnnotateType("CPPASTConditionalExpression", nil, role.Expression, role.Condition),

	AnnotateTypeCustom("CPPASTUnaryExpression",
		FieldRoles{
			"operator": {Op: Var("operator")},
		},
		LookupArrOpVar("operator", unaryExprRoles)),

	AnnotateTypeCustom("CPPASTSimpleDeclSpecifier",
		FieldRoles {
			"Type": {Rename: uast.KeyToken, Op: Var("type")},
		},
		LookupArrOpVar("type", typeRoles),
	),

	AnnotateType("CPPASTASMDeclaration", FieldRoles{
		"Assembly": {Rename: uast.KeyToken},
	}, role.Declaration, role.Block, role.Incomplete),

	AnnotateType("CPPASTLinkageSpecification", FieldRoles{
		"Literal": {Rename: uast.KeyToken},
	}, role.Declaration, role.Block, role.Incomplete),

	AnnotateTypeCustom("CPPASTLiteralExpression",
		FieldRoles {
			"LiteralValue": {Rename: uast.KeyToken},
			"kind": {Op: Var("kind")},
		},
		LookupArrOpVar("kind", litExprRoles),
	),

	AnnotateType("CPPASTCompoundStatement", nil, role.Body),
	AnnotateType("CPPASTDeclarator", FieldRoles{
		"Name": {Rename: uast.KeyToken},
	}, role.Declaration, role.Variable, role.Name),
	AnnotateType("CPPASTDeclarator", nil, role.Declaration, role.Variable, role.Name),

	AnnotateType("CPPASTFunctionDefinition", ObjRoles{
		"Prop_Body": {role.Function, role.Declaration, role.Body},
		"Prop_DeclSpecifier": {role.Function, role.Declaration, role.Return, role.Type},
	}, role.Function, role.Declaration),

	AnnotateType("CPPASTFunctionDeclarator", FieldRoles{
		"Prop_Name": {Roles: role.Roles{role.Function, role.Declaration, role.Name}},
		// SDK TODO: adding "Opt: true" fails since Arrays can't be optional, but without
		// it the annotation won't match, thus the duplicated annotation below
		"Prop_Parameters": {Arr: true, Roles: role.Roles{role.Function, role.Declaration,
			role.Argument}},
	}, role.Function, role.Declaration),

	AnnotateType("CPPASTFunctionDeclarator", FieldRoles{
		"Prop_Name": {Roles: role.Roles{role.Function, role.Declaration, role.Name}},
	}, role.Function, role.Declaration),

	AnnotateType("CPPASTReturnStatement", ObjRoles{
		"Prop_ReturnArgument": {role.Return, role.Value},
	}, role.Statement, role.Return),

	AnnotateTypeCustom("CPPASTBinaryExpression", MapObj(Obj{
		"Operator": Var("operator"),
		"Prop_Operand1": ObjectRoles("operand1"),
		"Prop_Operand2": ObjectRoles("operand2"),
		"Prop_InitOperand2": ObjectRoles("init_operand2"),
	}, Obj{
		uast.KeyToken: Var("operator"),
		"Prop_Operand1": ObjectRoles("operand1", role.Binary, role.Expression, role.Left),
		"Prop_Operand2": ObjectRoles("operand2", role.Binary, role.Expression, role.Right),
		"Prop_InitOperand2": ObjectRoles("init_operand2", role.Binary, role.Expression, role.Right),
	}), LookupArrOpVar("operator", binaryExprRoles)),

	AnnotateType("CPPASTEqualsInitializer", nil, role.Declaration, role.Assignment,
		role.Expression, role.Right),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("struct")},
	} , role.Declaration, role.Type),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("struct")},
		"Prop_Members": {Arr: true, Roles: role.Roles{role.Declaration, role.Type}},
	} , role.Declaration, role.Type),

	AnnotateType("CPPASTElaboratedTypeSpecifier", FieldRoles{
		"Kind": {Op: String("enum")},
	} , role.Declaration, role.Type, role.Enumeration),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("class")},
	} , role.Declaration, role.Type),

	// No Union role
	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("union")},
		"Prop_Members": {Arr: true, Roles: role.Roles{role.Declaration, role.Type,
			role.Incomplete}},
		"Prop_Clauses": {Arr: true, Roles: role.Roles{role.Declaration, role.Type,
			role.Incomplete}},
	} , role.Declaration, role.Type, role.Incomplete),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("union")},
		"Prop_Members": {Arr: true, Roles: role.Roles{role.Declaration, role.Type,
			role.Incomplete}},
	} , role.Declaration, role.Type, role.Incomplete),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("class")},
		"Prop_Members": {Arr: true, Roles: role.Roles{role.Declaration}},
	} , role.Declaration, role.Type),

	AnnotateType("CPPASTCompositeTypeSpecifier", FieldRoles{
		"Key": {Op: String("class")},
		"Prop_Members": {Arr: true, Roles: role.Roles{role.Declaration, role.Type}},
		"Prop_BaseSpecifiers": {Arr: true, Roles: role.Roles{role.Base,
			role.Declaration}},
	} , role.Declaration, role.Type),

	AnnotateType("CPPASTWhileStatement", ObjRoles{
		"Prop_Body": {role.While},
		"Prop_Condition": {role.While, role.Condition},
	}, role.Statement, role.While),

	AnnotateType("CPPASTDoStatement", ObjRoles{
		"Prop_Body": {role.While},
		"Prop_Condition": {role.While, role.Condition},
	}, role.Statement, role.While),

	AnnotateType("CPPASTSwitchStatement", ObjRoles{
		"Prop_Body": {role.Switch},
		"Prop_ControllerExpression": {role.Switch, role.Condition, role.Expression},
	}, role.Statement, role.Switch),
	AnnotateType("CPPASTCaseStatement", nil, role.Switch, role.Case),
	AnnotateType("CPPASTDefaultStatement", nil, role.Switch, role.Case, role.Default),

	AnnotateType("CPPASTAliasDeclaration", ObjRoles{
		"Prop_Alias": {role.Alias, role.Left},
		"Prop_MappingTypeId": {role.Alias, role.Right},
	}, role.Alias),

	AnnotateType("CPPASTForStatement", ObjRoles{
		"Prop_Body": {role.For},
		"Prop_InitializerStatement": {role.For, role.Initialization},
		"Prop_IterationExpression": {role.For, role.Update, role.Expression},
	}, role.For, role.Statement),

	AnnotateType("CPPASTRangeBasedForStatement", ObjRoles{
		"Prop_Body": {role.For},
		"Prop_Declaration": {role.For, role.Declaration, role.Variable},
		"Prop_InitializerClause": {role.For, role.Iterator},
	}, role.For, role.Statement),

	AnnotateType("CPPASTIfStatement", ObjRoles{
		"Prop_ThenClause": {role.If, role.Then},
		"Prop_ElseClause": {role.If, role.Else},
		"Prop_ConditionalExpression": {role.If, role.Condition, role.Expression},
	}, role.If, role.Statement),

	AnnotateType("CPPASTFunctionCallExpression", FieldRoles{
		"Prop_Arguments": {Arr: true, Roles: role.Roles{role.Function, role.Call,
			role.Argument}},
		"Prop_FunctionNameExpression": {Roles: role.Roles{role.Function, role.Call,
			role.Name}},
	}, role.Function, role.Call, role.Expression),

	AnnotateType("CPPASTFunctionCallExpression", FieldRoles{
		"Prop_FunctionNameExpression": {Roles: role.Roles{role.Function, role.Call,
			role.Name}},
	}, role.Function, role.Call, role.Expression),

	AnnotateType("CPPASTLambdaExpression", FieldRoles{
		"Prop_Body": { Roles: role.Roles{role.Function, role.Declaration}},
		"Prop_Declarator": { Roles: role.Roles{role.Function, role.Declaration,
			role.Type}},
		"Prop_Captures": {Arr: true, Roles: role.Roles{role.Function, role.Declaration,
			role.Incomplete}},
	}, role.Function, role.Declaration, role.Anonymous, role.Expression),

	AnnotateType("CPPASTLambdaExpression", FieldRoles{
		"Prop_Body": { Roles: role.Roles{role.Function, role.Declaration}},
		"Prop_Declarator": { Roles: role.Roles{role.Function, role.Declaration,
			role.Type}},
	}, role.Function, role.Declaration, role.Anonymous, role.Expression),

	AnnotateType("CPPASTArrayDeclarator", FieldRoles{
		"Prop_Initializer": { Opt: true, Roles: role.Roles{role.List, role.Initialization,
			role.Right}},
		// Dimensions and sizes
		"Prop_ArrayModifiers": { Arr: true, Roles: role.Roles{role.List,
			role.Declaration}},
	}, role.List, role.Declaration),

	// Dimension
	AnnotateType("CPPASTArrayModifier", nil, role.Type, role.Incomplete),
	// Index (on usage, not declaration), like a[1]
	AnnotateType("CPPASTArraySubscriptExpression", nil, role.List, role.Value,
		role.Incomplete),

	AnnotateType("CPPASTTryBlockStatement", FieldRoles{
		"Prop_TryBody": {Roles: role.Roles{role.Try, role.Body}},
		"Prop_CatchHandlers": {Arr: true, Roles: role.Roles{role.Try, role.Catch}},
	}, role.Try, role.Statement),

	AnnotateType("CPPASTCatchHandler", ObjRoles{
		"Prop_Declaration": {role.Catch, role.Type, role.Argument},
		"Prop_CatchBody": {role.Catch, role.Body},
	}),

	AnnotateType("CPPASTQualifiedName", FieldRoles{
		"Prop_AllSegments": {Arr: true, Roles: role.Roles{role.Qualified}},
		"Prop_Qualifier": {Arr: true, Roles: role.Roles{role.Identifier}},
	}, role.Qualified),

	AnnotateType("CPPASTConstructorChainInitializer", ObjRoles{
		"Prop_MemberInitializerId": {role.Type, role.Declaration, role.Initialization,
			role.Incomplete},
	}, role.Type, role.Declaration, role.Initialization, role.Incomplete),

	AnnotateType("CPPASTConstructorInitializer", FieldRoles{
		"Prop_Arguments": {Arr: true, Roles: role.Roles{role.Initialization,
			role.Declaration, role.Argument, role.Value, role.Incomplete}},
		"Prop_Expression": {Roles: role.Roles{role.Initialization, role.Declaration,
			role.Value, role.Incomplete}},
	}, role.Initialization, role.Declaration, role.Incomplete),
	AnnotateType("CPPASTConstructorInitializer", nil, role.Initialization,
		role.Declaration, role.Incomplete),

	AnnotateType("Comment", MapObj(Obj{
		"Comment": UncommentCLike("text"),
	}, Obj{
		uast.KeyToken: Var("text"),
	}), role.Noop, role.Comment),

	AnnotateType("CPPASTFieldDeclarator", ObjRoles{
		"Prop_BitFieldSize": {role.Type, role.Declaration, role.Number, role.Incomplete},
	}, role.Type, role.Declaration, role.Incomplete),
}
