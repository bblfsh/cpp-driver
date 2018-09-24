int main() {
  int a[] = {1, 2, 3};
  int b[3] = {1,2,3};
  int c[2][2];

  a[0] = 4;
  int d = a[1];
  c[0][0] = 42;
  int *ptr = &(b[0]);
  int f = *(++ptr);
}
