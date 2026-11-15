package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"Backend/internal/middleware"
	"Backend/internal/store"

	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type MockBlocklistStore struct {
	items  map[uint]*store.BlocklistItem
	nextID uint
}

func NewMockBlocklistStore() *MockBlocklistStore {
	return &MockBlocklistStore{
		items:  make(map[uint]*store.BlocklistItem),
		nextID: 1,
	}
}

func (m *MockBlocklistStore) GetByUserID(userID uint, itemType string) ([]store.BlocklistItem, error) {
	var result []store.BlocklistItem
	for _, it := range m.items {
		if it.UserID == userID {
			if itemType == "" || strings.EqualFold(it.Type, itemType) {
				result = append(result, *it)
			}
		}
	}
	return result, nil
}

func (m *MockBlocklistStore) GetByID(userID uint, id uint) (*store.BlocklistItem, error) {
	it, ok := m.items[id]
	if !ok || it.UserID != userID {
		return nil, store.ErrBlockItemNotFound
	}
	return it, nil
}

func (m *MockBlocklistStore) Create(item *store.BlocklistItem) (*store.BlocklistItem, error) {
	// Check for conflict
	for _, it := range m.items {
		if it.UserID == item.UserID && it.Type == item.Type && it.Value == item.Value {
			it.Label = item.Label
			it.IsEnabled = item.IsEnabled
			it.UpdatedAt = time.Now()
			return it, nil
		}
	}

	item.ID = m.nextID
	item.CreatedAt = time.Now()
	item.UpdatedAt = time.Now()
	m.items[item.ID] = item
	m.nextID++
	return item, nil
}

func (m *MockBlocklistStore) BulkCreate(userID uint, items []store.BlocklistItem) ([]store.BlocklistItem, error) {
	var created []store.BlocklistItem
	for _, it := range items {
		itCopy := it
		saved, err := m.Create(&itCopy)
		if err != nil {
			return nil, err
		}
		created = append(created, *saved)
	}
	return created, nil
}

func (m *MockBlocklistStore) ToggleEnabled(userID uint, id uint, isEnabled bool) (*store.BlocklistItem, error) {
	it, err := m.GetByID(userID, id)
	if err != nil {
		return nil, err
	}
	it.IsEnabled = isEnabled
	it.UpdatedAt = time.Now()
	return it, nil
}

func (m *MockBlocklistStore) Delete(userID uint, id uint) error {
	it, ok := m.items[id]
	if !ok || it.UserID != userID {
		return store.ErrBlockItemNotFound
	}
	delete(m.items, id)
	return nil
}

func TestBlocklistHandler_NormalizeBlockValue(t *testing.T) {
	tests := []struct {
		name      string
		itemType  string
		raw       string
		expected  string
		expectErr bool
	}{
		{"full URL with https and www", store.BlockTypeWebsite, "https://www.youtube.com/watch?v=123", "youtube.com", false},
		{"URL with http and trailing slash", store.BlockTypeWebsite, "http://reddit.com/", "reddit.com", false},
		{"URL with port and path", store.BlockTypeWebsite, "http://localhost:8080/dashboard", "localhost", false},
		{"app package name", store.BlockTypeApp, "  com.instagram.android  ", "com.instagram.android", false},
		{"keyword with uppercase and whitespace", store.BlockTypeKeyword, "  Adult Content  ", "adult content", false},
		{"empty string returns error", store.BlockTypeWebsite, "   ", "", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			val, err := normalizeBlockValue(tt.itemType, tt.raw)
			if tt.expectErr {
				assert.Error(t, err)
			} else {
				require.NoError(t, err)
				assert.Equal(t, tt.expected, val)
			}
		})
	}
}

