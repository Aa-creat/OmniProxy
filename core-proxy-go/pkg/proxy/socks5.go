package proxy

import (
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"strconv"
	"sync"
	"time"
)

type Socks5Server struct {
	addr     string
	username string
	password string
	listener net.Listener
	mu       sync.Mutex
	running  bool
}

func NewSocks5Server(host string, port int, username, password string) *Socks5Server {
	return &Socks5Server{
		addr:     fmt.Sprintf("%s:%d", host, port),
		username: username,
		password: password,
	}
}

func (s *Socks5Server) Start() error {
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

func (s *Socks5Server) Stop() {
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

func (s *Socks5Server) handleConnection(conn net.Conn) {
	IncActive()
	defer DecActive()
	defer conn.Close()

	if err := s.auth(conn); err != nil {
		return
	}

	targetAddr, err := s.readRequest(conn)
	if err != nil {
		return
	}

	targetConn, err := net.DialTimeout("tcp", targetAddr, 10*time.Second)
	if err != nil {
		// General SOCKS server failure
		_, _ = conn.Write([]byte{0x05, 0x01, 0x00, 0x01, 0, 0, 0, 0, 0, 0})
		return
	}
	defer targetConn.Close()

	// Succeeded response
	_, _ = conn.Write([]byte{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0})

	var wg sync.WaitGroup
	wg.Add(2)

	go func() {
		defer wg.Done()
		counted := NewCountingReader(conn, AddUpload)
		_, _ = io.Copy(targetConn, counted)
		if tc, ok := targetConn.(*net.TCPConn); ok {
			_ = tc.CloseWrite()
		}
	}()

	go func() {
		defer wg.Done()
		counted := NewCountingReader(targetConn, AddDownload)
		_, _ = io.Copy(conn, counted)
		if cc, ok := conn.(*net.TCPConn); ok {
			_ = cc.CloseWrite()
		}
	}()

	wg.Wait()
}

func (s *Socks5Server) auth(conn net.Conn) error {
	buf := make([]byte, 2)
	if _, err := io.ReadFull(conn, buf); err != nil {
		return err
	}
	if buf[0] != 0x05 {
		return errors.New("unsupported socks version")
	}

	numMethods := int(buf[1])
	methods := make([]byte, numMethods)
	if _, err := io.ReadFull(conn, methods); err != nil {
		return err
	}

	hasAuth := s.username != "" && s.password != ""
	if hasAuth {
		// Requires Username/Password (0x02)
		if _, err := conn.Write([]byte{0x05, 0x02}); err != nil {
			return err
		}
		return s.verifyUserPass(conn)
	}

	// No authentication required (0x00)
	_, err := conn.Write([]byte{0x05, 0x00})
	return err
}

func (s *Socks5Server) verifyUserPass(conn net.Conn) error {
	ver := make([]byte, 1)
	if _, err := io.ReadFull(conn, ver); err != nil {
		return err
	}
	ulen := make([]byte, 1)
	if _, err := io.ReadFull(conn, ulen); err != nil {
		return err
	}
	user := make([]byte, int(ulen[0]))
	if _, err := io.ReadFull(conn, user); err != nil {
		return err
	}
	plen := make([]byte, 1)
	if _, err := io.ReadFull(conn, plen); err != nil {
		return err
	}
	pass := make([]byte, int(plen[0]))
	if _, err := io.ReadFull(conn, pass); err != nil {
		return err
	}

	if string(user) == s.username && string(pass) == s.password {
		_, err := conn.Write([]byte{0x01, 0x00}) // Success
		return err
	}

	_, _ = conn.Write([]byte{0x01, 0x01}) // Failure
	return errors.New("authentication failed")
}

func (s *Socks5Server) readRequest(conn net.Conn) (string, error) {
	buf := make([]byte, 4)
	if _, err := io.ReadFull(conn, buf); err != nil {
		return "", err
	}

	if buf[1] != 0x01 { // 0x01 = CONNECT
		_, _ = conn.Write([]byte{0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0}) // Command not supported
		return "", errors.New("command not supported")
	}

	var host string
	switch buf[3] {
	case 0x01: // IPv4
		ip := make([]byte, 4)
		if _, err := io.ReadFull(conn, ip); err != nil {
			return "", err
		}
		host = net.IP(ip).String()
	case 0x03: // Domain
		dlen := make([]byte, 1)
		if _, err := io.ReadFull(conn, dlen); err != nil {
			return "", err
		}
		domain := make([]byte, int(dlen[0]))
		if _, err := io.ReadFull(conn, domain); err != nil {
			return "", err
		}
		host = string(domain)
	case 0x04: // IPv6
		ip := make([]byte, 16)
		if _, err := io.ReadFull(conn, ip); err != nil {
			return "", err
		}
		host = net.IP(ip).String()
	default:
		return "", errors.New("address type not supported")
	}

	portBuf := make([]byte, 2)
	if _, err := io.ReadFull(conn, portBuf); err != nil {
		return "", err
	}
	port := binary.BigEndian.Uint16(portBuf)

	return net.JoinHostPort(host, strconv.Itoa(int(port))), nil
}
