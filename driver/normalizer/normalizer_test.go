package normalizer

import (
	"github.com/bblfsh/sdk/v3/uast/nodes"
	"github.com/stretchr/testify/require"
	"testing"

	. "github.com/bblfsh/sdk/v3/uast/transformer"
)

func TestOpJoinNamesArray(t *testing.T) {
	op := opJoinNamesArray{Var("x")}
	st := NewState()
	n1 := nodes.String("a::b::c")

	res, err := op.Check(st, n1)
	require.NoError(t, err)
	require.True(t, res)

	n2, ok := st.GetVar("x")
	require.True(t, ok)
	require.NotNil(t, n2)
	require.Equal(t, nodes.Array{
		nodes.Object{
			"@type": nodes.String("uast:Identifier"),
			"@pos":  nodes.Object{"@type": nodes.String("uast:Positions")},
			"Name":  nodes.String("a"),
		},
		nodes.Object{
			"@type": nodes.String("uast:Identifier"),
			"@pos":  nodes.Object{"@type": nodes.String("uast:Positions")},
			"Name":  nodes.String("b"),
		},
		nodes.Object{
			"@type": nodes.String("uast:Identifier"),
			"@pos":  nodes.Object{"@type": nodes.String("uast:Positions")},
			"Name":  nodes.String("c"),
		},
	}, n2)

	n3, err := op.Construct(st, nil)
	require.NotNil(t, n3)
	require.NoError(t, err)
	n4, ok := n3.(nodes.String)
	require.True(t, ok)
	require.Equal(t, n1, n4)
	require.Equal(t, "a::b::c", string(n4))
}
