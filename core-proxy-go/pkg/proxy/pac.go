package proxy

import (
	"fmt"
	"net"
	"net/http"
	"sync"
)

type PacServer struct {
	addr        string
	proxyHost   string
	proxyPort   int
	socksPort   int
	httpServer  *http.Server
	listener    net.Listener
	mu          sync.Mutex
	running     bool
}

func NewPacServer(host string, port int, proxyHost string, proxyPort, socksPort int) *PacServer {
	return &PacServer{
		addr:      fmt.Sprintf("%s:%d", host, port),
		proxyHost: proxyHost,
		proxyPort: proxyPort,
		socksPort: socksPort,
	}
}

func (s *PacServer) Start() error {
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

	mux := http.NewServeMux()
	mux.HandleFunc("/", s.handlePac)

	s.httpServer = &http.Server{Handler: mux}

	go func() {
		_ = s.httpServer.Serve(ln)
	}()

	return nil
}

func (s *PacServer) Stop() {
	s.mu.Lock()
	defer s.mu.Unlock()
	if !s.running {
		return
	}
	s.running = false
	if s.httpServer != nil {
		_ = s.httpServer.Close()
	}
}

func (s *PacServer) handlePac(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/x-ns-proxy-autoconfig")
	w.Header().Set("Cache-Control", "no-cache")

	proxyLine := fmt.Sprintf("PROXY %s:%d; DIRECT", s.proxyHost, s.proxyPort)
	if s.socksPort > 0 {
		proxyLine = fmt.Sprintf("PROXY %s:%d; SOCKS5 %s:%d; DIRECT", s.proxyHost, s.proxyPort, s.proxyHost, s.socksPort)
	}

	pacContent := fmt.Sprintf(`function FindProxyForURL(url, host) {
    if (isPlainHostName(host) ||
        shExpMatch(host, "*.local") ||
        isInNet(dnsResolve(host), "10.0.0.0", "255.0.0.0") ||
        isInNet(dnsResolve(host), "172.16.0.0", "255.240.0.0") ||
        isInNet(dnsResolve(host), "192.168.0.0", "255.255.0.0") ||
        isInNet(dnsResolve(host), "127.0.0.0", "255.0.0.0")) {
        return "DIRECT";
    }
    return "%s";
}`, proxyLine)

	_, _ = w.Write([]byte(pacContent))
}
