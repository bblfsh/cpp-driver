struct Foo {
  int b;
};

Foo testfnc1(int a, Foo f) {
  f.b = 1;
  return f;
}
