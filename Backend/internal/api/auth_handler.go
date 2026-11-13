package api

import (
	"encoding/json"
	"log"
	"net/http"

	"Backend/internal/auth"
	"Backend/internal/store"
	"Backend/internal/tokens"
	"Backend/internal/utils"
)

type AuthHandler struct {
	userStore      store.UserStore
	tokenStore     store.TokenStore
	googleVerifier *auth.GoogleVerifier
	tokenService   *tokens.TokenService
	logger         *log.Logger
}

func NewAuthHandler(
	userStore store.UserStore,
	tokenStore store.TokenStore,
	googleVerifier *auth.GoogleVerifier,
	tokenService *tokens.TokenService,
	logger *log.Logger,
) *AuthHandler {
	return &AuthHandler{
		userStore:      userStore,
		tokenStore:     tokenStore,
		googleVerifier: googleVerifier,
		tokenService:   tokenService,
		logger:         logger,
	}
}

type googleAuthRequest struct {
	IDToken    string `json:"id_token"`
	DeviceInfo string `json:"device_info"`
}

type refreshTokenRequest struct {
	RefreshToken string `json:"refresh_token"`
	DeviceInfo   string `json:"device_info"`
}

type logoutRequest struct {
	RefreshToken string `json:"refresh_token"`
}

func (h *AuthHandler) HandleGoogleAuth(w http.ResponseWriter, r *http.Request) {
	var req googleAuthRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Printf("ERROR: decode google auth body: %v", err)
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if req.IDToken == "" {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "id_token is required"})
		return
	}

	googlePayload, err := h.googleVerifier.VerifyToken(r.Context(), req.IDToken)
	if err != nil {
		h.logger.Printf("ERROR: google token verification: %v", err)
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Invalid Google token"})
		return
	}

	user, err := h.userStore.FindOrCreateGoogleUser(
		googlePayload.GoogleID,
		googlePayload.Email,
		googlePayload.Name,
		googlePayload.Picture,
	)
	if err != nil {
		h.logger.Printf("ERROR: find or create google user: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	accessToken, expiresIn, err := h.tokenService.GenerateAccessToken(user.ID, user.Email)
	if err != nil {
		h.logger.Printf("ERROR: generate access token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	plainRefresh, hash, expiresAt, err := h.tokenService.GenerateRefreshToken()
	if err != nil {
		h.logger.Printf("ERROR: generate refresh token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	_, err = h.tokenStore.CreateRefreshToken(user.ID, hash, req.DeviceInfo, expiresAt)
	if err != nil {
		h.logger.Printf("ERROR: store refresh token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"access_token":  accessToken,
		"refresh_token": plainRefresh,
		"expires_in":    expiresIn,
		"user":          user,
	})
}

func (h *AuthHandler) HandleRefreshToken(w http.ResponseWriter, r *http.Request) {
	var req refreshTokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Printf("ERROR: decode refresh request: %v", err)
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if req.RefreshToken == "" {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "refresh_token is required"})
		return
	}

	tokenHash := tokens.HashToken(req.RefreshToken)
	existingToken, err := h.tokenStore.GetValidRefreshToken(tokenHash)
	if err != nil || existingToken == nil {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Invalid or expired refresh token"})
		return
	}

	// Token rotation: Revoke existing refresh token immediately
	_ = h.tokenStore.RevokeRefreshToken(tokenHash)

	user, err := h.userStore.GetUserByID(existingToken.UserID)
	if err != nil || user == nil {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "User not found"})
		return
	}

	accessToken, expiresIn, err := h.tokenService.GenerateAccessToken(user.ID, user.Email)
	if err != nil {
		h.logger.Printf("ERROR: generate access token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	plainRefresh, newHash, expiresAt, err := h.tokenService.GenerateRefreshToken()
	if err != nil {
		h.logger.Printf("ERROR: generate refresh token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	deviceInfo := req.DeviceInfo
	if deviceInfo == "" {
		deviceInfo = existingToken.DeviceInfo
	}

	_, err = h.tokenStore.CreateRefreshToken(user.ID, newHash, deviceInfo, expiresAt)
	if err != nil {
		h.logger.Printf("ERROR: store refresh token: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"access_token":  accessToken,
		"refresh_token": plainRefresh,
		"expires_in":    expiresIn,
		"user":          user,
	})
}

func (h *AuthHandler) HandleLogout(w http.ResponseWriter, r *http.Request) {
	var req logoutRequest
	_ = json.NewDecoder(r.Body).Decode(&req)

	if req.RefreshToken != "" {
		hash := tokens.HashToken(req.RefreshToken)
		_ = h.tokenStore.RevokeRefreshToken(hash)
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{"status": "logged_out"})
}
