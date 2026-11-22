package proxy

import (
	"bufio"
	"encoding/base64"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
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
		for {
			conn, err := ln.Accept()
			if err != nil {
				s.mu.Lock()
				isRunning := s.running
				s.mu.Unlock()
				if !isRunning {
					return
				}
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

	reader := bufio.NewReader(clientConn)
	req, err := http.ReadRequest(reader)
	if err != nil {
		return
	}

	// Basic Authentication check
	if s.username != "" && s.password != "" {
		auth := req.Header.Get("Proxy-Authorization")
		if !s.checkAuth(auth) {
			resp := "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"EveryProxy\"\r\n\r\n"
			clientConn.Write([]byte(resp))
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
	return len(pair) == 2 && pair[0] == s.username && pair[1] == s.password
}

func (s *HttpProxyServer) handleConnect(clientConn net.Conn, req *http.Request) {
	targetConn, err := net.DialTimeout("tcp", req.Host, 10*time.Second)
	if err != nil {
		clientConn.Write([]byte("HTTP/1.1 502 Bad Gateway\r\n\r\n"))
		return
	}
	defer targetConn.Close()

	// 200 Connection Established
	clientConn.Write([]byte("HTTP/1.1 200 Connection Established\r\n\r\n"))

	s.relay(clientConn, targetConn)
}

func (s *HttpProxyServer) handleHttp(clientConn net.Conn, req *http.Request) {
	targetHost := req.Host
	if !strings.Contains(targetHost, ":") {
		targetHost += ":80"
	}

	targetConn, err := net.DialTimeout("tcp", targetHost, 10*time.Second)
	if err != nil {
		clientConn.Write([]byte("HTTP/1.1 502 Bad Gateway\r\n\r\n"))
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