func TestBlocklistHandler_HandleGetBlocklist(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockBlocklistStore()
	handler := NewBlocklistHandler(mockStore, logger)

	user1 := &store.User{ID: 1, Email: "user1@pguard.local"}
	user2 := &store.User{ID: 2, Email: "user2@pguard.local"}

	// Seed items
	_, _ = mockStore.Create(&store.BlocklistItem{UserID: 1, Type: "website", Value: "tiktok.com", Label: "TikTok", IsEnabled: true})
	_, _ = mockStore.Create(&store.BlocklistItem{UserID: 1, Type: "app", Value: "com.twitter.android", Label: "Twitter", IsEnabled: true})
	_, _ = mockStore.Create(&store.BlocklistItem{UserID: 2, Type: "website", Value: "onlyfans.com", Label: "OnlyFans", IsEnabled: true})

	t.Run("returns unauthorized if not authenticated", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/blocklist", nil)
		rr := httptest.NewRecorder()

		handler.HandleGetBlocklist(rr, req)

		assert.Equal(t, http.StatusUnauthorized, rr.Code)
	})

	t.Run("fetches all items for authenticated user only", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/blocklist", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetBlocklist(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Items []store.BlocklistItem `json:"items"`
			Count int                   `json:"count"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 2, resp.Count)
		assert.Len(t, resp.Items, 2)

		// Verify user2's items are not returned
		for _, item := range resp.Items {
			assert.NotEqual(t, user2.ID, item.UserID)
		}
	})

	t.Run("filters by type parameter", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/blocklist?type=website", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetBlocklist(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Items []store.BlocklistItem `json:"items"`
			Count int                   `json:"count"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 1, resp.Count)
		assert.Equal(t, "tiktok.com", resp.Items[0].Value)
	})

	t.Run("rejects invalid type parameter", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/blocklist?type=invalid", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetBlocklist(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})
}

func TestBlocklistHandler_HandleCreateBlocklistItem(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockBlocklistStore()
	handler := NewBlocklistHandler(mockStore, logger)

	user := &store.User{ID: 10, Email: "user10@pguard.local"}

	t.Run("successfully creates single website item with normalization", func(t *testing.T) {
		body := `{"type": "website", "value": "https://www.youtube.com/shorts", "label": "YouTube"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/blocklist", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleCreateBlocklistItem(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)

		var resp struct {
			Message string              `json:"message"`
			Item    store.BlocklistItem `json:"item"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "youtube.com", resp.Item.Value)
		assert.Equal(t, "YouTube", resp.Item.Label)
		assert.True(t, resp.Item.IsEnabled)
	})

	t.Run("successfully bulk creates items", func(t *testing.T) {
		body := `{
			"items": [
				{"type": "app", "value": "com.snapchat.android", "label": "Snapchat"},
				{"type": "keyword", "value": "pornography", "label": "Porn Keyword"}
			]
		}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/blocklist", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleCreateBlocklistItem(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)

		var resp struct {
			Count int                   `json:"count"`
			Items []store.BlocklistItem `json:"items"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 2, resp.Count)
	})

	t.Run("rejects invalid item type", func(t *testing.T) {
		body := `{"type": "unsupported", "value": "something"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/blocklist", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleCreateBlocklistItem(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})
}

func TestBlocklistHandler_HandleToggleAndDelete(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockBlocklistStore()
	handler := NewBlocklistHandler(mockStore, logger)

	user1 := &store.User{ID: 1, Email: "user1@pguard.local"}
	user2 := &store.User{ID: 2, Email: "user2@pguard.local"}

	item1, _ := mockStore.Create(&store.BlocklistItem{UserID: 1, Type: "website", Value: "twitter.com", IsEnabled: true})

	t.Run("toggles is_enabled status", func(t *testing.T) {
		body := `{"is_enabled": false}`
		req := httptest.NewRequest(http.MethodPatch, fmt.Sprintf("/api/v1/blocklist/%d", item1.ID), strings.NewReader(body))
		req = middleware.SetUser(req, user1)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", item1.ID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleToggleBlocklistItem(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Item store.BlocklistItem `json:"item"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.False(t, resp.Item.IsEnabled)
	})

	t.Run("prevents User 2 from toggling User 1's item", func(t *testing.T) {
		body := `{"is_enabled": true}`
		req := httptest.NewRequest(http.MethodPatch, fmt.Sprintf("/api/v1/blocklist/%d", item1.ID), strings.NewReader(body))
		req = middleware.SetUser(req, user2)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", item1.ID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleToggleBlocklistItem(rr, req)

		assert.Equal(t, http.StatusNotFound, rr.Code)
	})

	t.Run("deletes item successfully", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/v1/blocklist/%d", item1.ID), nil)
		req = middleware.SetUser(req, user1)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", item1.ID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleDeleteBlocklistItem(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		// Verify deleted
		_, err := mockStore.GetByID(1, item1.ID)
		assert.ErrorIs(t, err, store.ErrBlockItemNotFound)
	})
}
