class testcls1 {
  int _a, _b;

  testcls1(int a, int b) : _a(a), _b(b) {}
  testcls1(int a) : testcls1(a, 10) {}
};
