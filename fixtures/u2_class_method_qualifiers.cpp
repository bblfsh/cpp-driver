class testcls1 {
  virtual void testfnc1() = 0;
  private: void testfnc2();
  protected: void testfnc3();
  public: void testfnc4();
  void testfnc5() const;
  static void testfnc6();
  inline void testfnc7();
};

void testcls1::testfnc2() {}
void testcls1::testfnc3() {}
void testcls1::testfnc4() {}
void testcls1::testfnc5() const {}
void testcls1::testfnc6() {}
void testcls1::testfnc7() {}
