package mailer

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestResendMailer_MockWithoutKey(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mailer := &ResendMailer{
		apiKey:     "",
		fromEmail:  "PGuard <onboarding@resend.dev>",
		httpClient: http.DefaultClient,
		logger:     logger,
	}

	id, err := mailer.SendEmail(context.Background(), []string{"test@example.com"}, "Test Subject", "<p>Hello</p>", "Hello")
	if err != nil {
		t.Fatalf("expected nil error, got: %v", err)
	}
	if id == "" {
		t.Fatal("expected non-empty mock ID")
	}
}

func TestResendMailer_SendUnlockRequest(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	var receivedBody resendEmailRequest
	called := false

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		called = true
		if r.Header.Get("Authorization") != "Bearer test_key" {
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		if err := json.NewDecoder(r.Body).Decode(&receivedBody); err != nil {
			http.Error(w, "bad body", http.StatusBadRequest)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{"id": "resend_test_123"}`))
	}))
	defer server.Close()

	mailer := &ResendMailer{
		apiKey:     "test_key",
		apiURL:     server.URL,
		fromEmail:  "PGuard <onboarding@resend.dev>",
		httpClient: server.Client(),
		logger:     logger,
	}

	err := mailer.SendUnlockRequest(context.Background(), "partner@example.com", "user@example.com", "<script>YouTube Shorts</script>", "123456")
	if err != nil {
		t.Fatalf("expected nil error, got: %v", err)
	}
	if !called {
		t.Fatal("expected httptest server to receive request")
	}
	if strings.Contains(receivedBody.HTML, "<script>") {
		t.Fatal("expected HTML to escape script tags")
	}
	if !strings.Contains(receivedBody.HTML, "&lt;script&gt;YouTube Shorts&lt;/script&gt;") {
		t.Fatal("expected escaped feature name in email HTML")
	}
}
