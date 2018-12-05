package normalizer

import (
	"fmt"
	"github.com/stretchr/testify/require"
	"gopkg.in/bblfsh/sdk.v2/uast/nodes"
	"testing"

	. "gopkg.in/bblfsh/sdk.v2/uast/transformer"
)

func TestOpJoinNamesArray(t *testing.T) {
	op := opJoinNamesArray{Var("x")}
	st := NewState()
	n1 := nodes.String("a::b::c")
	fmt.Println(string(n1) == "a::b::c") // true

	res, err := op.Check(st, n1)
	require.NoError(t, err)
	require.True(t, res)

	n2, ok := st.GetVar("x")
	require.True(t, ok)
	require.NotNil(t, n2)
	//fmt.Println(n2) // prints the QualifiedIdentifier correctly

	n3, err := op.Construct(st, nil)
	require.NotNil(t, n3)
	require.NoError(t, err)
	fmt.Println("XXX n3")
	fmt.Println(n3)
	x, ok := n3.(nodes.String)
	require.True(t, ok)
	fmt.Println("XXX should be String")
	fmt.Println(x) // empty
	fmt.Println(string(x))
	fmt.Println(string(x) == "a::b::c") // false
}
