int main() {
  int i = 42;
  const int& ref = i;
  const int* ptr = &i;

  const_cast<int&>(ref) = 3;
  *(const_cast<int*>(ptr)) = 12;
}
