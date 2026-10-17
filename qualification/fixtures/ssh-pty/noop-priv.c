/*
 * Test-only privilege compatibility shim for the rootless OpenSSH fixture.
 *
 * The execution environment already denies setgroups in its outer user namespace and bwrap
 * rejects chroot. OpenSSH's real password, channel, PTY, shell and SFTP paths remain unchanged;
 * these three OS privilege calls alone return success inside the private mount namespace.
 * This library is never packaged in an app and establishes no privilege-isolation evidence.
 */
#include <grp.h>
#include <unistd.h>

int chroot(const char *path) {
    (void)path;
    return 0;
}

int setgroups(size_t size, const gid_t *list) {
    (void)size;
    (void)list;
    return 0;
}

int initgroups(const char *user, gid_t group) {
    (void)user;
    (void)group;
    return 0;
}
