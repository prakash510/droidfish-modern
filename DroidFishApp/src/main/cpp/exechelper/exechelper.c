#include <unistd.h>
#include <stdio.h>

int main(int argc, char *argv[]) {
    if (argc < 2) {
        return 1;
    }
    execv(argv[1], &argv[1]);
    perror("execv");
    return 127;
}
