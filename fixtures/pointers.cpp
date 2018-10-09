struct Foo
{
  int a = 10;
};

void somefunc() {}

int main() {
  int bar = 10;
  int *pbar = &bar;
  int pok = *pbar;
  pbar++;
  int **ppbar = &pbar;
  ppbar = nullptr;

  Foo f;
  Foo *pf = &f;
  pf->a = 20;
  (*pf).a = 30;
  const char *old = "hello";

  void(*funcptr)() = somefunc;
}
