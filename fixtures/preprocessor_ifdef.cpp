#define FOO 1
#define BAR 10
#define POK 20

#ifdef FOO
  #define BAZ
#elif BAR>5
  #undef POK
#endif

int testfnc1() {
  int a;
  if (true) {
#ifdef FOO
    a = 1;
  } else {
    a = 2;
  }
#else
    a = 3;
  }
#endif

}
