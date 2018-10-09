class testcls1 {
  public:
    testcls1() {}
};

class testcls2 : testcls1 {
  testcls2() : testcls1() {}
};
