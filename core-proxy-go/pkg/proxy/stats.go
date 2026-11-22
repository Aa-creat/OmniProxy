package proxy

import (
	"io"
	"sync/atomic"
)

type Stats struct {
	ActiveConnections int64 `json:"activeConnections"`
	BytesUploaded     int64 `json:"bytesUploaded"`
	BytesDownloaded   int64 `json:"bytesDownloaded"`
}

var GlobalStats Stats

func IncActive() {
	atomic.AddInt64(&GlobalStats.ActiveConnections, 1)
}

func DecActive() {
	atomic.AddInt64(&GlobalStats.ActiveConnections, -1)
}

func AddUpload(n int64) {
	atomic.AddInt64(&GlobalStats.BytesUploaded, n)
}

func AddDownload(n int64) {
	atomic.AddInt64(&GlobalStats.BytesDownloaded, n)
}

func ResetStats() {
	atomic.StoreInt64(&GlobalStats.ActiveConnections, 0)
	atomic.StoreInt64(&GlobalStats.BytesUploaded, 0)
	atomic.StoreInt64(&GlobalStats.BytesDownloaded, 0)
}

// CountingReader wraps an io.Reader and counts bytes read
type CountingReader struct {
	reader io.Reader
	onRead func(int64)
}

func NewCountingReader(r io.Reader, onRead func(int64)) *CountingReader {
	return &CountingReader{reader: r, onRead: onRead}
}

func (cr *CountingReader) Read(p []byte) (int, error) {
	n, err := cr.reader.Read(p)
	if n > 0 && cr.onRead != nil {
		cr.onRead(int64(n))
	}
	return n, err
}
