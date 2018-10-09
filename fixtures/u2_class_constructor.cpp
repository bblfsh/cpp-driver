class testcls1
{
  public:
    testcls1();
};

testcls1::testcls1() {}

class testcls2
{
  testcls2() = default;
};

class testcls3
{
  testcls3() = delete;
};

class testcls4
{
  public:
    explicit testcls4();
};
