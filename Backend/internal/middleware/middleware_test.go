package middleware

import (
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"Backend/internal/store"
	"Backend/internal/tokens"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type mockUserStore struct {
	user *store.User
}

func (m *mockUserStore) FindOrCreateGoogleUser(googleID, email, name, avatarURL string) (*store.User, error) {
	return m.user, nil
}
func (m *mockUserStore) GetUserByID(id uint) (*store.User, error) {
	if m.user != nil && m.user.ID == id {
		return m.user, nil
	}
	return nil, nil
}
func (m *mockUserStore) GetUserByEmail(email string) (*store.User, error) {
	return m.user, nil
}
func (m *mockUserStore) GetUserByGoogleID(googleID string) (*store.User, error) {
	return m.user, nil
}
func (m *mockUserStore) UpdateUser(user *store.User) error {
	return nil
}

func TestUserMiddleware(t *testing.T) {
	os.Setenv("JWT_SECRET", "test-secret-key-min-32-chars-long")
	tokenService := tokens.NewTokenService()

	testUser := &store.User{
		ID:          99,
		Email:       "test@example.com",
		DisplayName: "Test Middleware User",
	}
	userStore := &mockUserStore{user: testUser}
	mw := UserMiddleware{UserStore: userStore, TokenService: tokenService}

	t.Run("unauthenticated request sets anonymous user", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/test", nil)
		w := httptest.NewRecorder()

		handlerCalled := false
		next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			handlerCalled = true
			u := GetUser(r)
			assert.True(t, u.IsAnonymous())
		})

		mw.Authenticate(next).ServeHTTP(w, req)
		assert.True(t, handlerCalled)
	})

	t.Run("valid token sets authenticated user in context", func(t *testing.T) {
		accessToken, _, err := tokenService.GenerateAccessToken(testUser.ID, testUser.Email)
		require.NoError(t, err)

		req := httptest.NewRequest(http.MethodGet, "/test", nil)
		req.Header.Set("Authorization", "Bearer "+accessToken)
		w := httptest.NewRecorder()

		handlerCalled := false
		next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			handlerCalled = true
			u := GetUser(r)
			assert.False(t, u.IsAnonymous())
			assert.Equal(t, testUser.ID, u.ID)
			assert.Equal(t, testUser.Email, u.Email)
		})

		mw.Authenticate(next).ServeHTTP(w, req)
		assert.True(t, handlerCalled)
	})

	t.Run("RequireUser blocks anonymous user with 401", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/protected", nil)
		req = SetUser(req, store.AnonymousUser)
		w := httptest.NewRecorder()

		nextCalled := false
		protectedHandler := mw.RequireUser(func(w http.ResponseWriter, r *http.Request) {
			nextCalled = true
		})

		protectedHandler.ServeHTTP(w, req)
		assert.False(t, nextCalled)
		assert.Equal(t, http.StatusUnauthorized, w.Code)
	})

	t.Run("RequireUser allows authenticated user through", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/protected", nil)
		req = SetUser(req, testUser)
		w := httptest.NewRecorder()

		nextCalled := false
		protectedHandler := mw.RequireUser(func(w http.ResponseWriter, r *http.Request) {
			nextCalled = true
			w.WriteHeader(http.StatusOK)
		})

		protectedHandler.ServeHTTP(w, req)
		assert.True(t, nextCalled)
		assert.Equal(t, http.StatusOK, w.Code)
	})
}
