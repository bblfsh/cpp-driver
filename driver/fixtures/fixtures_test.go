package fixtures

import (
	"path/filepath"
	"testing"

	"github.com/bblfsh/cpp-driver/driver/normalizer"
	"github.com/bblfsh/sdk/v3/driver"
	"github.com/bblfsh/sdk/v3/driver/fixtures"
	"github.com/bblfsh/sdk/v3/driver/native"
)

const projectRoot = "../../"

var Suite = &fixtures.Suite{
	Lang: "cpp",
	Ext:  ".cpp",
	Path: filepath.Join(projectRoot, fixtures.Dir),
	NewDriver: func() driver.Native {
		return native.NewDriverAt(filepath.Join(projectRoot, "build/bin/native"), native.UTF8)
	},
	Transforms: normalizer.Transforms,
	BenchName:  "dynamic_cast",
	Semantic: fixtures.SemanticConfig{
		BlacklistTypes: []string{
			// LiteralValue (for strings) can't be added since its also used for other literals
			"Comment",
			"CPPASTImplicitName",
			"CPPASTName",
			"CPPASTOperatorName",
			"CPPASTCompoundStatement",
			"CPPASTQualifiedName",
			"CPPASTFunctionDefinition",
			"ASTInclusionStatement",
		},
	},
}

func TestCppDriver(t *testing.T) {
	Suite.RunTests(t)
}

func BenchmarkCppDriver(b *testing.B) {
	Suite.RunBenchmarks(b)
}
