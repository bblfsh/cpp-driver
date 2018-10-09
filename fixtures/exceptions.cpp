#include <stdexcept>

int main() {
  int a;

  try {
    throw 20;
  } catch (int e) {
    a = 1;
  }

  try {
    1/0;
  } catch (...) {
    a = 2;
  }

  try {
    throw std::runtime_error("some message");
  } catch(std::runtime_error& e) {
    e.what();
  }
}
