#include <iostream>

#define JOIN(a,b) a ## b

int main() {
  JOIN(std::c, out) << "test";
  int a = JOIN(3, 4);
}
