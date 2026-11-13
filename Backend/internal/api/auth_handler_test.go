package api

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"Backend/internal/auth"
	"Backend/internal/store"
	"Backend/internal/tokens"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// MockUserStore implements store.UserStore in memory
type MockUserStore struct {
	users map[uint]*store.User
	nextID uint
}

func NewMockUserStore() *MockUserStore {
	return &MockUserStore{
		users:  make(map[uint]*store.User),
		nextID: 1,
	}
}

func (m *MockUserStore) FindOrCreateGoogleUser(googleID, email, name, avatarURL string) (*store.User, error) {
	for _, u := range m.users {
		if u.GoogleID == googleID || u.Email == email {
			u.DisplayName = name
			u.AvatarURL = avatarURL
			u.LastLoginAt = time.Now()
			return u, nil
		}
	}

	user := &store.User{
		ID:          m.nextID,
		GoogleID:    googleID,
		Email:       email,
		DisplayName: name,
		AvatarURL:   avatarURL,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
		LastLoginAt: time.Now(),
	}
	m.users[user.ID] = user
	m.nextID++
	return user, nil
}

func (m *MockUserStore) GetUserByID(id uint) (*store.User, error) {
	user, exists := m.users[id]
	if !exists {
		return nil, nil
	}
	return user, nil
}

func (m *MockUserStore) GetUserByEmail(email string) (*store.User, error) {
	for _, u := range m.users {
		if u.Email == email {
			return u, nil
		}
	}
	return nil, nil
}

func (m *MockUserStore) GetUserByGoogleID(googleID string) (*store.User, error) {
	for _, u := range m.users {
		if u.GoogleID == googleID {
			return u, nil
		}
	}
	return nil, nil
}

func (m *MockUserStore) UpdateUser(user *store.User) error {
	m.users[user.ID] = user
	return nil
}

// MockTokenStore implements store.TokenStore in memory
type MockTokenStore struct {
	tokens map[string]*store.RefreshToken
	nextID uint
}

func NewMockTokenStore() *MockTokenStore {
	return &MockTokenStore{
		tokens: make(map[string]*store.RefreshToken),
		nextID: 1,
	}
}

func (m *MockTokenStore) CreateRefreshToken(userID uint, tokenHash string, deviceInfo string, expiresAt time.Time) (*store.RefreshToken, error) {
	token := &store.RefreshToken{
		ID:         m.nextID,
		UserID:     userID,
		TokenHash:  tokenHash,
		DeviceInfo: deviceInfo,
		ExpiresAt:  expiresAt,
		Revoked:    false,
		CreatedAt:  time.Now(),
	}
	m.tokens[tokenHash] = token
	m.nextID++
	return token, nil
}

func (m *MockTokenStore) GetValidRefreshToken(tokenHash string) (*store.RefreshToken, error) {
	token, exists := m.tokens[tokenHash]
	if !exists || token.Revoked || token.ExpiresAt.Before(time.Now()) {
		return nil, nil
	}
	return token, nil
}

func (m *MockTokenStore) RevokeRefreshToken(tokenHash string) error {
	token, exists := m.tokens[tokenHash]
	if !exists {
		return errors.New("not found")
	}
	token.Revoked = true
	return nil
}

func (m *MockTokenStore) RevokeAllUserTokens(userID uint) error {
	for _, t := range m.tokens {
		if t.UserID == userID {
			t.Revoked = true
		}
	}
	return nil
}

func setupTestHandler() (*AuthHandler, *MockUserStore, *MockTokenStore, *tokens.TokenService) {
	os.Setenv("DEV_MOCK_AUTH", "true")
	os.Setenv("JWT_SECRET", "test-secret-key-min-32-chars-long")

	userStore := NewMockUserStore()
	tokenStore := NewMockTokenStore()
	googleVerifier := auth.NewGoogleVerifier()
	tokenService := tokens.NewTokenService()
	logger := log.New(io.Discard, "", 0)

	handler := NewAuthHandler(userStore, tokenStore, googleVerifier, tokenService, logger)
	return handler, userStore, tokenStore, tokenService
}

