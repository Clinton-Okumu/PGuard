package api

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"Backend/internal/middleware"
	"Backend/internal/store"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type MockSettingsStore struct {
	settings map[uint]*store.UserSettings
}

func NewMockSettingsStore() *MockSettingsStore {
	return &MockSettingsStore{
		settings: make(map[uint]*store.UserSettings),
	}
}

func (m *MockSettingsStore) GetByUserID(userID uint) (*store.UserSettings, error) {
	if s, ok := m.settings[userID]; ok {
		return s, nil
	}
	defaults := store.DefaultUserSettings(userID)
	defaults.ID = userID
	m.settings[userID] = defaults
	return defaults, nil
}

func (m *MockSettingsStore) Upsert(settings *store.UserSettings) (*store.UserSettings, error) {
	m.settings[settings.UserID] = settings
	return settings, nil
}

func TestSettingsHandler_HandleGetSettings(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockSettingsStore()
	handler := NewSettingsHandler(mockStore, logger)

	t.Run("returns unauthorized if user is anonymous", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/settings", nil)
		req = middleware.SetUser(req, store.AnonymousUser)
		rr := httptest.NewRecorder()

		handler.HandleGetSettings(rr, req)

		assert.Equal(t, http.StatusUnauthorized, rr.Code)
	})

	t.Run("returns default settings for new user", func(t *testing.T) {
		user := &store.User{ID: 42, Email: "test@pguard.local"}
		req := httptest.NewRequest(http.MethodGet, "/api/v1/settings", nil)
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleGetSettings(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Settings store.UserSettings `json:"settings"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, uint(42), resp.Settings.UserID)
		assert.True(t, resp.Settings.BlockAdultContent)
		assert.False(t, resp.Settings.BlockShorts)
		assert.Equal(t, "This page is blocked.", resp.Settings.BlockedScreenMessage)
		assert.Equal(t, 3, resp.Settings.BlockedScreenCountdown)
	})
}

func TestSettingsHandler_HandleUpdateSettings(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockSettingsStore()
	handler := NewSettingsHandler(mockStore, logger)

	user := &store.User{ID: 10, Email: "user10@pguard.local"}

	t.Run("successfully updates partial fields", func(t *testing.T) {
		blockShorts := true
		countdown := 5
		customMsg := "Stay focused on your goals!"

		payload := updateSettingsRequest{
			BlockShorts:            &blockShorts,
			BlockedScreenCountdown: &countdown,
			BlockedScreenMessage:   &customMsg,
		}
		body, _ := json.Marshal(payload)

		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Message  string             `json:"message"`
			Settings store.UserSettings `json:"settings"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "Settings updated successfully", resp.Message)
		assert.True(t, resp.Settings.BlockShorts)
		assert.True(t, resp.Settings.BlockAdultContent) // retained default
		assert.Equal(t, 5, resp.Settings.BlockedScreenCountdown)
		assert.Equal(t, "Stay focused on your goals!", resp.Settings.BlockedScreenMessage)
	})

	t.Run("returns bad request for negative countdown", func(t *testing.T) {
		negativeCountdown := -1
		payload := updateSettingsRequest{
			BlockedScreenCountdown: &negativeCountdown,
		}
		body, _ := json.Marshal(payload)

		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("returns bad request for countdown exceeding 300", func(t *testing.T) {
		tooHigh := 301
		payload := updateSettingsRequest{
			BlockedScreenCountdown: &tooHigh,
		}
		body, _ := json.Marshal(payload)

		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("returns bad request for message exceeding 300 runes", func(t *testing.T) {
		longMsg := string(bytes.Repeat([]byte("a"), 301))
		payload := updateSettingsRequest{
			BlockedScreenMessage: &longMsg,
		}
		body, _ := json.Marshal(payload)

		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)

		tooManyRunes := strings.Repeat("🛡", 301)
		body2, _ := json.Marshal(updateSettingsRequest{BlockedScreenMessage: &tooManyRunes})
		req2 := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body2))
		req2 = middleware.SetUser(req2, user)
		rr2 := httptest.NewRecorder()
		handler.HandleUpdateSettings(rr2, req2)
		assert.Equal(t, http.StatusBadRequest, rr2.Code)
	})

	t.Run("rejects empty message and accepts multi-byte UTF-8 message within 300 runes", func(t *testing.T) {
		empty := "   "
		body, _ := json.Marshal(updateSettingsRequest{BlockedScreenMessage: &empty})
		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()
		handler.HandleUpdateSettings(rr, req)
		assert.Equal(t, http.StatusBadRequest, rr.Code)

		emojiMsg := strings.Repeat("🛡", 150)
		body2, _ := json.Marshal(updateSettingsRequest{BlockedScreenMessage: &emojiMsg})
		req2 := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body2))
		req2 = middleware.SetUser(req2, user)
		rr2 := httptest.NewRecorder()
		handler.HandleUpdateSettings(rr2, req2)
		assert.Equal(t, http.StatusOK, rr2.Code)
	})

	t.Run("returns bad request for unsafe redirect_url scheme", func(t *testing.T) {
		unsafe := "javascript:alert(1)"
		payload := updateSettingsRequest{
			RedirectURL: &unsafe,
		}
		body, _ := json.Marshal(payload)

		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("updates valid accountability_mode and rejects invalid modes", func(t *testing.T) {
		for _, valid := range []string{"friend", "self", "time_delay", "none"} {
			mode := valid
			body, _ := json.Marshal(updateSettingsRequest{AccountabilityMode: &mode})
			req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
			req = middleware.SetUser(req, user)
			rr := httptest.NewRecorder()

			handler.HandleUpdateSettings(rr, req)

			assert.Equal(t, http.StatusOK, rr.Code)
			var resp struct {
				Settings store.UserSettings `json:"settings"`
			}
			require.NoError(t, json.NewDecoder(rr.Body).Decode(&resp))
			assert.Equal(t, valid, resp.Settings.AccountabilityMode)
		}

		invalid := "bypass"
		body, _ := json.Marshal(updateSettingsRequest{AccountabilityMode: &invalid})
		req := httptest.NewRequest(http.MethodPut, "/api/v1/settings", bytes.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleUpdateSettings(rr, req)
		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})
}
