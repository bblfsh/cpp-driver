mnum foo {
  bar = 0,
  baz = 1,
  pok = 2
};

int main() {
  enum foo f;
  f = foo::bar;
  f = pok;
}
