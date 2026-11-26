package proxy

import (
	"context"
	"crypto/subtle"
	"errors"
	"net"
	"syscall"
	"time"
)

var errBlockedAddress = errors.New("target address is blocked by SSRF policy")

var blockedCIDRs = func() []*net.IPNet {
	cidrs := []string{
		"0.0.0.0/8",          // Unspecified / current network
		"10.0.0.0/8",         // RFC1918 private
		"100.64.0.0/10",      // CGNAT
		"127.0.0.0/8",        // IPv4 loopback
		"169.254.0.0/16",     // IPv4 link-local / cloud metadata
		"172.16.0.0/12",      // RFC1918 private
		"192.0.0.0/24",       // IETF protocol assignments
		"192.168.0.0/16",     // RFC1918 private
		"224.0.0.0/4",        // Multicast
		"240.0.0.0/4",        // Reserved / broadcast
		"::/128",             // IPv6 unspecified
		"::1/128",            // IPv6 loopback
		"fc00::/7",           // IPv6 unique local
		"fe80::/10",          // IPv6 link-local
		"ff00::/8",           // IPv6 multicast
	}
	nets := make([]*net.IPNet, 0, len(cidrs))
	for _, c := range cidrs {
		_, ipNet, err := net.ParseCIDR(c)
		if err == nil {
			nets = append(nets, ipNet)
		}
	}
	return nets
}()

// isBlockedIP reports whether ip belongs to loopback, link-local, private, or reserved ranges.
func isBlockedIP(ip net.IP) bool {
	if ip == nil {
		return true
	}
	if ip4 := ip.To4(); ip4 != nil {
		ip = ip4
	}
	if ip.IsLoopback() || ip.IsLinkLocalUnicast() || ip.IsLinkLocalMulticast() || ip.IsMulticast() || ip.IsUnspecified() || ip.IsPrivate() {
		return true
	}
	for _, cidr := range blockedCIDRs {
		if cidr.Contains(ip) {
			return true
		}
	}
	return false
}

// isBlockedError reports whether err was caused by the SSRF address filter.
func isBlockedError(err error) bool {
	return errors.Is(err, errBlockedAddress)
}

// newSafeDialer returns a net.Dialer that rejects connections to local/private/reserved IPs
// after DNS resolution (preventing both direct IP SSRF and DNS rebinding).
func newSafeDialer(timeout time.Duration) *net.Dialer {
	return &net.Dialer{
		Timeout: timeout,
		Control: func(network, address string, c syscall.RawConn) error {
			host, _, err := net.SplitHostPort(address)
			if err != nil {
				return errBlockedAddress
			}
			ip := net.ParseIP(host)
			if isBlockedIP(ip) {
				return errBlockedAddress
			}
			return nil
		},
	}
}

func dialSafeTarget(ctx context.Context, address string, timeout time.Duration) (net.Conn, error) {
	return newSafeDialer(timeout).DialContext(ctx, "tcp", address)
}

func constantTimeEqual(a, b string) bool {
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

// acceptWithBackoff accepts a connection or backs off exponentially on transient errors.
func acceptWithBackoff(ln net.Listener, isRunning func() bool, tempDelay *time.Duration) (net.Conn, bool, error) {
	conn, err := ln.Accept()
	if err == nil {
		*tempDelay = 0
		return conn, true, nil
	}
	if !isRunning() {
		return nil, false, err
	}
	if *tempDelay == 0 {
		*tempDelay = 5 * time.Millisecond
	} else {
		*tempDelay *= 2
	}
	if max := 1 * time.Second; *tempDelay > max {
		*tempDelay = max
	}
	time.Sleep(*tempDelay)
	return nil, true, err
}
