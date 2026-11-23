package routes

import (
	"net/http"
	"os"
	"strings"
	"time"

	"Backend/internal/app"
	"Backend/internal/middleware"

	"github.com/go-chi/chi/v5"
	chimiddleware "github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
)

func parseAllowedOrigins() []string {
	raw := strings.TrimSpace(os.Getenv("CORS_ALLOWED_ORIGINS"))
	if raw != "" {
		var origins []string
		for _, o := range strings.Split(raw, ",") {
			trimmed := strings.TrimSpace(o)
			if trimmed != "" {
				origins = append(origins, trimmed)
			}
		}
		if len(origins) > 0 {
			return origins
		}
	}
	if strings.EqualFold(strings.TrimSpace(os.Getenv("DEV_MOCK_AUTH")), "true") {
		return []string{"*"}
	}
	return nil
}

func SetUpRoutes(app *app.Application) *chi.Mux {
	r := chi.NewRouter()

	r.Use(chimiddleware.RequestID)
	r.Use(chimiddleware.Logger)
	r.Use(chimiddleware.Recoverer)
	r.Use(middleware.MaxBodySize(1 << 20)) // 1 MiB body limit

	allowedOrigins := parseAllowedOrigins()
	r.Use(cors.Handler(cors.Options{
		AllowOriginFunc: func(r *http.Request, origin string) bool {
			for _, allowed := range allowedOrigins {
				if allowed == "*" || strings.EqualFold(allowed, origin) {
					return true
				}
			}
			return false
		},
		AllowedMethods:   []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "X-CSRF-Token"},
		ExposedHeaders:   []string{"Link"},
		AllowCredentials: false,
		MaxAge:           300,
	}))

	authLimiter := middleware.NewRateLimiter(20, time.Minute)
	accountabilityLimiter := middleware.NewRateLimiter(15, time.Minute)

	r.Get("/", app.Welcome)
	r.Get("/health", app.HealthChecker)

	r.Route("/api/v1", func(r chi.Router) {
		r.Group(func(r chi.Router) {
			r.Use(authLimiter.Middleware)
			r.Post("/auth/google", app.AuthHandler.HandleGoogleAuth)
			r.Post("/auth/refresh", app.AuthHandler.HandleRefreshToken)
			r.Post("/auth/logout", app.AuthHandler.HandleLogout)
		})

		// Partner Web Acceptance: GET renders read-only confirmation page, POST performs state mutation
		r.Group(func(r chi.Router) {
			r.Use(accountabilityLimiter.Middleware)
			r.Get("/accountability/accept-web", app.AccountabilityHandler.HandleAcceptInviteWebPage)
			r.Post("/accountability/accept-web", app.AccountabilityHandler.HandleAcceptInviteWeb)
		})

		// Protected endpoints
		r.Group(func(r chi.Router) {
			r.Use(app.Middleware.Authenticate)
			r.Get("/me", app.Middleware.RequireUser(app.Me))

			// Settings endpoints
			r.Get("/settings", app.Middleware.RequireUser(app.SettingsHandler.HandleGetSettings))
			r.Put("/settings", app.Middleware.RequireUser(app.SettingsHandler.HandleUpdateSettings))

			// Blocklist endpoints
			r.Get("/blocklist", app.Middleware.RequireUser(app.BlocklistHandler.HandleGetBlocklist))
			r.Post("/blocklist", app.Middleware.RequireUser(app.BlocklistHandler.HandleCreateBlocklistItem))
			r.Patch("/blocklist/{id}", app.Middleware.RequireUser(app.BlocklistHandler.HandleToggleBlocklistItem))
			r.Delete("/blocklist/{id}", app.Middleware.RequireUser(app.BlocklistHandler.HandleDeleteBlocklistItem))

			// Streak & Recovery endpoints
			r.Get("/streak", app.Middleware.RequireUser(app.HabitsHandler.HandleGetStreak))
			r.Post("/streak/reset", app.Middleware.RequireUser(app.HabitsHandler.HandleResetStreak))
			r.Get("/streak/history", app.Middleware.RequireUser(app.HabitsHandler.HandleGetRelapseHistory))

			// Habits & Daily Check-in endpoints
			r.Get("/habits", app.Middleware.RequireUser(app.HabitsHandler.HandleGetHabits))
			r.Post("/habits", app.Middleware.RequireUser(app.HabitsHandler.HandleCreateHabit))
			r.Post("/habits/{id}/toggle", app.Middleware.RequireUser(app.HabitsHandler.HandleToggleHabit))
			r.Delete("/habits/{id}", app.Middleware.RequireUser(app.HabitsHandler.HandleDeleteHabit))

			// Accountability Partner & Security Alert endpoints
			r.Get("/accountability/partner", app.Middleware.RequireUser(app.AccountabilityHandler.HandleGetPartner))
			r.With(accountabilityLimiter.Middleware).Post("/accountability/invite", app.Middleware.RequireUser(app.AccountabilityHandler.HandleCreateInvite))
			r.Post("/accountability/accept", app.Middleware.RequireUser(app.AccountabilityHandler.HandleAcceptInvite))
			r.Delete("/accountability/partner", app.Middleware.RequireUser(app.AccountabilityHandler.HandleDisconnectPartner))
			r.Post("/accountability/event", app.Middleware.RequireUser(app.AccountabilityHandler.HandleRecordEvent))
			r.Get("/accountability/events", app.Middleware.RequireUser(app.AccountabilityHandler.HandleGetEvents))
			r.With(accountabilityLimiter.Middleware).Post("/accountability/request-unlock", app.Middleware.RequireUser(app.AccountabilityHandler.HandleRequestUnlockCode))
			r.With(accountabilityLimiter.Middleware).Post("/accountability/verify-unlock", app.Middleware.RequireUser(app.AccountabilityHandler.HandleVerifyUnlockCode))
			r.Get("/accountability/time-delay", app.Middleware.RequireUser(app.AccountabilityHandler.HandleGetTimeDelay))
			r.With(accountabilityLimiter.Middleware).Post("/accountability/time-delay", app.Middleware.RequireUser(app.AccountabilityHandler.HandleStartTimeDelay))
			r.Delete("/accountability/time-delay", app.Middleware.RequireUser(app.AccountabilityHandler.HandleClearTimeDelay))
		})
	})

	return r
}
