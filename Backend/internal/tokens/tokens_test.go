package tokens

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTokenService_NewTokenServiceValidation(t *testing.T) {
	t.Run("fails when JWT_SECRET is unset and DEV_MOCK_AUTH is false", func(t *testing.T) {
		t.Setenv("JWT_SECRET", "")
		t.Setenv("DEV_MOCK_AUTH", "false")

		svc, err := NewTokenService()
		assert.Nil(t, svc)
		assert.Error(t, err)
	})

	t.Run("fails when JWT_SECRET is shorter than 32 chars in non-dev mode", func(t *testing.T) {
		t.Setenv("JWT_SECRET", "too-short-secret")
		t.Setenv("DEV_MOCK_AUTH", "false")

		svc, err := NewTokenService()
		assert.Nil(t, svc)
		assert.Error(t, err)
	})

	t.Run("allows dev fallback when DEV_MOCK_AUTH is true", func(t *testing.T) {
		t.Setenv("JWT_SECRET", "")
		t.Setenv("DEV_MOCK_AUTH", "true")

		svc, err := NewTokenService()
		require.NoError(t, err)
		assert.NotNil(t, svc)
	})
}

func TestTokenService_AccessToken(t *testing.T) {
	t.Setenv("JWT_SECRET", "test-secret-key-min-32-chars-long")
	service, err := NewTokenService()
	require.NoError(t, err)

	userID := uint(42)
	email := "test@example.com"

	// 1. Generate access token
	tokenStr, expiresIn, err := service.GenerateAccessToken(userID, email)
	require.NoError(t, err)
	assert.NotEmpty(t, tokenStr)
	assert.Greater(t, expiresIn, int64(0))

	// 2. Validate valid access token
	claims, err := service.ValidateAccessToken(tokenStr)
	require.NoError(t, err)
	assert.Equal(t, userID, claims.UserID)
	assert.Equal(t, email, claims.Email)
	assert.Equal(t, "pguard-backend", claims.Issuer)

	// 3. Reject invalid / forged token
	_, err = service.ValidateAccessToken("invalid.token.signature")
	assert.Error(t, err)
	assert.ErrorIs(t, err, ErrInvalidToken)
}

func TestTokenService_RefreshToken(t *testing.T) {
	t.Setenv("JWT_SECRET", "test-secret-key-min-32-chars-long")
	service, err := NewTokenService()
	require.NoError(t, err)

	plain1, hash1, expiry1, err := service.GenerateRefreshToken()
	require.NoError(t, err)
	assert.NotEmpty(t, plain1)
	assert.NotEmpty(t, hash1)
	assert.True(t, expiry1.After(time.Now()))

	plain2, hash2, _, err := service.GenerateRefreshToken()
	require.NoError(t, err)
	assert.NotEqual(t, plain1, plain2, "tokens should be unique")
	assert.NotEqual(t, hash1, hash2, "hashes should be unique")

	// Consistent hashing
	assert.Equal(t, hash1, HashToken(plain1))
}