func TestHandleGoogleAuth(t *testing.T) {
	handler, _, _, _ := setupTestHandler()

	t.Run("successful google auth with mock token", func(t *testing.T) {
		payload := map[string]string{
			"id_token":    "mock:Test User:test@pguard.local",
			"device_info": "UnitTestRunner",
		}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/google", bytes.NewReader(body))
		w := httptest.NewRecorder()

		handler.HandleGoogleAuth(w, req)

		res := w.Result()
		defer res.Body.Close()

		assert.Equal(t, http.StatusOK, res.StatusCode)

		var resBody map[string]interface{}
		err := json.NewDecoder(res.Body).Decode(&resBody)
		require.NoError(t, err)

		assert.NotEmpty(t, resBody["access_token"])
		assert.NotEmpty(t, resBody["refresh_token"])
		assert.Equal(t, float64(1800), resBody["expires_in"])

		userData := resBody["user"].(map[string]interface{})
		assert.Equal(t, "test@pguard.local", userData["email"])
		assert.Equal(t, "Test User", userData["display_name"])
	})

	t.Run("missing id_token returns 400", func(t *testing.T) {
		payload := map[string]string{
			"device_info": "UnitTestRunner",
		}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/google", bytes.NewReader(body))
		w := httptest.NewRecorder()

		handler.HandleGoogleAuth(w, req)

		res := w.Result()
		defer res.Body.Close()

		assert.Equal(t, http.StatusBadRequest, res.StatusCode)
	})
}

func TestHandleRefreshToken(t *testing.T) {
	handler, userStore, tokenStore, tokenService := setupTestHandler()

	// Seed user & refresh token
	user, err := userStore.FindOrCreateGoogleUser("seed_gid", "seed@example.com", "Seed User", "")
	require.NoError(t, err)

	plainRefresh, hash, expiry, err := tokenService.GenerateRefreshToken()
	require.NoError(t, err)

	_, err = tokenStore.CreateRefreshToken(user.ID, hash, "device_test", expiry)
	require.NoError(t, err)

	t.Run("successful token refresh with rotation", func(t *testing.T) {
		payload := map[string]string{
			"refresh_token": plainRefresh,
			"device_info":   "device_test",
		}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh", bytes.NewReader(body))
		w := httptest.NewRecorder()

		handler.HandleRefreshToken(w, req)

		res := w.Result()
		defer res.Body.Close()

		assert.Equal(t, http.StatusOK, res.StatusCode)

		var resBody map[string]interface{}
		err := json.NewDecoder(res.Body).Decode(&resBody)
		require.NoError(t, err)

		assert.NotEmpty(t, resBody["access_token"])
		newRefresh := resBody["refresh_token"].(string)
		assert.NotEmpty(t, newRefresh)
		assert.NotEqual(t, plainRefresh, newRefresh, "refresh token must be rotated")

		// Old token must now be revoked
		revoked, _ := tokenStore.GetValidRefreshToken(hash)
		assert.Nil(t, revoked, "old refresh token should be revoked")
	})

	t.Run("invalid refresh token returns 401", func(t *testing.T) {
		payload := map[string]string{
			"refresh_token": "non_existent_token",
		}
		body, _ := json.Marshal(payload)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/refresh", bytes.NewReader(body))
		w := httptest.NewRecorder()

		handler.HandleRefreshToken(w, req)

		res := w.Result()
		defer res.Body.Close()

		assert.Equal(t, http.StatusUnauthorized, res.StatusCode)
	})
}

func TestHandleLogout(t *testing.T) {
	handler, userStore, tokenStore, tokenService := setupTestHandler()

	user, err := userStore.FindOrCreateGoogleUser("logout_gid", "logout@example.com", "Logout User", "")
	require.NoError(t, err)

	plainRefresh, hash, expiry, err := tokenService.GenerateRefreshToken()
	require.NoError(t, err)

	_, err = tokenStore.CreateRefreshToken(user.ID, hash, "device_logout", expiry)
	require.NoError(t, err)

	payload := map[string]string{
		"refresh_token": plainRefresh,
	}
	body, _ := json.Marshal(payload)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/logout", bytes.NewReader(body))
	w := httptest.NewRecorder()

	handler.HandleLogout(w, req)

	res := w.Result()
	defer res.Body.Close()

	assert.Equal(t, http.StatusOK, res.StatusCode)

	// Token must now be revoked
	valid, _ := tokenStore.GetValidRefreshToken(hash)
	assert.Nil(t, valid, "logged-out token must be revoked")
}
