struct S {int a;} s;

int main() {
  int* pint = reinterpret_cast<int*>(&s);
}
