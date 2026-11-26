package proxy

import (
	"bufio"
	"context"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

const (
	httpHandshakeTimeout = 15 * time.Second
	httpDialTimeout      = 10 * time.Second
)

type HttpProxyServer struct {
	addr     string
	username string
	password string
	listener net.Listener
	mu       sync.Mutex
	running  bool
}

func NewHttpProxyServer(host string, port int, username, password string) *HttpProxyServer {
	return &HttpProxyServer{
		addr:     fmt.Sprintf("%s:%d", host, port),
		username: username,
		password: password,
	}
}

func (s *HttpProxyServer) Start() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.running {
		return nil
	}

	ln, err := net.Listen("tcp", s.addr)
	if err != nil {
		return err
	}
	s.listener = ln
	s.running = true

	go func() {
		var tempDelay time.Duration
		for {
			conn, keepGoing, err := acceptWithBackoff(ln, func() bool {
				s.mu.Lock()
				defer s.mu.Unlock()
				return s.running
			}, &tempDelay)
			if !keepGoing {
				return
			}
			if err != nil {
				continue
			}
			go s.handleConnection(conn)
		}
	}()

	return nil
}

func (s *HttpProxyServer) Stop() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.running {
		return
	}
	s.running = false
	if s.listener != nil {
		_ = s.listener.Close()
	}
}

func (s *HttpProxyServer) handleConnection(clientConn net.Conn) {
	IncActive()
	defer DecActive()
	defer clientConn.Close()

	_ = clientConn.SetReadDeadline(time.Now().Add(httpHandshakeTimeout))
	reader := bufio.NewReader(clientConn)
	req, err := http.ReadRequest(reader)
	if err != nil {
		return
	}
	_ = clientConn.SetReadDeadline(time.Time{})

	// Basic Authentication check whenever either username or password is configured
	if s.username != "" || s.password != "" {
		auth := req.Header.Get("Proxy-Authorization")
		if !s.checkAuth(auth) {
			resp := "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"OmniProxy\"\r\n\r\n"
			_, _ = clientConn.Write([]byte(resp))
			return
		}
	}

	if req.Method == http.MethodConnect {
		s.handleConnect(clientConn, req)
	} else {
		s.handleHttp(clientConn, req)
	}
}

func (s *HttpProxyServer) checkAuth(authHeader string) bool {
	if !strings.HasPrefix(authHeader, "Basic ") {
		return false
	}
	payload, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(authHeader, "Basic "))
	if err != nil {
		return false
	}
	pair := strings.SplitN(string(payload), ":", 2)
	return len(pair) == 2 && constantTimeEqual(pair[0], s.username) && constantTimeEqual(pair[1], s.password)
}

func (s *HttpProxyServer) handleConnect(clientConn net.Conn, req *http.Request) {
	targetConn, err := dialSafeTarget(context.Background(), req.Host, httpDialTimeout)
	if err != nil {
		if isBlockedError(err) {
			_, _ = clientConn.Write([]byte("HTTP/1.1 403 Forbidden\r\n\r\n"))
		} else {
			_, _ = clientConn.Write([]byte("HTTP/1.1 502 Bad Gateway\r\n\r\n"))
		}
		return
	}
	defer targetConn.Close()

	// 200 Connection Established
	if _, err := clientConn.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n")); err != nil {
		return
	}

	s.relay(clientConn, targetConn)
}

func (s *HttpProxyServer) handleHttp(clientConn net.Conn, req *http.Request) {
	targetHost := req.Host
	if targetHost == "" && req.URL != nil {
		targetHost = req.URL.Host
	}
	if targetHost == "" {
		_, _ = clientConn.Write([]byte("HTTP/1.1 400 Bad Request\r\n\r\n"))
		return
	}
	if !strings.Contains(targetHost, ":") {
		targetHost += ":80"
	}

	targetConn, err := dialSafeTarget(context.Background(), targetHost, httpDialTimeout)
	if err != nil {
		if isBlockedError(err) {
			_, _ = clientConn.Write([]byte("HTTP/1.1 403 Forbidden\r\n\r\n"))
		} else {
			_, _ = clientConn.Write([]byte("HTTP/1.1 502 Bad Gateway\r\n\r\n"))
		}
		return
	}
	defer targetConn.Close()

	req.Header.Del("Proxy-Authorization")
	req.Header.Del("Proxy-Connection")

	if err := req.Write(targetConn); err != nil {
		return
	}

	s.relay(clientConn, targetConn)
}

func (s *HttpProxyServer) relay(client, target net.Conn) {
	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		countedReader := NewCountingReader(client, AddUpload)
		_, _ = io.Copy(target, countedReader)
		if tc, ok := target.(*net.TCPConn); ok {
			_ = tc.CloseWrite()
		}
	}()

	go func() {
		defer wg.Done()
		countedReader := NewCountingReader(target, AddDownload)
		_, _ = io.Copy(client, countedReader)
		if cc, ok := client.(*net.TCPConn); ok {
			_ = cc.CloseWrite()
		}
	}()

	wg.Wait()
}
