#include <memory>
using namespace std;

struct Foo {};

int main() {
  auto sp1 = make_shared<Foo>();
  shared_ptr<Foo> sp2(new Foo);
  auto sp3 = shared_ptr<Foo>(new Foo);
}
