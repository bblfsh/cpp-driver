int main() {
  int a = 2;
  int b;

  switch(a) {
    case 1:
      b = 1;
    case 2:
      b = 3; break;
    case 3: {
      b = 4;
      break;
    }
    default:
      b = 10;
      break;
  };
}
