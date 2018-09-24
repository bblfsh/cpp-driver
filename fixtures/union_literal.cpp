union foo {int a; double b;};

int main() {
  foo f = {1};
  foo g = {.a=1};
  foo h = {.b=1.0};
}
