package app

import (
	"database/sql"
	"fmt"
	"log"
	"net/http"
	"os"

	"Backend/internal/api"
	"Backend/internal/auth"
	"Backend/internal/mailer"
	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/tokens"
	"Backend/internal/utils"
)

type Application struct {
	Logger                *log.Logger
	AuthHandler           *api.AuthHandler
	SettingsHandler       *api.SettingsHandler
	BlocklistHandler      *api.BlocklistHandler
	HabitsHandler         *api.HabitsHandler
	AccountabilityHandler *api.AccountabilityHandler
	Middleware            middleware.UserMiddleware
	DB                    *sql.DB
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
	settingsStore := store.NewPostgresSettingsStore(gormDB)
	blocklistStore := store.NewPostgresBlocklistStore(gormDB)
	habitsStore := store.NewPostgresHabitsStore(gormDB)
	accountabilityStore := store.NewPostgresAccountabilityStore(gormDB)

	// Services
	googleVerifier := auth.NewGoogleVerifier()
	tokenService, err := tokens.NewTokenService()
	if err != nil {
		return nil, fmt.Errorf("token service initialization failed: %w", err)
	}
	resendMailer := mailer.NewResendMailer(logger)

	// Handlers
	authHandler := api.NewAuthHandler(userStore, tokenStore, googleVerifier, tokenService, logger)
	settingsHandler := api.NewSettingsHandler(settingsStore, logger)
	blocklistHandler := api.NewBlocklistHandler(blocklistStore, logger)
	habitsHandler := api.NewHabitsHandler(habitsStore, logger)
	accountabilityHandler := api.NewAccountabilityHandler(accountabilityStore, resendMailer, logger)

	// Middleware
	userMiddleware := middleware.UserMiddleware{
		UserStore:    userStore,
		TokenService: tokenService,
	}

	return &Application{
		Logger:                logger,
		AuthHandler:           authHandler,
		SettingsHandler:       settingsHandler,
		BlocklistHandler:      blocklistHandler,
		HabitsHandler:         habitsHandler,
		AccountabilityHandler: accountabilityHandler,
		Middleware:            userMiddleware,
		DB:                    sqlDB,
	}, nil
}

func (a *Application) HealthChecker(w http.ResponseWriter, r *http.Request) {
	if a.DB != nil {
		if err := a.DB.PingContext(r.Context()); err != nil {
			_ = utils.WriteJSON(w, http.StatusServiceUnavailable, utils.Envelope{
				"status":  "unhealthy",
				"service": "PGuard API",
				"error":   "database unavailable",
			})
			return
		}
	}
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
