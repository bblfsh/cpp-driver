class testcls1 {
  ~testcls1();
};

testcls1::~testcls1() {}

class testcls2 {
  ~testcls2() = default;
};

class testcls3 {
  ~testcls3()  = delete;
};
