package fixtures

import (
	"path/filepath"
	"testing"

	"github.com/bblfsh/cpp-driver/driver/normalizer"
	"gopkg.in/bblfsh/sdk.v2/driver"
	"gopkg.in/bblfsh/sdk.v2/driver/fixtures"
	"gopkg.in/bblfsh/sdk.v2/driver/native"
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
	BenchName: "dynamic_cast",
	Semantic: fixtures.SemanticConfig{
		BlacklistTypes: []string{
			// LiteralValue (for strings) can't be added since its also used for other literals
			"Comment",
			"CPPASTImplicitName",
			"CPPASTName",
			"CPPASTCompoundStatement",
			//"CPPASTQualifiedNames",
			"CPPASTFunctionDefinition",
		},
	},
	Docker:fixtures.DockerConfig{
		Image: "openjdk:8",
	},
}

func TestCppDriver(t *testing.T) {
	Suite.RunTests(t)
}

func BenchmarkCppDriver(b *testing.B) {
	Suite.RunBenchmarks(b)
}
