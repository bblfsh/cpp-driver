struct point {
  double x;
  int y;

  void method(double newx) {
    x = newx;
  }
};

int main() {
  point p;
  p.x = 10.0;
  p.y = 42;
  p.method(100);

  point *ptr;
  ptr = &p;
  ptr->y = 10;
  (*ptr).x = 3.14;
}
