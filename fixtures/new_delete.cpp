#include <new>

struct Foo {};

int main() {
  auto f = new Foo;
  auto g = new Foo[5];
  delete f;
  delete[] g;
}
