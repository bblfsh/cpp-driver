#include <algorithm>
#include <vector>

int main() {
  std::vector<int> foo;
  foo.push_back(1);

  auto foofunc = [](const int& n) { };
  std::for_each(foo.begin(), foo.end(), foofunc);
}
