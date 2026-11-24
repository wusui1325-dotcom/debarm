#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <android/log.h>

#define TAG "debarm-pty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static void throwIo(JNIEnv* env, const char* where) {
    char buf[192];
    snprintf(buf, sizeof(buf), "%s: %s", where, strerror(errno));
    jclass cls = env->FindClass("java/io/IOException");
    if (cls) env->ThrowNew(cls, buf);
}

static char** to_cstr_array(JNIEnv* env, jobjectArray arr, int* outCount) {
    const jint n = env->GetArrayLength(arr);
    char** out = (char**)calloc((size_t)n + 1, sizeof(char*));
    if (!out) return nullptr;
    for (jint i = 0; i < n; i++) {
        auto s = (jstring)env->GetObjectArrayElement(arr, i);
        if (!s) {
            out[i] = strdup("");
            continue;
        }
        const char* utf = env->GetStringUTFChars(s, nullptr);
        out[i] = strdup(utf ? utf : "");
        env->ReleaseStringUTFChars(s, utf);
        env->DeleteLocalRef(s);
    }
    out[n] = nullptr;
    *outCount = n;
    return out;
}

static void free_cstr_array(char** a) {
    if (!a) return;
    for (int i = 0; a[i]; i++) free(a[i]);
    free(a);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_wusper_debarm_core_Pty_nativeStart(
        JNIEnv* env, jclass,
        jobjectArray jargv,
        jobjectArray jenvp,
        jstring jcwd,
        jint rows,
        jint cols) {
    int argc = 0, envc = 0;
    char** argv = to_cstr_array(env, jargv, &argc);
    char** envp = to_cstr_array(env, jenvp, &envc);
    char cwd[1024];
    cwd[0] = 0;
    if (jcwd) {
        const char* c = env->GetStringUTFChars(jcwd, nullptr);
        if (c) {
            strncpy(cwd, c, sizeof(cwd) - 1);
            env->ReleaseStringUTFChars(jcwd, c);
        }
    }

    if (!argv || !envp || argc < 1) {
        free_cstr_array(argv);
        free_cstr_array(envp);
        errno = EINVAL;
        throwIo(env, "argv");
        return nullptr;
    }

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0) {
        free_cstr_array(argv);
        free_cstr_array(envp);
        throwIo(env, "posix_openpt");
        return nullptr;
    }
    if (grantpt(master) != 0 || unlockpt(master) != 0) {
        close(master);
        free_cstr_array(argv);
        free_cstr_array(envp);
        throwIo(env, "grantpt/unlockpt");
        return nullptr;
    }
    char slavePath[64];
    if (ptsname_r(master, slavePath, sizeof(slavePath)) != 0) {
        close(master);
        free_cstr_array(argv);
        free_cstr_array(envp);
        throwIo(env, "ptsname_r");
        return nullptr;
    }

    struct winsize ws {};
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    ioctl(master, TIOCSWINSZ, &ws);

    pid_t pid = fork();
    if (pid < 0) {
        close(master);
        free_cstr_array(argv);
        free_cstr_array(envp);
        throwIo(env, "fork");
        return nullptr;
    }
    if (pid == 0) {
        /* Child of a multi-threaded process: only async-signal-safe calls. */
        close(master);
        if (setsid() < 0) _exit(127);
        int slave = open(slavePath, O_RDWR);
        if (slave < 0) _exit(126);
        ioctl(slave, TIOCSCTTY, 0);
        ioctl(slave, TIOCSWINSZ, &ws);
        dup2(slave, 0);
        dup2(slave, 1);
        dup2(slave, 2);
        if (slave > 2) close(slave);
        if (cwd[0]) {
            if (chdir(cwd) != 0) _exit(125);
        }
        execve(argv[0], argv, envp);
        _exit(127);
    }

    free_cstr_array(argv);
    free_cstr_array(envp);

    jint buf[2] = {(jint)master, (jint)pid};
    jintArray out = env->NewIntArray(2);
    if (!out) {
        kill(pid, SIGKILL);
        close(master);
        return nullptr;
    }
    env->SetIntArrayRegion(out, 0, 2, buf);
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wusper_debarm_core_Pty_nativeRead(
        JNIEnv* env, jclass, jint fd, jbyteArray jbuf) {
    const jint len = env->GetArrayLength(jbuf);
    jbyte* buf = env->GetByteArrayElements(jbuf, nullptr);
    if (!buf) return -1;
    ssize_t n;
    do {
        n = read((int)fd, buf, (size_t)len);
    } while (n < 0 && errno == EINTR);
    env->ReleaseByteArrayElements(jbuf, buf, 0);
    if (n < 0) {
        if (errno == EAGAIN || errno == EIO) return 0;
        throwIo(env, "read");
        return -1;
    }
    return (jint)n;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wusper_debarm_core_Pty_nativeWrite(
        JNIEnv* env, jclass, jint fd, jbyteArray jbuf, jint off, jint len) {
    jbyte* buf = env->GetByteArrayElements(jbuf, nullptr);
    if (!buf) return -1;
    ssize_t n;
    do {
        n = write((int)fd, buf + off, (size_t)len);
    } while (n < 0 && errno == EINTR);
    env->ReleaseByteArrayElements(jbuf, buf, JNI_ABORT);
    if (n < 0) {
        throwIo(env, "write");
        return -1;
    }
    return (jint)n;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wusper_debarm_core_Pty_nativeSetWinsize(
        JNIEnv*, jclass, jint fd, jint rows, jint cols) {
    struct winsize ws {};
    ws.ws_row = (unsigned short)(rows > 0 ? rows : 24);
    ws.ws_col = (unsigned short)(cols > 0 ? cols : 80);
    ioctl((int)fd, TIOCSWINSZ, &ws);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wusper_debarm_core_Pty_nativeKill(JNIEnv*, jclass, jint pid, jint sig) {
    if (pid > 0) {
        /* Negative pid: signal the process group started via setsid. */
        kill(-(pid_t)pid, sig);
        kill((pid_t)pid, sig);
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wusper_debarm_core_Pty_nativeWaitpid(
        JNIEnv*, jclass, jint pid, jboolean hang) {
    if (pid <= 0) return -1;
    int status = 0;
    int flags = hang == JNI_TRUE ? 0 : WNOHANG;
    pid_t r;
    do {
        r = waitpid((pid_t)pid, &status, flags);
    } while (r < 0 && errno == EINTR);
    if (r <= 0) return -1;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wusper_debarm_core_Pty_nativeClose(JNIEnv*, jclass, jint fd) {
    if (fd >= 0) close((int)fd);
}
