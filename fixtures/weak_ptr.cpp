#include <memory>
using namespace std;

struct Foo{};

int main() {
  auto ptr1 = make_shared<Foo>(new Foo);
  weak_ptr<Foo> cache_ptr = ptr1;
  if (auto real_ptr = cache_ptr.lock()) {}
}
