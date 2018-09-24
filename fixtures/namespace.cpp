namespace foo {
  int a = 1;

  namespace bar {
    int b = 2;
  }
}

namespace baz {
  int c = 3;
}

using namespace baz;

int main() {
  foo::a += 1;
  foo::bar::b += 3;

  c += 1;
}
