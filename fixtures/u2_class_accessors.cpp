class testcls1
{
  int _x;

  public:
  int get_x();
  void set_x(int x);
};

int testcls1::get_x() {
  return _x;
}

void testcls1::set_x(int x) {
  _x = x;
}
