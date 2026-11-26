package proxy

import (
	"bufio"
	"context"
	"encoding/base64"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestIsBlockedIP(t *testing.T) {
	blocked := []string{
		"127.0.0.1",
		"127.0.1.1",
		"0.0.0.0",
		"10.0.0.1",
		"172.16.0.1",
		"192.168.1.1",
		"169.254.169.254",
		"::1",
		"::",
		"fe80::1",
		"fc00::1",
		"fd12:3456::1",
	}
	for _, raw := range blocked {
		ip := net.ParseIP(raw)
		if !isBlockedIP(ip) {
			t.Errorf("expected %s to be blocked", raw)
		}
	}

	allowed := []string{
		"1.1.1.1",
		"8.8.8.8",
		"93.184.216.34",
		"2606:4700:4700::1111",
	}
	for _, raw := range allowed {
		ip := net.ParseIP(raw)
		if isBlockedIP(ip) {
			t.Errorf("expected %s to be allowed", raw)
		}
	}
}

func TestDialSafeTargetBlocksLoopback(t *testing.T) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen failed: %v", err)
	}
	defer ln.Close()

	_, err = dialSafeTarget(context.Background(), ln.Addr().String(), 2*time.Second)
	if !isBlockedError(err) {
		t.Fatalf("expected SSRF block error for loopback, got: %v", err)
	}
}

func TestHttpProxyAuthSingleCredentialAndSSRF(t *testing.T) {
	srv := NewHttpProxyServer("127.0.0.1", 0, "admin", "")
	if err := srv.Start(); err != nil {
		t.Fatalf("start failed: %v", err)
	}
	defer srv.Stop()

	addr := srv.listener.Addr().String()

	// 1. Missing Proxy-Authorization must return 407 even when password is empty
	conn, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	_, _ = conn.Write([]byte("CONNECT 127.0.0.1:80 HTTP/1.1\r\nHost: 127.0.0.1:80\r\n\r\n"))
	resp, err := http.ReadResponse(bufio.NewReader(conn), nil)
	_ = conn.Close()
	if err != nil {
		t.Fatalf("read response failed: %v", err)
	}
	if resp.StatusCode != http.StatusProxyAuthRequired {
		t.Fatalf("expected 407, got %d", resp.StatusCode)
	}

	// 2. Valid auth targeting loopback must return 403 Forbidden
	conn2, err := net.DialTimeout("tcp", addr, 2*time.Second)
	if err != nil {
		t.Fatalf("dial failed: %v", err)
	}
	creds := base64.StdEncoding.EncodeToString([]byte("admin:"))
	_, _ = conn2.Write([]byte("CONNECT 127.0.0.1:80 HTTP/1.1\r\nHost: 127.0.0.1:80\r\nProxy-Authorization: Basic " + creds + "\r\n\r\n"))
	resp2, err := http.ReadResponse(bufio.NewReader(conn2), nil)
	_ = conn2.Close()
	if err != nil {
		t.Fatalf("read response failed: %v", err)
	}
	if resp2.StatusCode != http.StatusForbidden {
		t.Fatalf("expected 403 for SSRF target, got %d", resp2.StatusCode)
	}
}

func TestPacServerTimeoutsAndBody(t *testing.T) {
	srv := NewPacServer("127.0.0.1", 0, "192.168.43.1", 8080, 1080)
	req := httptest.NewRequest(http.MethodGet, "/", nil)
	w := httptest.NewRecorder()
	srv.handlePac(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	body := w.Body.String()
	if !strings.Contains(body, "PROXY 192.168.43.1:8080; SOCKS5 192.168.43.1:1080; DIRECT") {
		t.Fatalf("unexpected PAC content: %s", body)
	}
}
