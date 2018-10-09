#include <string>

void somefunc(int a) {}
void somefunc(double b) {}
void somefunc(std::string s) {}

int main() {
  somefunc(42);
  somefunc(3.14);
  somefunc("foo");
}
