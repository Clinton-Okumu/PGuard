package middleware

import (
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"Backend/internal/utils"
)

// MaxBodySize limits request body bytes to prevent unbounded payload memory exhaustion.
func MaxBodySize(maxBytes int64) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Body != nil {
				r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
			}
			next.ServeHTTP(w, r)
		})
	}
}

type rateEntry struct {
	count   int
	resetAt time.Time
}

// RateLimiter provides thread-safe fixed-window per-client-IP rate limiting.
type RateLimiter struct {
	mu          sync.Mutex
	limit       int
	window      time.Duration
	entries     map[string]*rateEntry
	lastCleanup time.Time
}

func NewRateLimiter(limit int, window time.Duration) *RateLimiter {
	return &RateLimiter{
		limit:       limit,
		window:      window,
		entries:     make(map[string]*rateEntry),
		lastCleanup: time.Now(),
	}
}

func (rl *RateLimiter) allow(key string, now time.Time) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()

	if now.Sub(rl.lastCleanup) > rl.window {
		for k, v := range rl.entries {
			if now.After(v.resetAt) {
				delete(rl.entries, k)
			}
		}
		rl.lastCleanup = now
	}

	entry, ok := rl.entries[key]
	if !ok || now.After(entry.resetAt) {
		rl.entries[key] = &rateEntry{
			count:   1,
			resetAt: now.Add(rl.window),
		}
		return true
	}

	if entry.count >= rl.limit {
		return false
	}
	entry.count++
	return true
}

func (rl *RateLimiter) Middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		ip := extractClientIP(r)
		if !rl.allow(ip, time.Now()) {
			_ = utils.WriteJSON(w, http.StatusTooManyRequests, utils.Envelope{
				"error": "Too many requests. Please wait and try again.",
			})
			return
		}
		next.ServeHTTP(w, r)
	})
}

func extractClientIP(r *http.Request) string {
	if xff := strings.TrimSpace(r.Header.Get("X-Forwarded-For")); xff != "" {
		parts := strings.Split(xff, ",")
		if len(parts) > 0 && strings.TrimSpace(parts[0]) != "" {
			return strings.TrimSpace(parts[0])
		}
	}
	host, _, err := net.SplitHostPort(strings.TrimSpace(r.RemoteAddr))
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}
