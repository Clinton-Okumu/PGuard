package auth

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGoogleVerifier_VerifyToken(t *testing.T) {
	ctx := context.Background()

	t.Run("rejects empty token", func(t *testing.T) {
		v := &GoogleVerifier{allowedClientIDs: []string{"client-123"}, devMockAuth: true}
		payload, err := v.VerifyToken(ctx, "")
		assert.Nil(t, payload)
		assert.Error(t, err)
	})

	t.Run("rejects mock token when DEV_MOCK_AUTH is false", func(t *testing.T) {
		t.Setenv("DEV_MOCK_AUTH", "false")
		t.Setenv("GOOGLE_CLIENT_IDS", "client-123")
		v := NewGoogleVerifier()

		payload, err := v.VerifyToken(ctx, "mock:user@pguard.local")
		assert.Nil(t, payload)
		assert.ErrorIs(t, err, ErrInvalidGoogleToken)
	})

	t.Run("accepts mock token when DEV_MOCK_AUTH is true", func(t *testing.T) {
		t.Setenv("DEV_MOCK_AUTH", "true")
		t.Setenv("GOOGLE_CLIENT_IDS", "")
		v := NewGoogleVerifier()

		payload, err := v.VerifyToken(ctx, "mock:Alice:alice@pguard.local")
		require.NoError(t, err)
		assert.Equal(t, "mock_google_alice@pguard.local", payload.GoogleID)
		assert.Equal(t, "alice@pguard.local", payload.Email)
		assert.Equal(t, "Alice", payload.Name)
	})

	t.Run("fails closed when GOOGLE_CLIENT_IDS is empty and DEV_MOCK_AUTH is false", func(t *testing.T) {
		t.Setenv("DEV_MOCK_AUTH", "false")
		t.Setenv("GOOGLE_CLIENT_IDS", "")
		v := NewGoogleVerifier()

		payload, err := v.VerifyToken(ctx, "some.jwt.token")
		assert.Nil(t, payload)
		assert.ErrorIs(t, err, ErrInvalidGoogleToken)
		assert.Contains(t, err.Error(), "GOOGLE_CLIENT_IDS")
	})
}
