#include <memory>

struct Foo {};

int main() {
  auto fptr1 = std::unique_ptr<Foo>(new Foo);
  auto fptr2 = std::make_unique<Foo>(new Foo);
  auto fptr3 = std::move(fptr1);
}
