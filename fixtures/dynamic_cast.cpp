using namespace std;

class Base { virtual void dummy() {} };
class Derived: public Base { int a; };

int main() {
    Base * pbase_derived = new Derived;
    Base * pbase_base = new Base;
    Derived * pderived;

    pderived = dynamic_cast<Derived*>(pbase_derived);
    pderived = dynamic_cast<Derived*>(pbase_base); // pderived = nullptr
}
