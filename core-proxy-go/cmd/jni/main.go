package main

/*
#include <jni.h>
#include <stdlib.h>
*/
import "C"
import (
	"encoding/json"
	"sync"
	"unsafe"

	"io.github.aacreat.omniproxy/core/pkg/proxy"
)

var (
	httpServer   *proxy.HttpProxyServer
	socksServer  *proxy.Socks5Server
	pacServer    *proxy.PacServer
	proxyMutex   sync.Mutex
)

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startHttpProxy
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startHttpProxy(
	env *C.JNIEnv,
	clazz C.jclass,
	host C.jstring,
	port C.jint,
	username C.jstring,
	password C.jstring,
) C.jboolean {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if httpServer != nil {
		httpServer.Stop()
	}

	h := jstringToString(env, host)
	u := jstringToString(env, username)
	p := jstringToString(env, password)

	httpServer = proxy.NewHttpProxyServer(h, int(port), u, p)
	if err := httpServer.Start(); err != nil {
		return C.JNI_FALSE
	}
	return C.JNI_TRUE
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopHttpProxy
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopHttpProxy(
	env *C.JNIEnv,
	clazz C.jclass,
) {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if httpServer != nil {
		httpServer.Stop()
		httpServer = nil
	}
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startSocks5Proxy
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startSocks5Proxy(
	env *C.JNIEnv,
	clazz C.jclass,
	host C.jstring,
	port C.jint,
	username C.jstring,
	password C.jstring,
) C.jboolean {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if socksServer != nil {
		socksServer.Stop()
	}

	h := jstringToString(env, host)
	u := jstringToString(env, username)
	p := jstringToString(env, password)

	socksServer = proxy.NewSocks5Server(h, int(port), u, p)
	if err := socksServer.Start(); err != nil {
		return C.JNI_FALSE
	}
	return C.JNI_TRUE
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopSocks5Proxy
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopSocks5Proxy(
	env *C.JNIEnv,
	clazz C.jclass,
) {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if socksServer != nil {
		socksServer.Stop()
		socksServer = nil
	}
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startPacServer
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_startPacServer(
	env *C.JNIEnv,
	clazz C.jclass,
	host C.jstring,
	port C.jint,
	proxyHost C.jstring,
	proxyPort C.jint,
	socksPort C.jint,
) C.jboolean {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if pacServer != nil {
		pacServer.Stop()
	}

	h := jstringToString(env, host)
	ph := jstringToString(env, proxyHost)

	pacServer = proxy.NewPacServer(h, int(port), ph, int(proxyPort), int(socksPort))
	if err := pacServer.Start(); err != nil {
		return C.JNI_FALSE
	}
	return C.JNI_TRUE
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopPacServer
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_stopPacServer(
	env *C.JNIEnv,
	clazz C.jclass,
) {
	proxyMutex.Lock()
	defer proxyMutex.Unlock()

	if pacServer != nil {
		pacServer.Stop()
		pacServer = nil
	}
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_getStatsJson
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_getStatsJson(
	env *C.JNIEnv,
	clazz C.jclass,
) C.jstring {
	data, err := json.Marshal(proxy.GlobalStats)
	if err != nil {
		return stringToJstring(env, "{}")
	}
	return stringToJstring(env, string(data))
}

//export Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_resetStats
func Java_io_github_aacreat_everyproxy_core_jni_NativeProxy_resetStats(
	env *C.JNIEnv,
	clazz C.jclass,
) {
	proxy.ResetStats()
}

func jstringToString(env *C.JNIEnv, jstr C.jstring) string {
	if jstr == nil {
		return ""
	}
	// Call GetStringUTFChars via JNI
	var isCopy C.jboolean
	fns := (**C.JNINativeInterface)(unsafe.Pointer(env))
	chars := C.CString("")
	if fns != nil && (*fns).GetStringUTFChars != nil {
		chars = (*fns).GetStringUTFChars(env, jstr, &isCopy)
		defer (*fns).ReleaseStringUTFChars(env, jstr, chars)
	}
	return C.GoString(chars)
}

func stringToJstring(env *C.JNIEnv, str string) C.jstring {
	cStr := C.CString(str)
	defer C.free(unsafe.Pointer(cStr))
	fns := (**C.JNINativeInterface)(unsafe.Pointer(env))
	if fns != nil && (*fns).NewStringUTF != nil {
		return (*fns).NewStringUTF(env, cStr)
	}
	return nil
}

func main() {}
