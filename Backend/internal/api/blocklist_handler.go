package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"

	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/utils"

	"github.com/go-chi/chi/v5"
)

type BlocklistHandler struct {
	blocklistStore store.BlocklistStore
	logger         *log.Logger
}

func NewBlocklistHandler(blocklistStore store.BlocklistStore, logger *log.Logger) *BlocklistHandler {
	return &BlocklistHandler{
		blocklistStore: blocklistStore,
		logger:         logger,
	}
}

type blocklistPayload struct {
	Type      string `json:"type"`
	Value     string `json:"value"`
	Label     string `json:"label"`
	IsEnabled *bool  `json:"is_enabled"`
}

type createBlocklistRequest struct {
	// Single item fields
	Type      string `json:"type"`
	Value     string `json:"value"`
	Label     string `json:"label"`
	IsEnabled *bool  `json:"is_enabled"`

	// Bulk items support
	Items []blocklistPayload `json:"items"`
}

type toggleBlocklistRequest struct {
	IsEnabled *bool `json:"is_enabled"`
}

func normalizeBlockValue(itemType, rawValue string) (string, error) {
	val := strings.TrimSpace(rawValue)
	if val == "" {
		return "", store.ErrEmptyBlockValue
	}

	switch strings.ToLower(itemType) {
	case store.BlockTypeWebsite:
		// Strip protocol
		val = strings.TrimPrefix(val, "https://")
		val = strings.TrimPrefix(val, "http://")
		// Strip path, query params, hash
		if idx := strings.IndexAny(val, "/?#"); idx != -1 {
			val = val[:idx]
		}
		// Strip port if present
		if colonIdx := strings.LastIndex(val, ":"); colonIdx != -1 {
			val = val[:colonIdx]
		}
		// Strip leading www.
		val = strings.TrimPrefix(val, "www.")
		val = strings.ToLower(val)
	case store.BlockTypeKeyword:
		val = strings.ToLower(val)
	case store.BlockTypeApp:
		val = strings.TrimSpace(val)
	}

	if val == "" {
		return "", store.ErrEmptyBlockValue
	}
	return val, nil
}

func (h *BlocklistHandler) HandleGetBlocklist(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	itemType := r.URL.Query().Get("type")
	if itemType != "" && !store.IsValidBlockType(itemType) {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{
			"error": "Invalid type parameter. Must be 'app', 'website', or 'keyword'",
		})
		return
	}

	items, err := h.blocklistStore.GetByUserID(user.ID, itemType)
	if err != nil {
		h.logger.Printf("ERROR: get blocklist for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to fetch blocklist"})
		return
	}

	if items == nil {
		items = []store.BlocklistItem{}
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{"items": items, "count": len(items)})
}

func (h *BlocklistHandler) HandleCreateBlocklistItem(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req createBlocklistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Printf("ERROR: decode create blocklist body: %v", err)
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	// Case 1: Bulk creation
	if len(req.Items) > 0 {
		var toCreate []store.BlocklistItem
		for _, raw := range req.Items {
			if !store.IsValidBlockType(raw.Type) {
				_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{
					"error": "Invalid item type '" + raw.Type + "', must be 'app', 'website', or 'keyword'",
				})
				return
			}

			normVal, err := normalizeBlockValue(raw.Type, raw.Value)
			if err != nil {
				_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid value: " + err.Error()})
				return
			}

			enabled := true
			if raw.IsEnabled != nil {
				enabled = *raw.IsEnabled
			}

			label := strings.TrimSpace(raw.Label)
			if label == "" {
				label = normVal
			}

			toCreate = append(toCreate, store.BlocklistItem{
				UserID:    user.ID,
				Type:      strings.ToLower(strings.TrimSpace(raw.Type)),
				Value:     normVal,
				Label:     label,
				IsEnabled: enabled,
			})
		}

		created, err := h.blocklistStore.BulkCreate(user.ID, toCreate)
		if err != nil {
			h.logger.Printf("ERROR: bulk create blocklist for user %d: %v", user.ID, err)
			_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to create blocklist items"})
			return
		}

		_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
			"message": "Blocklist items created successfully",
			"items":   created,
			"count":   len(created),
		})
		return
	}

	// Case 2: Single item creation
	if !store.IsValidBlockType(req.Type) {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{
			"error": "Invalid type, must be 'app', 'website', or 'keyword'",
		})
		return
	}

	normVal, err := normalizeBlockValue(req.Type, req.Value)
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": err.Error()})
		return
	}

	enabled := true
	if req.IsEnabled != nil {
		enabled = *req.IsEnabled
	}

	label := strings.TrimSpace(req.Label)
	if label == "" {
		label = normVal
	}

	newItem := &store.BlocklistItem{
		UserID:    user.ID,
		Type:      strings.ToLower(strings.TrimSpace(req.Type)),
		Value:     normVal,
		Label:     label,
		IsEnabled: enabled,
	}

	saved, err := h.blocklistStore.Create(newItem)
	if err != nil {
		h.logger.Printf("ERROR: create blocklist item: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to create blocklist item"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
		"message": "Blocklist item created successfully",
		"item":    saved,
	})
}

func (h *BlocklistHandler) HandleToggleBlocklistItem(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	rawID := chi.URLParam(r, "id")
	id64, err := strconv.ParseUint(rawID, 10, 32)
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid item ID"})
		return
	}

	var req toggleBlocklistRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Printf("ERROR: decode toggle blocklist body: %v", err)
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if req.IsEnabled == nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "is_enabled field is required"})
		return
	}

	updated, err := h.blocklistStore.ToggleEnabled(user.ID, uint(id64), *req.IsEnabled)
	if err != nil {
		if errors.Is(err, store.ErrBlockItemNotFound) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "Blocklist item not found"})
			return
		}
		h.logger.Printf("ERROR: toggle blocklist item %d: %v", id64, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to update blocklist item"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message": "Blocklist item updated",
		"item":    updated,
	})
}

func (h *BlocklistHandler) HandleDeleteBlocklistItem(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	rawID := chi.URLParam(r, "id")
	id64, err := strconv.ParseUint(rawID, 10, 32)
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid item ID"})
		return
	}

	err = h.blocklistStore.Delete(user.ID, uint(id64))
	if err != nil {
		if errors.Is(err, store.ErrBlockItemNotFound) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "Blocklist item not found"})
			return
		}
		h.logger.Printf("ERROR: delete blocklist item %d: %v", id64, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to delete blocklist item"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"status":  "deleted",
		"message": "Blocklist item deleted successfully",
	})
}
