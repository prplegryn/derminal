# Third party software

derminal application code is licensed under Apache-2.0. Components retain their own licenses.

| Component | Version / source | License |
| --- | --- | --- |
| Termux terminal-emulator / terminal-view | termux/termux-app commit `8629e632fcb95da272221be327db653fb24befe9` | Apache-2.0 |
| PRoot | termux/proot 5.1.107.98 | GPL-2.0-or-later, see upstream COPYING |
| talloc | Samba talloc 2.5.0 | LGPL-3.0-or-later |
| libandroid-shmem | termux/libandroid-shmem 0.7 | Apache-2.0 |
| Debian 13 userspace | proot-distro v4.26.0 base, upgraded to Debian 13.7 | Per-package licenses in `/usr/share/doc/*/copyright` |
| AndroidX / Compose | versions in Gradle | Apache-2.0 |
| Kotlin / coroutines | versions in Gradle | Apache-2.0 |

Terminal library Java and C source is vendored with original copyright notices. Their Gradle build files were adapted for this project, while terminal implementation is retained. Additional hardening changes are documented in the git history.

Each application Release includes `derminal-runtime-sources.tar.gz` containing the exact PRoot, talloc and shared memory source archives, native `.deb` packages, Debian corresponding source archives and package inventory, build recipes and Termux packaging patches. Debian redistribution notices and licenses remain inside the offline filesystem. Each installed binary's corresponding source package version is also recorded.

Native changes: the PRoot and talloc ELF string `libtalloc.so.2` is padded to `libtalloc.so` without changing offsets, allowing Android's native library filename convention. The loader is selected using `PROOT_LOADER`, dependencies via `LD_LIBRARY_PATH`; no Termux installation is required. PTY is compiled from source with NDK and 16 KB alignment.
