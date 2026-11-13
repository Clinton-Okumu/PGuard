package tokens

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestTokenService_AccessToken(t *testing.T) {
	service := NewTokenService()

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
	service := NewTokenService()

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
