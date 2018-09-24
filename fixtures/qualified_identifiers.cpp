struct Foo
{
  int a;
};

struct Bar
{
  Foo f;
};

int main() {
  Bar b;
  b.f.a = 10;
}
