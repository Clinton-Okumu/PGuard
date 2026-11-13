package app

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"

	"Backend/internal/api"
	"Backend/internal/auth"
	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/tokens"
	"Backend/internal/utils"
)

type Application struct {
	Logger      *log.Logger
	AuthHandler *api.AuthHandler
	Middleware  middleware.UserMiddleware
	DB          *sql.DB
}

func NewApplication() (*Application, error) {
	gormDB, err := store.Open()
	if err != nil {
		return nil, err
	}

	err = store.AutoMigrate(gormDB)
	if err != nil {
		return nil, fmt.Errorf("auto-migration failed: %w", err)
	}

	sqlDB, err := gormDB.DB()
	if err != nil {
		return nil, fmt.Errorf("failed to get sql.DB: %w", err)
	}

	logger := log.New(os.Stdout, "", log.Ldate|log.Ltime)

	// Stores
	userStore := store.NewPostgresUserStore(gormDB)
	tokenStore := store.NewPostgresTokenStore(gormDB)

	// Services
	googleVerifier := auth.NewGoogleVerifier()
	tokenService := tokens.NewTokenService()

	// Handlers
	authHandler := api.NewAuthHandler(userStore, tokenStore, googleVerifier, tokenService, logger)

	// Middleware
	userMiddleware := middleware.UserMiddleware{
		UserStore:    userStore,
		TokenService: tokenService,
	}

	return &Application{
		Logger:      logger,
		AuthHandler: authHandler,
		Middleware:  userMiddleware,
		DB:          sqlDB,
	}, nil
}

func (a *Application) HealthChecker(w http.ResponseWriter, r *http.Request) {
	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"status":  "ok",
		"service": "PGuard API",
	})
}

func (a *Application) Welcome(w http.ResponseWriter, r *http.Request) {
	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message": "Welcome to PGuard API",
	})
}
