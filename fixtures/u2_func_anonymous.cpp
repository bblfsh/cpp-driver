int main() {
  auto testfnc1 = [](auto a, auto b) { return a+b; };
  auto testfnc2 = []<class T>(T a, T b) { return a>b; };
  int a = 1;
  auto testfnc3 = [a](auto b) { return a+b; };
  auto testfnc4 = [&]() {};
  auto testfnc5 = [&, a]() { return a*2; };
  auto testfnc6 = [=]() {};
  auto testfnc7 = [=, &a]() { return a*2; };
}
